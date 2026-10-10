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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jsonvalidator.JsonValidatorComponent;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorKey;
import org.apache.camel.spi.ValidatorRegistry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Registers a Camel validator for each {@code @JsonSchema} type, under the data type of the class, so that
 * {@code inputTypeWithValidate(Order.class)} and {@code outputTypeWithValidate(Order.class)} validate the
 * message against the schema generated for the type at build time. The validators that routes declare for a
 * type, with {@code validator().type(...)}, replace these.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
abstract sealed class JsonSchemaValidators implements CamelContextCustomizer {

    private final JsonSchemaTypes types;
    private final Function<Class<?>, Validator> validators;

    /**
     * @param types      The {@code @JsonSchema} types
     * @param validators Creates the validator of a type
     */
    JsonSchemaValidators(JsonSchemaTypes types, Function<Class<?>, Validator> validators) {
        this.types = types;
        this.validators = validators;
    }

    @Override
    public void configure(CamelContext camelContext) {
        ValidatorRegistry registry = camelContext.getValidatorRegistry();
        for (JsonSchemaTypes.JsonSchemaType schemaType : types.types()) {
            DataType dataType = new DataType(schemaType.type());
            ValidatorKey key = new ValidatorKey(dataType);
            if (registry.get(key) == null) {
                Validator validator = validators.apply(schemaType.type());
                validator.setType(dataType.getFullName());
                validator.setCamelContext(camelContext);
                registry.put(key, validator);
            }
        }
    }

    /**
     * The validators backed by the Micronaut JSON Schema validator of {@code micronaut-json-schema-validation}.
     */
    @Singleton
    @Internal
    @Requires(beans = JsonSchemaTypes.class)
    @Requires(classes = JsonSchemaValidator.class)
    @Requires(beans = JsonSchemaValidator.class)
    static final class Micronaut extends JsonSchemaValidators {

        /**
         * @param types       The {@code @JsonSchema} types
         * @param validator   The Micronaut JSON Schema validator
         * @param environment The environment, to read the schemas from
         */
        Micronaut(JsonSchemaTypes types, JsonSchemaValidator validator, Environment environment) {
            super(types, type -> new MicronautJsonSchemaValidator(validator, type, schemaUnlessFoundByType(types, type, environment)));
        }

        /**
         * The Micronaut validator looks up the schema of a type by its simple name, or by the {@code uri} of the
         * annotation, which misses the schemas of nested types and of types with a title: the schema of those is
         * given to the validator instead.
         */
        private static @Nullable String schemaUnlessFoundByType(JsonSchemaTypes types, Class<?> type, Environment environment) {
            String resource = types.find(type.getName()).map(JsonSchemaTypes.JsonSchemaType::resource).orElseThrow();
            AnnotationValue<JsonSchema> annotation = BeanIntrospection.getIntrospection(type).getAnnotation(JsonSchema.class);
            String name = annotation == null ? null : annotation.stringValue("uri").map(uri -> uri.replace("/", "")).orElse(null);
            String expected = (name == null ? NameUtils.hyphenate(type.getSimpleName()) : name) + ".schema.json";
            if (resource.endsWith("/" + expected)) {
                return null;
            }
            try (InputStream in = environment.getResourceAsStream(resource).orElse(null)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read the JSON schema " + resource, e);
            }
        }
    }

    /**
     * The validators backed by the {@code json-validator} component of {@code camel-json-validator}, when the
     * Micronaut JSON Schema validator is not there: the two use incompatible versions of the NetworkNT JSON
     * Schema library, so an application has one or the other.
     */
    @Singleton
    @Internal
    @Requires(beans = JsonSchemaTypes.class)
    @Requires(classes = JsonValidatorComponent.class)
    @Requires(missingClasses = "io.micronaut.jsonschema.validation.JsonSchemaValidator")
    static final class Component extends JsonSchemaValidators {

        /**
         * @param types The {@code @JsonSchema} types
         */
        Component(JsonSchemaTypes types) {
            super(types, JsonValidatorEndpointValidator::new);
        }
    }
}
