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
package io.micronaut.camel.examples.restopenapi;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code rest-openapi} example of camel-spring-boot-examples.
 * <p>
 * The REST DSL uses the {@code platform-http} component, served by the Micronaut HTTP server through
 * {@code micronaut-camel-http}, instead of the {@code servlet} component, so the {@code /api} context path of the
 * Camel servlet became the context path of the REST configuration, read from {@code api.context-path}, and the
 * REST configuration no longer sets the port. The actuator, Micrometer and Prometheus parts were dropped. The
 * example has no test: this one makes the requests of its README and reads the OpenAPI document.
 */
@MicronautTest(environments = "example-rest-openapi")
class CamelRouterTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void findsUsers() {
        User[] users = httpClient.toBlocking().retrieve(HttpRequest.GET("/api/users"), User[].class);

        assertEquals(3, users.length);
        assertEquals("John Coltrane", users[0].getName());
        assertEquals("Miles Davis", httpClient.toBlocking().retrieve(HttpRequest.GET("/api/users/2"), User.class).getName());
    }

    @Test
    void updatesAUser() {
        HttpResponse<String> response = httpClient.toBlocking().exchange(
            HttpRequest.PUT("/api/users/3", "{\"id\": 3, \"name\": \"Charlie Parker\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), String.class);

        assertEquals(HttpStatus.NO_CONTENT, response.status());
        assertEquals("Charlie Parker", httpClient.toBlocking().retrieve(HttpRequest.GET("/api/users/3"), User.class).getName());
    }

    @Test
    void enablesCors() {
        HttpResponse<String> response = httpClient.toBlocking().exchange(HttpRequest.OPTIONS("/api/users"), String.class);

        // the preflight request is answered by Camel, without a body
        assertEquals(HttpStatus.NO_CONTENT, response.status());
        assertEquals("*", response.header("Access-Control-Allow-Origin"));
        assertNotNull(response.header("Access-Control-Allow-Methods"));
    }

    @Test
    void servesTheApiDocument() {
        Map<?, ?> document = httpClient.toBlocking().retrieve(HttpRequest.GET("/api/api-doc"), Map.class);

        Map<?, ?> info = (Map<?, ?>) document.get("info");
        assertEquals("User API", info.get("title"));
        assertTrue(((Map<?, ?>) document.get("paths")).containsKey("/users/{id}"));
    }
}
