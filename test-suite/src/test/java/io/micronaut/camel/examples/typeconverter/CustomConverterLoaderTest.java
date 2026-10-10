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
package io.micronaut.camel.examples.typeconverter;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Added to the {@code type-converter} example of camel-spring-boot-examples, whose tests register the
 * converters at runtime: checks that the copied {@link CustomGeneratedConverterLoader} is found through
 * {@code META-INF/services/org/apache/camel/TypeConverterLoader}, without registering anything in the test.
 */
@MicronautTest(environments = "example-type-converter")
class CustomConverterLoaderTest {

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void generatedLoaderRegistersTheConverter() {
        Person person = producerTemplate.requestBody("direct:convert1", "Jane Doe 33".getBytes(), Person.class);

        assertEquals("Jane", person.getFirstName());
        assertEquals("Doe", person.getLastName());
        assertEquals(33, person.getAge());
    }
}
