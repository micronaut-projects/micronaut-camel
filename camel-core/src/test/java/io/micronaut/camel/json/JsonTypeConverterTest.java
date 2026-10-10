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
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "spec.name", value = "JsonTypeConverterTest")
class JsonTypeConverterTest {

    @Inject
    CamelContext camelContext;

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

    @Serdeable
    public record Book(String title, int pages) {
    }

    public record Note(String text) {
    }
}
