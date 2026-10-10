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
package io.micronaut.camel.jackson;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * With {@code autoDiscoverObjectMapper} ({@code camel.dataformat.jackson.auto-discover-object-mapper}), the
 * Jackson data formats use the object mapper of the Camel registry when it holds exactly one, as their
 * {@code useDefaultObjectMapper} option is {@code true} by default. Micronaut 5's micronaut-jackson-databind
 * creates a Jackson 3 {@code JsonMapper}, which camel-jackson3 uses; camel-jackson, on Jackson 2, uses a
 * Jackson 2 {@code ObjectMapper} bean of the application. Both artifacts name their data format
 * {@code jackson}, so the test creates them directly.
 */
@MicronautTest
@Property(name = "spec.name", value = "JacksonBridgeTest")
class JacksonBridgeTest {

    @Inject
    CamelContext camelContext;

    @Inject
    tools.jackson.databind.ObjectMapper micronautMapper;

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;

    @Test
    void camelJackson3UsesTheMicronautObjectMapper() {
        var dataFormat = new org.apache.camel.component.jackson3.JacksonDataFormat();
        dataFormat.setCamelContext(camelContext);
        dataFormat.setAutoDiscoverObjectMapper(true);
        ServiceHelper.startService(dataFormat);

        assertSame(micronautMapper, dataFormat.getObjectMapper());
    }

    @Test
    void camelJackson3CreatesItsOwnObjectMapperByDefault() {
        var dataFormat = new org.apache.camel.component.jackson3.JacksonDataFormat();
        dataFormat.setCamelContext(camelContext);
        ServiceHelper.startService(dataFormat);

        assertNotSame(micronautMapper, dataFormat.getObjectMapper());
    }

    @Test
    void camelJacksonUsesTheJackson2ObjectMapperBean() {
        var dataFormat = new org.apache.camel.component.jackson.JacksonDataFormat();
        dataFormat.setCamelContext(camelContext);
        dataFormat.setAutoDiscoverObjectMapper(true);
        ServiceHelper.startService(dataFormat);

        assertSame(jackson2Mapper, dataFormat.getObjectMapper());
    }

    @Factory
    @Requires(property = "spec.name", value = "JacksonBridgeTest")
    static class Jackson2Mapper {

        @Singleton
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }
}
