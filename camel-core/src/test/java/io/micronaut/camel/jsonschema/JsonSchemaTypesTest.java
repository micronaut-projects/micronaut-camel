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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.jsonschema.JsonSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSchemaTypesTest {

    private final JsonSchemaTypes types = new JsonSchemaTypes("schemas");

    @Test
    void namesTheSchemasAsTheAnnotationProcessor() {
        assertEquals("json-schema-types-test.schema.json", types.fileName(JsonSchemaTypesTest.class, AnnotationValue.builder(JsonSchema.class).build()));
        assertEquals("purchase-order.schema.json", types.fileName(JsonSchemaTypesTest.class,
            AnnotationValue.builder(JsonSchema.class).member("title", "PurchaseOrder").build()));
        assertEquals("orders/order.schema.json", types.fileName(JsonSchemaTypesTest.class,
            AnnotationValue.builder(JsonSchema.class).member("uri", "/orders/order").build()));
        assertEquals("order.schema.json", types.fileName(JsonSchemaTypesTest.class,
            AnnotationValue.builder(JsonSchema.class).member("uri", "https://example.com/schemas/order.schema.json").build()));
        assertEquals("META-INF/schemas/unknown.schema.json", types.resource("unknown"));
    }
}
