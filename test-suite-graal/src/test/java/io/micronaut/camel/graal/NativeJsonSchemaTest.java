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

import io.micronaut.camel.jsonschema.JsonSchemaValidationException;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON schemas generated at build time: the {@code jsonschema:} resources and the validators of the types,
 * with the Micronaut JSON Schema validator.
 */
@MicronautTest
class NativeJsonSchemaTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void resolvesTheSchemaOfAType() throws Exception {
        try (InputStream in = PluginHelper.getResourceLoader(camelContext).resolveResource("jsonschema:Parcel").getInputStream()) {
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"weight\""));
        }
    }

    @Test
    void validatesTheInputType() {
        assertEquals(new Parcel("P1", 2), producerTemplate.requestBody("direct:parcel", "{\"id\":\"P1\",\"weight\":2}"));
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.requestBody("direct:parcel", "{\"id\":\"P2\",\"weight\":0}"));
        assertInstanceOf(JsonSchemaValidationException.class, e.getCause());
    }

    @JsonSchema
    @Serdeable
    public record Parcel(@NotBlank String id, @Positive int weight) {
    }

    @Singleton
    public static class ParcelRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:parcel").inputTypeWithValidate(Parcel.class).log("${body}");
        }
    }
}
