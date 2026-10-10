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
package io.micronaut.camel.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.TypeConverterSupport;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fallback type converter between the types that Micronaut Serialization serializes
 * ({@code @Serdeable} types) and JSON:
 *
 * <ul>
 *     <li>JSON objects in a {@code String}, {@code byte[]} or {@code InputStream} to a serializable type</li>
 *     <li>a serializable type to a JSON {@code String}, instead of its {@code toString()}</li>
 *     <li>a serializable type to a {@code Map}</li>
 * </ul>
 *
 * <p>Maps to serializable types are converted by the Micronaut conversion service already.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class JsonTypeConverter extends TypeConverterSupport {

    private static final String SERIALIZABLE = "io.micronaut.serde.annotation.Serdeable$Serializable";
    private static final String DESERIALIZABLE = "io.micronaut.serde.annotation.Serdeable$Deserializable";

    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper The JSON mapper
     */
    JsonTypeConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T convertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value)
        throws TypeConversionException {
        if (value == null) {
            return null;
        }
        try {
            if (type == String.class) {
                return isSerdeable(value.getClass(), SERIALIZABLE) ? (T) jsonMapper.writeValueAsString(value) : null;
            }
            if (Map.class.isAssignableFrom(type) && type.isAssignableFrom(LinkedHashMap.class)) {
                if (!(value instanceof Map<?, ?>) && isSerdeable(value.getClass(), SERIALIZABLE)) {
                    return (T) jsonMapper.readValueFromTree(jsonMapper.writeValueToTree(value), Argument.mapOf(String.class, Object.class));
                }
                return null;
            }
            if (!isSerdeable(type, DESERIALIZABLE) || type.isInstance(value)) {
                return null;
            }
            if (value instanceof CharSequence text) {
                return isJsonObject(text) ? jsonMapper.readValue(text.toString(), type) : null;
            }
            if (value instanceof byte[] bytes) {
                return isJsonObject(new String(bytes, StandardCharsets.UTF_8)) ? jsonMapper.readValue(bytes, type) : null;
            }
            if (value instanceof InputStream in) {
                return jsonMapper.readValue(in, type);
            }
            return null;
        } catch (IOException | RuntimeException e) {
            throw new TypeConversionException(value, type, e);
        }
    }

    private static boolean isSerdeable(Class<?> type, String annotation) {
        if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) {
            return false;
        }
        return BeanIntrospector.SHARED.findIntrospection(type)
            .map(introspection -> isSerdeable(introspection, annotation))
            .orElse(false);
    }

    private static boolean isSerdeable(BeanIntrospection<?> introspection, String annotation) {
        return introspection.hasStereotype(annotation);
    }

    private static boolean isJsonObject(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == '{';
            }
        }
        return false;
    }
}
