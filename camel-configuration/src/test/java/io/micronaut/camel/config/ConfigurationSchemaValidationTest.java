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
package io.micronaut.camel.config;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.ConfigurationJsonSchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The configuration schemas of the generated configuration classes validate the {@code camel.*} options with
 * the configuration validator of Micronaut JSON Schema, without starting Camel.
 */
class ConfigurationSchemaValidationTest {

    @Test
    void acceptsValidOptions() {
        assertEquals(Map.of(), validate(Map.of(
            "camel.component.seda.queue-size", 500,
            "camel.component.seda.concurrentConsumers", 2,
            "camel.component.seda.enabled", true,
            "camel.component.bean.scope", "Prototype",
            "camel.main.name", "orders",
            "camel.main.jmx-management-statistics-level", "RoutesOnly",
            "camel.rest.binding-mode", "json"
        )));
    }

    @Test
    void reportsUnknownOptions() {
        Map<String, String> errors = validate(Map.of(
            "camel.component.seda.queue-sise", 500,
            "camel.main.shutdown-timout", 5
        ));

        assertEquals(2, errors.size(), errors::toString);
        assertTrue(errors.get("camel.component.seda.queue-sise").contains("Did you mean: camel.component.seda.queue-size?"), errors::toString);
        assertTrue(errors.get("camel.main.shutdown-timout").contains("camel.main.shutdown-timeout"), errors::toString);
    }

    @Test
    void reportsInvalidValues() {
        Map<String, String> errors = validate(Map.of(
            "camel.component.seda.queue-size", "lots",
            "camel.component.bean.scope", "Session",
            "camel.main.jmx-management-statistics-level", "All"
        ));

        assertEquals(Map.of(
            "camel.component.seda.queue-size", "Expected integer",
            "camel.component.bean.scope", "Value not in enum. Expected one of: 'Singleton', 'Request', 'Prototype'",
            "camel.main.jmx-management-statistics-level", "Value not in enum. Expected one of: 'Extended', 'Default', 'RoutesOnly', 'Off'"
        ), errors);
    }

    private static Map<String, String> validate(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.builder().properties(properties).build()) {
            Environment environment = context.getEnvironment().start();
            return new ConfigurationJsonSchemaValidator().validate(environment).stream()
                .filter(error -> error.property().startsWith("camel."))
                .collect(Collectors.toMap(ConfigurationError::property, ConfigurationError::message, (a, b) -> a + "; " + b, TreeMap::new));
        }
    }
}
