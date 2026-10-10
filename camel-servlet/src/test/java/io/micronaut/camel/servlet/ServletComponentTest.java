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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel's servlet next to the Micronaut servlet, on the server of the {@code servlet.server} system
 * property: the test suites run on Jetty, Tomcat and Undertow.
 */
@MicronautTest
@Property(name = "spec.name", value = "ServletComponentTest")
class ServletComponentTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    EmbeddedServer server;

    @Test
    void runsOnTheServerOfTheTestSuite() {
        String expected = System.getProperty("servlet.server", "Jetty");
        assertTrue(server.getClass().getName().contains(expected), server.getClass().getName());
    }

    @Test
    void servletRoutesAreServedBelowTheMapping() {
        assertEquals("Hello Fred", httpClient.toBlocking().retrieve("/camel/hello?name=Fred"));
    }

    @Test
    void echoesThePostedBody() {
        assertEquals("ping", httpClient.toBlocking().retrieve(HttpRequest.POST("/camel/echo", "ping")
            .contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void restServicesUseTheServletComponent() {
        String json = httpClient.toBlocking().retrieve(HttpRequest.POST("/camel/greetings", "{\"name\":\"Fred\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));

        assertEquals("{\"name\":\"Hello Fred\"}", json);
    }

    @Test
    void controllersStillWork() {
        assertEquals("controller", httpClient.toBlocking().retrieve("/controller"));
    }

    @Test
    void unknownPathsBelowTheMappingAreNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve("/camel/missing"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    /**
     * A greeting.
     *
     * @param name The name
     */
    public record Greeting(String name) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServletComponentTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            restConfiguration().component("servlet").bindingMode(RestBindingMode.json);

            rest("/greetings").post().type(Greeting.class).outType(Greeting.class).to("direct:greet");

            from("direct:greet")
                .process(e -> e.getMessage().setBody(new Greeting("Hello " + e.getMessage().getBody(Greeting.class).name())));

            from("servlet:hello").setBody(simple("Hello ${header.name}"));

            from("servlet:echo?httpMethodRestrict=POST").convertBodyTo(String.class);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "ServletComponentTest")
    static class TestController {

        @Get(uri = "/controller", produces = MediaType.TEXT_PLAIN)
        String controller() {
            return "controller";
        }
    }
}
