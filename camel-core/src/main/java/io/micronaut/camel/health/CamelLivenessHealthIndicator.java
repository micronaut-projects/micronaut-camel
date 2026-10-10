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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.annotation.Liveness;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

/**
 * Reports Camel's liveness health checks as the {@code camelLiveness} health indicator, part of
 * {@code /health} and {@code /health/liveness}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Liveness
@Requires(classes = {HealthEndpoint.class, DefaultHealthCheckRegistry.class})
@Requires(beans = HealthEndpoint.class)
@Requires(property = "camel.health.enabled", notEquals = StringUtils.FALSE)
@Internal
final class CamelLivenessHealthIndicator extends AbstractCamelHealthIndicator {

    /**
     * The name of the indicator.
     */
    static final String NAME = "camelLiveness";

    /**
     * @param camelContext    The Camel context
     * @param executorService The executor that runs the checks
     */
    CamelLivenessHealthIndicator(CamelContext camelContext, @Named(TaskExecutors.BLOCKING) ExecutorService executorService) {
        super(camelContext, executorService);
    }

    @Override
    String name() {
        return NAME;
    }

    @Override
    Collection<HealthCheck.Result> invoke(CamelContext camelContext) {
        return HealthCheckHelper.invokeLiveness(camelContext);
    }
}
