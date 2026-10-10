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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "CamelHealthIndicatorTest")
@Property(name = "endpoints.health.details-visible", value = "ANONYMOUS")
@Property(name = "camel.health.exposure-level", value = "full")
class CamelHealthIndicatorTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    CamelContext camelContext;

    @Inject
    InventoryHealthCheck inventory;

    @AfterEach
    void reset() {
        inventory.available = true;
    }

    @Test
    void camelChecksAreRegisteredByDefault() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        assertEquals(CamelHealthMainListener.REGISTRY_ID, registry.getId());
        assertTrue(registry.getCheck("context").isPresent());
        assertTrue(registry.getRepository("routes").isPresent());
        assertTrue(registry.getRepository("consumers").isPresent());
        // producers are disabled by default, as with camel-main
        assertFalse(registry.getRepository("producers").orElseThrow().isEnabled());
        assertTrue(HealthCheckHelper.invoke(camelContext).stream()
            .anyMatch(result -> result.getCheck().getId().equals("inventory")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthReportsCamelChecks() {
        Map<String, Object> health = client.toBlocking().retrieve("/health", Map.class);
        assertEquals("UP", health.get("status"));

        Map<String, Object> details = (Map<String, Object>) health.get("details");
        Map<String, Object> camel = (Map<String, Object>) details.get(CamelHealthIndicator.NAME);
        assertEquals("UP", camel.get("status"));
        Map<String, Object> checks = (Map<String, Object>) camel.get("details");
        assertEquals("UP", ((Map<String, Object>) checks.get("context")).get("status"));
        assertEquals("UP", ((Map<String, Object>) checks.get("route:orders")).get("status"));
        Map<String, Object> inventoryCheck = (Map<String, Object>) checks.get("inventory");
        assertEquals("UP", inventoryCheck.get("status"));
        assertEquals("42", inventoryCheck.get("items"));

        assertTrue(details.containsKey(CamelLivenessHealthIndicator.NAME));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downCheckMakesHealthDown() {
        inventory.available = false;

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve("/health/readiness", Map.class));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        Map<String, Object> health = e.getResponse().getBody(Map.class).orElseThrow();
        Map<String, Object> camel = (Map<String, Object>) ((Map<String, Object>) health.get("details")).get(CamelHealthIndicator.NAME);
        assertEquals("DOWN", camel.get("status"));
        Map<String, Object> inventoryCheck = (Map<String, Object>) ((Map<String, Object>) camel.get("details")).get("inventory");
        assertEquals("DOWN", inventoryCheck.get("status"));
        assertEquals("Out of stock", inventoryCheck.get("message"));

        // custom checks are readiness checks by default, so the application stays live
        Map<String, Object> liveness = client.toBlocking().retrieve("/health/liveness", Map.class);
        assertEquals("UP", liveness.get("status"));
        Map<String, Object> livenessDetails = (Map<String, Object>) liveness.get("details");
        assertTrue(livenessDetails.containsKey(CamelLivenessHealthIndicator.NAME));
        assertFalse(livenessDetails.containsKey(CamelHealthIndicator.NAME));
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelHealthIndicatorTest")
    static class InventoryHealthCheck extends AbstractHealthCheck {

        volatile boolean available = true;

        InventoryHealthCheck() {
            super("inventory");
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.detail("items", 42);
            if (available) {
                builder.up();
            } else {
                builder.message("Out of stock").down();
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelHealthIndicatorTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:orders").routeId("orders").log("${body}");
        }
    }
}
