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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.camel.http.JsonSchemaApiDocTest.map;
import static io.micronaut.camel.http.JsonSchemaApiDocTest.schemas;
import static org.junit.jupiter.api.Assertions.assertFalse;

@MicronautTest
@Property(name = "spec.name", value = "JsonSchemaApiDocTest")
@Property(name = "camel.rest.context-path", value = "/api")
@Property(name = "micronaut.camel.json-schema.openapi.enabled", value = "false")
class JsonSchemaApiDocDisabledTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void theApiDocumentHasTheSchemasOfCamel() {
        Map<?, ?> document = httpClient.toBlocking().retrieve(HttpRequest.GET("/api/api-doc"), Map.class);

        assertFalse(map(schemas(document).get("Parcel")).containsKey("title"));
    }
}
