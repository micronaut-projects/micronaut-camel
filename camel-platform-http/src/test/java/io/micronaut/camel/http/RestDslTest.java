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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "RestDslTest")
@Property(name = "camel.rest.context-path", value = "/api")
class RestDslTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    MicronautPlatformHttpEngine engine;

    @Test
    void platformHttpServesTheRestServices() {
        // the only REST component on the classpath
        assertEquals(2, engine.getConsumers().size());
    }

    @Test
    void jsonBindingRoundTripsAPojo() {
        HttpResponse<String> created = httpClient.toBlocking().exchange(
            HttpRequest.POST("/api/books", "{\"isbn\":\"123\",\"title\":\"Camel in Action\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE),
            String.class);

        assertEquals(HttpStatus.CREATED, created.status());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, created.getContentType().orElse(null));
        assertEquals("{\"isbn\":\"123\",\"title\":\"CAMEL IN ACTION\"}", created.body());

        String found = httpClient.toBlocking().retrieve("/api/books/123");
        assertEquals("{\"isbn\":\"123\",\"title\":\"CAMEL IN ACTION\"}", found);
    }

    @Test
    void restVerbsAreRestricted() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve(HttpRequest.DELETE("/api/books/123")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, e.getStatus());
    }

    /**
     * A book.
     *
     * @param isbn  The ISBN
     * @param title The title
     */
    public record Book(String isbn, String title) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "RestDslTest")
    static class BookRoutes extends RouteBuilder {

        private final Map<String, Book> books = new ConcurrentHashMap<>();

        @Override
        public void configure() {
            restConfiguration().bindingMode(RestBindingMode.json);

            rest("/books")
                .post().type(Book.class).outType(Book.class).to("direct:save")
                .get("/{isbn}").outType(Book.class).to("direct:find");

            from("direct:save")
                .process(e -> {
                    Book book = e.getMessage().getBody(Book.class);
                    Book saved = new Book(book.isbn(), book.title().toUpperCase());
                    books.put(saved.isbn(), saved);
                    e.getMessage().setBody(saved);
                })
                .setHeader("CamelHttpResponseCode", constant(201));

            from("direct:find")
                .process(e -> e.getMessage().setBody(books.get(e.getMessage().getHeader("isbn", String.class))));
        }
    }
}
