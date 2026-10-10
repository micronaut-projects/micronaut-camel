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
package io.micronaut.camel.graal;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REST DSL routes on the Micronaut HTTP server, with JSON bound by the micronaut-json data format.
 */
@MicronautTest
class NativeHttpTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    CamelContext camelContext;

    @Test
    void restRoutesAreServed() {
        assertEquals("Hello Fred", client.toBlocking().retrieve("/native/hello/Fred"));
    }

    @Test
    void jsonIsBoundWithMicronautSerialization() {
        String json = client.toBlocking().retrieve(HttpRequest.POST("/native/books", "{\"title\":\"Camel\",\"pages\":100}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE));

        assertEquals("{\"title\":\"CAMEL\",\"pages\":200}", json);
    }

    @Test
    void typesAreConvertedWithMicronautSerialization() {
        assertEquals(new Book("Camel", 100), camelContext.getTypeConverter().convertTo(Book.class, "{\"title\":\"Camel\",\"pages\":100}"));
        assertEquals("{\"title\":\"Camel\",\"pages\":100}", camelContext.getTypeConverter().convertTo(String.class, new Book("Camel", 100)));
    }

    @Serdeable
    public record Book(String title, int pages) {
    }

    @Singleton
    public static class RestRoutes extends RouteBuilder {
        @Override
        public void configure() {
            restConfiguration().bindingMode(RestBindingMode.json).jsonDataFormat("micronaut-json");

            rest("/native")
                .get("/hello/{name}").bindingMode(RestBindingMode.off).to("direct:hello")
                .post("/books").type(Book.class).outType(Book.class).to("direct:book");

            from("direct:hello").setBody(simple("Hello ${header.name}"));

            from("direct:book").process(exchange -> {
                Book book = exchange.getMessage().getBody(Book.class);
                exchange.getMessage().setBody(new Book(book.title().toUpperCase(), book.pages() * 2));
            });
        }
    }
}
