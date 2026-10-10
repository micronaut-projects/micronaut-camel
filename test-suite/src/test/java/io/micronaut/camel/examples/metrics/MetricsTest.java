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
package io.micronaut.camel.examples.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code metrics} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The example records route metrics with camel-metrics in a Dropwizard {@code MetricRegistry}, through a
 * {@code MetricsRoutePolicyFactory} added by a {@code CamelContextConfiguration}, and reports them to
 * Graphite. Here camel-micrometer records them in the Micronaut {@code MeterRegistry}, enabled with
 * {@code camel.metrics.enabled} in {@code application-example-metrics.yml}; a Micronaut application
 * reports them to Graphite by adding {@code micronaut-micrometer-registry-graphite}. The Spring
 * {@code @Bean} route builders are {@code @Singleton} methods of a factory, and the context name says
 * Micronaut instead of Spring Boot. The test waits for both routes to run and checks their timers in the
 * registry.
 */
@MicronautTest(environments = "example-metrics")
class MetricsTest {

    @Inject
    CamelContext camelContext;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void routeMetricsAreRecorded() {
        assertEquals("camel-example-micronaut-metrics", camelContext.getName());

        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("fast-route").whenDone(1)
            .and().fromRoute("slow-route").whenDone(1)
            .create();
        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        for (String route : new String[] {"fast-route", "slow-route"}) {
            Timer timer = meterRegistry.find("camel.route.policy").tag("routeId", route).timer();
            assertNotNull(timer);
            assertTrue(timer.count() >= 1);
        }
    }
}
