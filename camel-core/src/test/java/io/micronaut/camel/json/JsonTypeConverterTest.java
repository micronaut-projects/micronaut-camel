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

import io.micronaut.context.annotation.Property;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.stream.InputStreamCache;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "JsonTypeConverterTest")
class JsonTypeConverterTest {

    @Inject
    CamelContext camelContext;

    @Inject
    JsonMapper jsonMapper;

    TypeConverter converter() {
        return camelContext.getTypeConverter();
    }

    @Test
    void convertsJsonToSerdeableTypes() {
        assertEquals(new Book("Camel", 100), converter().convertTo(Book.class, "{\"title\":\"Camel\",\"pages\":100}"));
        assertEquals(new Book("Camel", 100), converter().convertTo(Book.class, "{\"title\":\"Camel\",\"pages\":100}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(new Book("Camel", 100), converter().convertTo(Book.class,
            new ByteArrayInputStream("{\"title\":\"Camel\",\"pages\":100}".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void convertsSerdeableTypesToJson() {
        assertEquals("{\"title\":\"Camel\",\"pages\":100}", converter().convertTo(String.class, new Book("Camel", 100)));
    }

    @Test
    void convertsSerdeableTypesToMapsAndBack() {
        assertEquals(Map.of("title", "Camel", "pages", 100), converter().convertTo(Map.class, new Book("Camel", 100)));
        assertEquals(new Book("Camel", 100), converter().convertTo(Book.class, Map.of("title", "Camel", "pages", 100)));
    }

    @Test
    void leavesOtherTypesToCamel() {
        assertEquals("Note[text=hello]", converter().convertTo(String.class, new Note("hello")));
        assertNull(converter().tryConvertTo(Book.class, "not json"));
    }

    @Test
    void failsForInvalidJson() {
        assertThrows(TypeConversionException.class, () -> converter().convertTo(Book.class, "{\"title\":"));
    }

    @Test
    void leavesStreamsThatAreNotJsonObjectsUnread() throws Exception {
        JsonTypeConverter jsonConverter = new JsonTypeConverter(jsonMapper);
        for (String content : List.of("<book><title>Camel</title></book>", "  [{\"title\":\"Camel\",\"pages\":100}]", "")) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);

            assertNull(jsonConverter.convertTo(Book.class, null, in), content);
            // the other converters can still read the stream
            assertArrayEquals(bytes, in.readAllBytes(), content);
        }
    }

    @Test
    void neverReadsAStreamItCannotReset() throws Exception {
        JsonTypeConverter jsonConverter = new JsonTypeConverter(jsonMapper);
        byte[] bytes = "{\"title\":\"Camel\",\"pages\":100}".getBytes(StandardCharsets.UTF_8);
        InputStream in = new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };

        assertNull(jsonConverter.convertTo(Book.class, null, in));
        assertArrayEquals(bytes, in.readAllBytes());
    }

    @Test
    void convertsCachedStreams() {
        byte[] bytes = "\n {\"title\":\"Camel\",\"pages\":100}".getBytes(StandardCharsets.UTF_8);
        assertEquals(new Book("Camel", 100), converter().convertTo(Book.class, new InputStreamCache(bytes)));
    }

    @Serdeable
    public record Book(String title, int pages) {
    }

    public record Note(String text) {
    }
}
