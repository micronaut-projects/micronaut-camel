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
package io.micronaut.camel.examples.platformhttp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code platform-http} example of camel-spring-boot-examples.
 * <p>
 * The REST DSL routes are served by the Micronaut HTTP server through {@code micronaut-camel-http} instead of
 * Spring MVC through {@code camel-platform-http-starter}. The {@code todoService} Spring service is a named
 * Micronaut singleton. The example has no test: this one makes the requests of its README with the Micronaut
 * HTTP client.
 */
@MicronautTest(environments = "example-platform-http")
class CamelRouterTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void managesTodos() {
        BlockingHttpClient client = httpClient.toBlocking();

        Todo created = client.retrieve(HttpRequest.POST("/todos",
                "{\"title\":\"Todo title\", \"completed\":\"false\", \"order\": 1, \"url\":\"\"}")
            .contentType(MediaType.APPLICATION_JSON_TYPE), Todo.class);
        assertEquals(1, created.getId());
        assertEquals("Todo title", created.getTitle());

        Todo[] all = client.retrieve(HttpRequest.GET("/todos"), Todo[].class);
        assertEquals(1, all.length);

        Todo updated = client.retrieve(HttpRequest.PATCH("/todos/1", "{\"title\":\"Done\", \"completed\":true}")
            .contentType(MediaType.APPLICATION_JSON_TYPE), Todo.class);
        assertTrue(updated.isCompleted());

        Todo found = client.retrieve(HttpRequest.GET("/todos/1"), Todo.class);
        assertEquals("Done", found.getTitle());

        assertEquals("1", client.retrieve(HttpRequest.DELETE("/todos")));

        HttpResponse<String> deleted = client.exchange(HttpRequest.DELETE("/todos/1"), String.class);
        // deleteOne returns nothing
        assertEquals(HttpStatus.NO_CONTENT, deleted.status());
        assertEquals(0, client.retrieve(HttpRequest.GET("/todos"), Todo[].class).length);
    }
}
