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
package io.micronaut.camel.jsonvalidation;

import io.micronaut.camel.jsonschema.JsonSchemaValidationException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel's {@code json-validator} (NetworkNT JSON Schema 2) and the Micronaut JSON Schema validator, which the MCP
 * server of Micronaut MCP brings, in one application context: the references between the generated schemas
 * resolve from the classpath with both.
 */
@MicronautTest(environments = {"docs-mcp", "json-validator-mcp"})
class JsonValidatorWithMcpTest {

    private static final String VALID = "{\"reference\":\"C-1\",\"orders\":[{\"id\":\"A-1\",\"quantity\":2}]}";
    // the error is in the schema of Order, which the schema of Consignment references
    private static final String INVALID = "{\"reference\":\"C-2\",\"orders\":[{\"id\":\"A-2\",\"quantity\":0}]}";

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    JsonSchemaValidator validator;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    JsonMapper jsonMapper;

    @Test
    void theJsonValidatorComponentValidates() throws Exception {
        assertValid("direct:json-validator-consignments");
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:json-validator-consignments", INVALID));
        JsonValidationException validation = assertInstanceOf(JsonValidationException.class, e.getCause());
        assertEquals(1, validation.getNumberOfErrors(), validation.getMessage());
    }

    @Test
    void theMicronautValidatorValidatesTheType() throws Exception {
        assertValid("direct:typed-consignments");
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:typed-consignments", INVALID));
        JsonSchemaValidationException validation = assertInstanceOf(JsonSchemaValidationException.class, e.getCause());
        assertEquals(List.of("/orders/0/quantity: must have an exclusive minimum value of 0"), validation.getErrors());
        assertEquals(1, validator.validate(INVALID, Consignment.class).size());
    }

    @Test
    void theMcpServerServesTheTools() throws IOException {
        String request = jsonMapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()));
        String response = client.toBlocking().retrieve(HttpRequest.POST("/mcp", request)
            .contentType("application/json")
            .accept("application/json", "text/event-stream"));
        assertTrue(response.contains("\"forecast\""), response);
    }

    private void assertValid(String uri) throws Exception {
        MockEndpoint consignments = camelContext.getEndpoint("mock:valid-consignments", MockEndpoint.class);
        consignments.reset();
        consignments.expectedMessageCount(1);
        producerTemplate.sendBody(uri, VALID);
        consignments.assertIsSatisfied();
    }
}
