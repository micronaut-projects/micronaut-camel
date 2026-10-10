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
package io.micronaut.camel.examples.openapicontractfirst;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ports the {@code openapi-contract-first} example of camel-spring-boot-examples.
 * <p>
 * The {@code petstore.json} contract is served by the Micronaut HTTP server through
 * {@code micronaut-camel-http}. The model classes, which the example generates with the swagger-codegen Maven
 * plugin, are written by hand, and the Spring {@code server.servlet.context-path} became
 * {@code micronaut.server.context-path}. Because camel-main creates the {@code rest-openapi} component to apply
 * its {@code base-path} before the route sets the REST configuration, the binding package is also configured
 * on the component. The actuator, management, console and CLI connector dependencies were dropped. The example
 * has no test: this one makes the requests of its readme, and checks that invalid requests are rejected.
 */
@MicronautTest(environments = "example-openapi-contract-first")
class PetStoreRouteTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    ResourceLoader resourceLoader;

    @Test
    void getsAPet() {
        Map<?, ?> pet = httpClient.toBlocking().retrieve(HttpRequest.GET("/myapp/myapi/pet/123"), Map.class);

        assertEquals(123, pet.get("id"));
        assertEquals("Tony the tiger", pet.get("name"));
        assertEquals("available", pet.get("status"));
    }

    @Test
    void updatesAPet() throws IOException {
        Map<?, ?> pet = httpClient.toBlocking().retrieve(HttpRequest.PUT("/myapp/myapi/pet", daisy())
            .contentType(MediaType.APPLICATION_JSON_TYPE), Map.class);

        assertEquals(555, pet.get("id"));
        assertEquals("Daisy the parrot", pet.get("name"));
        assertEquals("pending", pet.get("status"));
    }

    @Test
    void validatesTheRequest() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve(HttpRequest.PUT("/myapp/myapi/pet", "{\"id\": 555}")
                .contentType(MediaType.APPLICATION_JSON_TYPE)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    private String daisy() throws IOException {
        try (InputStream in = resourceLoader.getResourceAsStream("classpath:examples/openapi-contract-first/daisy.json").orElseThrow()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
