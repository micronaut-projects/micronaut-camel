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
package io.micronaut.camel.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "CamelEndpointTest")
@Property(name = "camel.main.name", value = "billing")
@Property(name = "endpoints.camel.sensitive", value = "false")
@Property(name = "endpoints.camel.write-sensitive", value = "false")
@Property(name = "endpoints.info.sensitive", value = "false")
class CamelEndpointTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void contextAndRoutesAreListed() {
        producerTemplate.sendBody("direct:invoices", "invoice");

        Map<String, Object> context = client.toBlocking().retrieve("/camel", Map.class);
        assertEquals("billing", context.get("name"));
        assertEquals(camelContext.getVersion(), context.get("version"));
        assertEquals("Started", context.get("status"));
        assertNotNull(context.get("uptimeMillis"));

        List<Map<String, Object>> routes = (List<Map<String, Object>>) context.get("routes");
        Map<String, Object> invoices = routes.stream().filter(r -> r.get("id").equals("invoices")).findFirst().orElseThrow();
        assertEquals("direct://invoices", invoices.get("uri"));
        assertEquals("Started", invoices.get("status"));
        assertEquals("billing", invoices.get("group"));
        // camel-management is on the classpath, so exchange statistics are available
        Map<String, Object> statistics = (Map<String, Object>) invoices.get("statistics");
        assertTrue(((Number) statistics.get("exchangesTotal")).longValue() >= 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeIsReadAndControlled() {
        Map<String, Object> route = client.toBlocking().retrieve("/camel/payments", Map.class);
        assertEquals("payments", route.get("id"));
        assertEquals("Started", route.get("status"));

        route = client.toBlocking().retrieve(HttpRequest.POST("/camel/payments/suspend", ""), Map.class);
        assertEquals("Suspended", route.get("status"));
        assertEquals(ServiceStatus.Suspended, camelContext.getRouteController().getRouteStatus("payments"));

        route = client.toBlocking().retrieve(HttpRequest.POST("/camel/payments/resume", ""), Map.class);
        assertEquals("Started", route.get("status"));

        route = client.toBlocking().retrieve(HttpRequest.POST("/camel/payments/stop", ""), Map.class);
        assertEquals("Stopped", route.get("status"));
        assertEquals(ServiceStatus.Stopped, camelContext.getRouteController().getRouteStatus("payments"));

        route = client.toBlocking().retrieve(HttpRequest.POST("/camel/payments/start", ""), Map.class);
        assertEquals("Started", route.get("status"));

        route = client.toBlocking().retrieve(HttpRequest.POST("/camel/payments/reset", ""), Map.class);
        assertEquals(0, ((Number) ((Map<String, Object>) route.get("statistics")).get("exchangesTotal")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeIsRemoved() {
        Map<String, Object> removed = client.toBlocking().retrieve(HttpRequest.DELETE("/camel/refunds"), Map.class);
        assertEquals(Boolean.TRUE, removed.get("removed"));
        assertNull(camelContext.getRoute("refunds"));
    }

    @Test
    void unknownRouteIsNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve("/camel/unknown", Map.class));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void camelIsAddedToInfo() {
        Map<String, Object> info = client.toBlocking().retrieve("/info", Map.class);
        Map<String, Object> camel = (Map<String, Object>) info.get("camel");
        assertEquals("billing", camel.get("name"));
        assertEquals("Started", camel.get("status"));
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelEndpointTest")
    static class BillingRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:invoices").routeId("invoices").routeGroup("billing").log("${body}");
            from("direct:payments").routeId("payments").log("${body}");
            from("direct:refunds").routeId("refunds").log("${body}");
        }
    }
}
