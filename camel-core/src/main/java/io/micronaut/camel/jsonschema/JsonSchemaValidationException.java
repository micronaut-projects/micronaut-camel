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

import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;

import java.io.Serial;
import java.util.List;

/**
 * Thrown when a message does not match the JSON schema of its {@code @JsonSchema} type, as validated by the
 * Micronaut JSON Schema validator.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public final class JsonSchemaValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Class<?> type;
    private final List<String> errors;

    /**
     * @param exchange The exchange
     * @param type     The type whose schema the message does not match
     * @param errors   The validation errors
     */
    public JsonSchemaValidationException(Exchange exchange, Class<?> type, List<String> errors) {
        super(exchange, "JSON schema validation of " + type.getName() + " failed with " + errors.size()
            + (errors.size() == 1 ? " error: " : " errors: ") + String.join(", ", errors));
        this.type = type;
        this.errors = List.copyOf(errors);
    }

    /**
     * @return The type whose schema the message does not match
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * @return The validation errors
     */
    public List<String> getErrors() {
        return errors;
    }
}
