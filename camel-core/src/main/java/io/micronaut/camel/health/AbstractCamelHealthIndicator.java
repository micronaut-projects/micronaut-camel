/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.camel.health;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Base class of the health indicators that report the results of Camel's health checks.
 *
 * <p>The details hold one entry per health check, keyed by the check id, with its state and, unless the
 * exposure level ({@code camel.health.exposure-level}) is {@code oneline}, its message, error and details.
 * With the {@code default} exposure level Camel reports only the checks that are DOWN, or a single check
 * when every check is UP. The indicator is DOWN when a check is DOWN, UP when a check is UP, and UNKNOWN
 * otherwise, for example while the checks of a supervised route are UNKNOWN.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
abstract sealed class AbstractCamelHealthIndicator implements HealthIndicator
    permits CamelHealthIndicator, CamelLivenessHealthIndicator {

    private static final String ONELINE = "oneline";
    private static final String FULL = "full";

    private final CamelContext camelContext;
    private final ExecutorService executorService;

    /**
     * @param camelContext    The Camel context
     * @param executorService The executor that runs the checks
     */
    AbstractCamelHealthIndicator(CamelContext camelContext, ExecutorService executorService) {
        this.camelContext = camelContext;
        this.executorService = executorService;
    }

    @Override
    public final Publisher<HealthResult> getResult() {
        return new AsyncSingleResultPublisher<>(executorService, this::healthResult);
    }

    /**
     * @return The name of the indicator
     */
    abstract String name();

    /**
     * @param camelContext The Camel context
     * @return The results of the checks this indicator reports
     */
    abstract Collection<HealthCheck.Result> invoke(CamelContext camelContext);

    private HealthResult healthResult() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        String exposureLevel = registry != null ? registry.getExposureLevel() : null;
        Map<String, Object> details = new LinkedHashMap<>();
        boolean up = false;
        boolean down = false;
        for (HealthCheck.Result result : invoke(camelContext)) {
            if (Boolean.FALSE.equals(result.getDetails().get(HealthCheck.CHECK_ENABLED))) {
                continue;
            }
            HealthCheck.State state = result.getState();
            up |= state == HealthCheck.State.UP;
            down |= state == HealthCheck.State.DOWN;
            details.put(result.getCheck().getId(), checkDetails(result, exposureLevel));
        }
        HealthStatus status = down ? HealthStatus.DOWN : up ? HealthStatus.UP : HealthStatus.UNKNOWN;
        return HealthResult.builder(name(), status).details(details).build();
    }

    private static Map<String, Object> checkDetails(HealthCheck.Result result, String exposureLevel) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("status", result.getState().name());
        if (ONELINE.equals(exposureLevel)) {
            return check;
        }
        result.getMessage().ifPresent(message -> check.put("message", message));
        result.getError().ifPresent(error -> check.put("error", error.getClass().getName() + ": " + error.getMessage()));
        // the check's own metadata, such as its id and group, is only shown with the full exposure level
        Set<String> metadata = FULL.equals(exposureLevel) ? Set.of() : result.getCheck().getMetaData().keySet();
        result.getDetails().forEach((key, value) -> {
            if (value != null && !metadata.contains(key)) {
                check.put(key, value.toString());
            }
        });
        return check;
    }
}
