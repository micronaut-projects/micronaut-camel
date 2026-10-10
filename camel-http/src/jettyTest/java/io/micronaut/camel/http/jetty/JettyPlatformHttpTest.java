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
package io.micronaut.camel.http.jetty;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
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
 * The engine on a servlet container, Jetty, instead of Netty.
 */
@MicronautTest
class JettyPlatformHttpTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    EmbeddedServer server;

    @Test
    void runsOnJetty() {
        assertTrue(server.getClass().getName().contains("Jetty"), server.getClass().getName());
    }

    @Test
    void servesRoutesWithPathParameters() {
        HttpResponse<String> response = httpClient.toBlocking().exchange(HttpRequest.GET("/users/42"), String.class);

        assertEquals("user 42", response.body());
        assertEquals("true", response.header("X-Filtered"));
    }

    @Test
    void echoesThePostedBody() {
        assertEquals("ping", httpClient.toBlocking().retrieve(HttpRequest.POST("/echo", "ping")
            .contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void roundTripsJsonWithTheRestDsl() {
        String json = httpClient.toBlocking().retrieve(HttpRequest.POST("/greetings", "{\"name\":\"Fred\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));

        assertEquals("{\"name\":\"Hello Fred\"}", json);
    }

    @Test
    void controllersStillWork() {
        assertEquals("controller", httpClient.toBlocking().retrieve("/controller"));
    }

    @Test
    void unknownPathsAndMethodsAreRejected() {
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve("/missing"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());

        HttpClientResponseException notAllowed = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve(HttpRequest.GET("/echo")));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatus());
        assertEquals("POST", notAllowed.getResponse().header(HttpHeaders.ALLOW));
    }

    @Test
    void exceptionsAreInternalServerErrors() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve("/boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    /**
     * A greeting.
     *
     * @param name The name
     */
    public record Greeting(String name) {
    }

    @Singleton
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            restConfiguration().bindingMode(RestBindingMode.json);

            rest("/greetings").post().type(Greeting.class).outType(Greeting.class).to("direct:greet");

            from("direct:greet")
                .process(e -> e.getMessage().setBody(new Greeting("Hello " + e.getMessage().getBody(Greeting.class).name())));

            from("platform-http:/users/{id}?httpMethodRestrict=GET")
                .setBody(simple("user ${header.id}"));

            from("platform-http:/echo?httpMethodRestrict=POST")
                .convertBodyTo(String.class);

            from("platform-http:/boom")
                .throwException(new IllegalStateException("Kaboom"));
        }
    }

    @Controller
    static class TestController {

        @Get(uri = "/controller", produces = MediaType.TEXT_PLAIN)
        String controller() {
            return "controller";
        }
    }

    @ServerFilter("/users/**")
    static class MarkingFilter {

        @ResponseFilter
        void mark(MutableHttpResponse<?> response) {
            response.header("X-Filtered", "true");
        }
    }
}
