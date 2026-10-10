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
package io.micronaut.camel.servlet;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@code camel.servlet.mapping.*} options.
 */
class ServletMappingTest {

    @Test
    void theMappingIsConfigurable() {
        try (EmbeddedServer server = start(Map.of(
            "camel.servlet.mapping.context-path", "/services/*",
            "camel.servlet.mapping.servlet-name", "Services",
            "camel.component.servlet.servlet-name", "Services"));
             HttpClient client = HttpClient.create(server.getURL())) {
            BlockingHttpClient http = client.toBlocking();

            assertEquals("pong", http.retrieve("/services/ping"));
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> http.retrieve("/camel/ping"));
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    @Test
    void theServletPathsAreBelowTheServerContextPath() {
        try (EmbeddedServer server = start(Map.of("micronaut.server.context-path", "/app"));
             HttpClient client = HttpClient.create(server.getURL())) {
            assertEquals("pong", client.toBlocking().retrieve("/app/camel/ping"));
        }
    }

    @Test
    void theServletCanBeDisabled() {
        try (EmbeddedServer server = start(Map.of("camel.servlet.mapping.enabled", "false"));
             HttpClient client = HttpClient.create(server.getURL())) {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve("/camel/ping"));
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    private static EmbeddedServer start(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("spec.name", "ServletMappingTest");
        return ApplicationContext.run(EmbeddedServer.class, all);
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServletMappingTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("servlet:ping").setBody(constant("pong"));
        }
    }
}
