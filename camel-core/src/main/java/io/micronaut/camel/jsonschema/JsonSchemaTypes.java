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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jsonschema.JsonSchema;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The types annotated with {@code @JsonSchema} and the classpath resources of their schemas, which the
 * Micronaut JSON Schema annotation processor writes under {@code META-INF/schemas} at build time. The types
 * are those of the bean introspections, computed at build time too: nothing is scanned or reflected on.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = JsonSchema.class)
@Requires(property = JsonSchemaTypes.ENABLED, notEquals = StringUtils.FALSE)
final class JsonSchemaTypes {

    /**
     * Whether the JSON schema integration is enabled.
     */
    static final String ENABLED = "micronaut.camel.json-schema.enabled";
    private static final String SUFFIX = ".schema.json";
    private static final String SLASH = "/";

    private final String outputLocation;
    private volatile @Nullable List<JsonSchemaType> types;

    /**
     * @param outputLocation The folder of the schemas under {@code META-INF}, as configured for the annotation
     *                       processor with {@code micronaut.jsonschema.outputLocation}
     */
    JsonSchemaTypes(@Value("${micronaut.jsonschema.output-location:schemas}") String outputLocation) {
        this.outputLocation = outputLocation;
    }

    /**
     * @return The {@code @JsonSchema} types, with the resources of their schemas
     */
    List<JsonSchemaType> types() {
        List<JsonSchemaType> result = types;
        if (result == null) {
            result = new ArrayList<>();
            for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(JsonSchema.class)) {
                AnnotationValue<JsonSchema> annotation = introspection.getAnnotation(JsonSchema.class);
                if (annotation != null) {
                    Class<?> type = introspection.getBeanType();
                    result.add(new JsonSchemaType(type, "META-INF/" + outputLocation + SLASH + fileName(type, annotation)));
                }
            }
            result.sort(Comparator.comparing(t -> t.type().getName()));
            result = List.copyOf(result);
            types = result;
        }
        return result;
    }

    /**
     * Finds a type by its fully qualified name, its simple name or the name of its schema, such as
     * {@code order} for {@code META-INF/schemas/order.schema.json}.
     *
     * @param name The name
     * @return The type
     */
    Optional<JsonSchemaType> find(String name) {
        List<JsonSchemaType> all = types();
        for (JsonSchemaType type : all) {
            if (type.type().getName().equals(name)) {
                return Optional.of(type);
            }
        }
        for (JsonSchemaType type : all) {
            if (type.type().getSimpleName().equals(name) || type.schemaName().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * The classpath resource of a schema: that of the type of the given name, or else the schema file of
     * that name, such as {@code META-INF/schemas/order.schema.json} for {@code order}.
     *
     * @param name The name of a type or of a schema
     * @return The path of the resource
     */
    String resource(String name) {
        return find(name).map(JsonSchemaType::resource)
            .orElseGet(() -> "META-INF/" + outputLocation + SLASH + (name.endsWith(SUFFIX) ? name : name + SUFFIX));
    }

    /**
     * The file name of a schema, as the annotation processor computes it: from the {@code uri} of the
     * annotation, or else from its title or the simple name of the type, in kebab case.
     *
     * @param type       The type
     * @param annotation The annotation
     * @return The name of the schema file
     */
    String fileName(Class<?> type, AnnotationValue<JsonSchema> annotation) {
        String uri = annotation.stringValue("uri").orElse(null);
        String id;
        if (uri == null) {
            String simpleName = type.getName().substring(type.getName().lastIndexOf('.') + 1).replace('$', '.');
            id = kebabCase(annotation.stringValue("title").orElse(simpleName)) + SUFFIX;
        } else if (uri.contains("://")) {
            id = URI.create(uri).getPath();
            id = id.startsWith(SLASH) ? id.substring(1) : id;
            id = id.startsWith(outputLocation) ? id.substring(outputLocation.length()) : id;
        } else {
            id = uri + SUFFIX;
        }
        return id.startsWith(SLASH) ? id.substring(1) : id;
    }

    private static String kebabCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean newWord = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                continue;
            }
            if (Character.isUpperCase(c)) {
                if (!newWord) {
                    result.append('-');
                }
                newWord = true;
            } else {
                newWord = false;
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    /**
     * A type annotated with {@code @JsonSchema}.
     *
     * @param type     The type
     * @param resource The classpath resource of its schema
     */
    record JsonSchemaType(Class<?> type, String resource) {

        /**
         * @return The name of the schema, its file name without {@code .schema.json}
         */
        String schemaName() {
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            return name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name;
        }
    }
}
