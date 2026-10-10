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

import io.micronaut.camel.json.JsonTypeConverterTest.Book;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@Property(name = "micronaut.camel.json.type-converter.enabled", value = "false")
class JsonTypeConverterDisabledTest {

    @Inject
    CamelContext camelContext;

    @Test
    void camelConvertsWithoutJson() {
        assertNull(camelContext.getTypeConverter().tryConvertTo(Book.class, "{\"title\":\"Camel\",\"pages\":100}"));
        assertEquals("Book[title=Camel, pages=100]", camelContext.getTypeConverter().convertTo(String.class, new Book("Camel", 100)));
        // maps are converted by the Micronaut conversion service
        assertEquals(new Book("Camel", 100), camelContext.getTypeConverter().convertTo(Book.class, Map.of("title", "Camel", "pages", 100)));
    }
}
