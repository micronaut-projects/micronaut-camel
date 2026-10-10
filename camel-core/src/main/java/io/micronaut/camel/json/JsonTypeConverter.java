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
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.TypeConverterSupport;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fallback type converter between the types that Micronaut Serialization serializes
 * ({@code @Serdeable} types) and JSON:
 *
 * <ul>
 *     <li>JSON objects in a {@code String}, {@code byte[]} or {@code InputStream} to a serializable type: the
 *     converter looks at the first character first, and converts only JSON objects, so the other converters
 *     can convert other content, such as XML; it reads only streams that it can reset, such as Camel's stream
 *     caches</li>
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
    // the leading whitespace a stream may have before its first character
    private static final int PEEK_LIMIT = 8192;
    // the byte order mark of UTF-8
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

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
                return isJsonObject(bytes) ? jsonMapper.readValue(bytes, type) : null;
            }
            if (value instanceof InputStream in) {
                return isJsonObject(in) ? jsonMapper.readValue(in, type) : null;
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

    private static boolean isJsonObject(byte[] bytes) {
        for (int i = startOfContent(bytes); i < bytes.length; i++) {
            if (!isWhitespace(bytes[i])) {
                return bytes[i] == '{';
            }
        }
        return false;
    }

    /**
     * Whether a stream holds a JSON object, from its first character, which is read and reset: a stream that
     * cannot be reset is never read, as the other converters could not read it any more.
     */
    private static boolean isJsonObject(InputStream in) throws IOException {
        if (in instanceof StreamCache cache && !in.markSupported()) {
            // a stream cache resets to its start
            boolean object = startsWithBrace(in, Integer.MAX_VALUE);
            cache.reset();
            return object;
        }
        if (!in.markSupported()) {
            return false;
        }
        in.mark(PEEK_LIMIT);
        try {
            return startsWithBrace(in, PEEK_LIMIT);
        } finally {
            in.reset();
        }
    }

    private static boolean startsWithBrace(InputStream in, int limit) throws IOException {
        for (int i = 0; i < limit; i++) {
            int b = in.read();
            if (b < 0) {
                return false;
            }
            if (i < BOM.length && b == (BOM[i] & 0xFF)) {
                continue;
            }
            if (!isWhitespace((byte) b)) {
                return b == '{';
            }
        }
        return false;
    }

    private static int startOfContent(byte[] bytes) {
        return bytes.length >= BOM.length && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2] ? BOM.length : 0;
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
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
