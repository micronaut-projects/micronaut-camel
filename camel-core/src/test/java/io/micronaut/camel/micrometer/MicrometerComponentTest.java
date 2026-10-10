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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@Property(name = "spec.name", value = "MicrometerComponentTest")
class MicrometerComponentTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void micrometerEndpointsUseTheMicronautRegistryWithoutCamelMetrics() {
        // as with camel-main, route metrics are only recorded with camel.metrics.enabled
        assertNull(camelContext.hasService(CamelMetricsService.class));
        assertNull(meterRegistry.find("camel.route.policy").timer());

        producerTemplate.sendBody("direct:shipments", "parcel");

        Counter shipped = meterRegistry.find("shipments.sent").counter();
        assertNotNull(shipped);
        assertEquals(1, shipped.count());
    }

    @Singleton
    @Requires(property = "spec.name", value = "MicrometerComponentTest")
    static class ShipmentRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:shipments").to("micrometer:counter:shipments.sent");
        }
    }
}
