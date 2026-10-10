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
package io.micronaut.camel.http;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "ConfigurationTest")
@Property(name = "micronaut.server.context-path", value = "/app")
@Property(name = "camel.component.platform-http.server-request-validation", value = "false")
class ConfigurationTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    CamelContext camelContext;

    @Test
    void componentOptionsComeFromMicronautConfiguration() {
        assertFalse(camelContext.getComponent("platform-http", PlatformHttpComponent.class).isServerRequestValidation());
        // without server validation the route receives any content type
        assertEquals("text/plain /hello", httpClient.toBlocking().retrieve(
            HttpRequest.POST("/app/hello", "x").contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void routesAreServedUnderTheServerContextPath() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve(HttpRequest.POST("/hello", "x")));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Singleton
    @Requires(property = "spec.name", value = "ConfigurationTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("platform-http:/hello?consumes=application/json")
                .setBody(simple("${header.Content-Type} ${header.CamelHttpPath}"));
        }
    }
}
