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
package io.micronaut.camel.openapi;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Swagger UI of micronaut-openapi, configured with {@code swagger-ui.spec.url=/api/api-doc}, shows the
 * OpenAPI document of the REST DSL that camel-openapi-java serves through the Micronaut server.
 */
@MicronautTest
class SwaggerUiTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void camelServesTheOpenApiDocumentOfTheRestServices() {
        Map<?, ?> document = httpClient.toBlocking().retrieve(HttpRequest.GET("/api/api-doc"), Map.class);

        assertEquals("Pet API", ((Map<?, ?>) document.get("info")).get("title"));
        assertTrue(((Map<?, ?>) document.get("paths")).containsKey("/pets/{id}"), document::toString);
    }

    @Test
    void theSwaggerUiShowsTheCamelDocument() {
        String page = httpClient.toBlocking().retrieve(HttpRequest.GET("/swagger-ui/index.html"), String.class);

        assertTrue(page.contains("/api/api-doc"), page);
    }

    @Test
    void theRestServicesAreServed() {
        assertEquals("Rex", httpClient.toBlocking().retrieve(HttpRequest.GET("/api/pets/7"), Map.class).get("name"));
    }
}
