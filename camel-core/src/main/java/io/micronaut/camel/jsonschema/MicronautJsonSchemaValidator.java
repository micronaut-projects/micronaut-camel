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

import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.ValidationMessage;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * A Camel validator of a {@code @JsonSchema} type, which validates the body with the Micronaut JSON Schema
 * validator against the schema generated for the type at build time: the body as JSON when it is a
 * {@code String} or another type that Camel converts to a {@code String}, or the body serialized to JSON when
 * it is an instance of the type.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class MicronautJsonSchemaValidator extends Validator {

    private final JsonSchemaValidator validator;
    private final Class<?> type;

    /**
     * @param validator The Micronaut JSON Schema validator
     * @param type      The {@code @JsonSchema} type
     */
    MicronautJsonSchemaValidator(JsonSchemaValidator validator, Class<?> type) {
        this.validator = validator;
        this.type = type;
    }

    @Override
    public void validate(Message message, DataType dataType) throws ValidationException {
        Object body = message.getBody();
        if (body == null) {
            throw new ValidationException(message.getExchange(), "No body to validate against the JSON schema of " + type.getName());
        }
        Set<? extends ValidationMessage> messages;
        try {
            Object value = type.isInstance(body) ? body : message.getMandatoryBody(String.class);
            messages = validator.validate(value, type);
        } catch (IOException | RuntimeException | InvalidPayloadException e) {
            throw new ValidationException("JSON schema validation of " + type.getName() + " failed: " + e.getMessage(),
                message.getExchange(), e);
        }
        if (!messages.isEmpty()) {
            List<String> errors = messages.stream().map(ValidationMessage::getMessage).toList();
            throw new JsonSchemaValidationException(message.getExchange(), type, errors);
        }
    }
}
