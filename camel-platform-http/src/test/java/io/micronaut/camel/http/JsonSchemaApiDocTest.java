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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "JsonSchemaApiDocTest")
@Property(name = "camel.rest.context-path", value = "/api")
class JsonSchemaApiDocTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void theApiDocumentHasTheGeneratedSchemas() {
        Map<String, Object> schemas = schemas(httpClient.toBlocking().retrieve(HttpRequest.GET("/api/api-doc"), Map.class));

        Map<String, Object> parcel = map(schemas.get("Parcel"));
        // the title and description of the generated schema, which camel-openapi-java does not write
        assertEquals("JsonSchemaApiDocTest.Parcel", parcel.get("title"));
        assertEquals("A parcel.", parcel.get("description"));
        assertEquals(List.of("id", "weight", "to"), parcel.get("required"));
        assertFalse(parcel.containsKey("$id"));
        assertFalse(parcel.containsKey("$schema"));
        Map<String, Object> properties = map(parcel.get("properties"));
        // exclusiveMinimum is a boolean in OpenAPI 3.0
        assertEquals(Map.of("description", "The weight", "type", "integer", "minimum", 0, "exclusiveMinimum", true), properties.get("weight"));
        // the reference to the schema of another type points to its component
        assertEquals("#/components/schemas/Address", map(properties.get("to")).get("$ref"));
        assertEquals("JsonSchemaApiDocTest.Address", map(schemas.get("Address")).get("title"));
    }

    @Test
    void theYamlApiDocumentHasTheGeneratedSchemas() {
        String yaml = httpClient.toBlocking().retrieve(HttpRequest.GET("/api/api-doc").accept("text/yaml"), String.class);

        assertTrue(yaml.contains("title: JsonSchemaApiDocTest.Parcel"), yaml);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> schemas(Map<?, ?> document) {
        return map(map(document.get("components")).get("schemas"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /**
     * A parcel.
     *
     * @param id     The identifier
     * @param weight The weight
     * @param to     The destination
     */
    @Introspected
    @JsonSchema
    public record Parcel(@NotBlank String id, @Positive int weight, Address to) {
    }

    /**
     * An address.
     *
     * @param city The city
     */
    @Introspected
    @JsonSchema
    public record Address(@NotBlank String city) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "JsonSchemaApiDocTest")
    static class ParcelRoutes extends RouteBuilder {

        private final String version;

        ParcelRoutes(@Value("${openapi.version:3.0.0}") String version) {
            this.version = version;
        }

        @Override
        public void configure() {
            restConfiguration()
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Parcels")
                .apiProperty("openapi.version", version);

            rest("/parcels")
                .post().type(Parcel.class).outType(Parcel.class).to("direct:parcel");

            from("direct:parcel").log("${body}");
        }
    }
}
