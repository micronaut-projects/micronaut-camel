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
package io.micronaut.camel.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelMetricsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "CamelMetricsTest")
@Property(name = "camel.metrics.enabled", value = "true")
@Property(name = "camel.metrics.enable-message-history", value = "true")
class CamelMetricsTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void camelMetricsAreRecordedInTheMicronautRegistry() {
        MicronautCamelMetricsService metrics = assertInstanceOf(MicronautCamelMetricsService.class,
            camelContext.hasService(CamelMetricsService.class));
        assertSame(meterRegistry, metrics.getMeterRegistry());
        assertTrue(metrics.isEnableMessageHistory());

        producerTemplate.sendBody("direct:orders", "order");

        Timer routeTimer = meterRegistry.find("camel.route.policy").tag("routeId", "orders").timer();
        assertNotNull(routeTimer);
        assertTrue(routeTimer.count() >= 1);
        assertNotNull(meterRegistry.find("camel.exchange.event.notifier").timer());
        assertNotNull(meterRegistry.find("camel.routes.running").gauge());
        assertNotNull(meterRegistry.find("camel.message.history").tag("routeId", "orders").timer());
    }

    @Test
    void micrometerEndpointsUseTheMicronautRegistry() {
        producerTemplate.sendBody("direct:orders", "order");

        Counter placed = meterRegistry.find("orders.placed").counter();
        assertNotNull(placed);
        assertTrue(placed.count() >= 1);
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelMetricsTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:orders").routeId("orders")
                .to("micrometer:counter:orders.placed")
                .log("${body}");
        }
    }
}
