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
package io.micronaut.camel.jsonschema;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "JsonSchemaValidatorsTest")
class JsonSchemaValidatorsTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void validatesTheInputAgainstTheSchemaOfItsType() throws Exception {
        MockEndpoint shipped = camelContext.getEndpoint("mock:shipped", MockEndpoint.class);
        shipped.expectedBodiesReceived(new Shipment("S1", 12));

        producerTemplate.sendBody("direct:ship", "{\"id\":\"S1\",\"weight\":12}");

        shipped.assertIsSatisfied();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:ship", "{\"id\":\"\",\"weight\":-1}"));
        JsonSchemaValidationException validation = assertInstanceOf(JsonSchemaValidationException.class, e.getCause());
        assertEquals(Shipment.class, validation.getType());
        assertEquals(2, validation.getErrors().size(), validation.getErrors().toString());
    }

    @Test
    void validatesTheOutputAgainstTheSchemaOfItsType() {
        assertEquals(new Shipment("S2", 3), producerTemplate.requestBodyAndHeader("direct:quote", null, "weight", 3));
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.requestBodyAndHeader("direct:quote", null, "weight", 0));
        assertInstanceOf(JsonSchemaValidationException.class, e.getCause());
    }

    @Test
    void validatesATitledTypeReferencingTheSchemaOfAnotherType() {
        MockEndpoint consigned = camelContext.getEndpoint("mock:consigned", MockEndpoint.class);
        consigned.expectedMessageCount(1);

        producerTemplate.sendBody("direct:consign", "{\"reference\":\"C1\",\"shipments\":[{\"id\":\"S1\",\"weight\":12}]}");

        // the error is in the schema of Shipment, which the schema of Consignment references by its classpath URI
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:consign", "{\"reference\":\"C2\",\"shipments\":[{\"id\":\"S2\",\"weight\":0}]}"));
        JsonSchemaValidationException validation = assertInstanceOf(JsonSchemaValidationException.class, e.getCause());
        assertEquals(Consignment.class, validation.getType());
        assertEquals(1, validation.getErrors().size(), validation.getErrors().toString());
        assertTrue(validation.getErrors().getFirst().contains("/shipments/0/weight"), validation.getErrors().toString());
    }

    @Test
    void resolvesTheSchemasAsResources() throws Exception {
        for (String name : List.of("Shipment", Shipment.class.getName(), "json-schema-validators-test-shipment")) {
            Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource("jsonschema:" + name);
            assertTrue(resource.exists(), name);
            try (InputStream in = resource.getInputStream()) {
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"weight\""), name);
            }
        }
        // a titled type is found by the name of its schema, which comes from the title
        Resource consignment = PluginHelper.getResourceLoader(camelContext).resolveResource("jsonschema:consignment");
        try (InputStream in = consignment.getInputStream()) {
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .contains("classpath:META-INF/schemas/json-schema-validators-test-shipment.schema.json"));
        }
        assertFalse(PluginHelper.getResourceLoader(camelContext).resolveResource("jsonschema:Unknown").exists());
    }

    @JsonSchema
    @Serdeable
    public record Shipment(@NotBlank String id, @Positive int weight) {
    }

    @JsonSchema(title = "Consignment")
    @Serdeable
    public record Consignment(@NotBlank String reference, List<Shipment> shipments) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "JsonSchemaValidatorsTest")
    static class ShipmentRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:ship")
                .inputTypeWithValidate(Shipment.class)
                .to("mock:shipped");
            from("direct:consign")
                .inputTypeWithValidate(Consignment.class)
                .to("mock:consigned");
            from("direct:quote")
                .outputTypeWithValidate(Shipment.class)
                .process(exchange -> exchange.getMessage().setBody(
                    new Shipment("S2", exchange.getMessage().getHeader("weight", Integer.class))));
        }
    }
}
