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
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "CamelEndpointSensitivityTest")
@Property(name = "endpoints.camel.sensitive", value = "false")
class CamelEndpointSensitivityTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    CamelContext camelContext;

    @Test
    void writesStaySensitive() {
        assertEquals("Started", client.toBlocking().retrieve("/camel/audit", Map.class).get("status"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST("/camel/audit/stop", ""), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.DELETE("/camel/audit"), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        assertEquals(ServiceStatus.Started, camelContext.getRouteController().getRouteStatus("audit"));
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelEndpointSensitivityTest")
    static class AuditRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:audit").routeId("audit").log("${body}");
        }
    }
}
