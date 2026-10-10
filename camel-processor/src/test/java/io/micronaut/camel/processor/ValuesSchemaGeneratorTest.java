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
package io.micronaut.camel.processor;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.MainModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesSchemaGeneratorTest {

    private final DefaultCamelCatalog catalog = new DefaultCamelCatalog(true);

    @Test
    void listsThePossibleValuesOfComponentOptions() {
        ComponentModel kafka = catalog.componentModel("kafka");
        Map<String, BaseOptionModel> options = ConfigurationGenerator.options(List.copyOf(kafka.getComponentOptions()), BaseOptionModel::getName);
        String schema = ValuesSchemaGenerator.schema("test.KafkaComponentConfiguration", "camel.component.kafka", options, ValuesSchemaGeneratorTest::isEnum);

        assertTrue(schema.contains("\"x-micronaut\":{\"prefix\":\"camel.component.kafka\",\"type\":\"test.KafkaComponentConfiguration\""), schema);
        assertTrue(schema.contains("\"auto-offset-reset\":{\"type\":\"string\",\"enum\":[\"latest\",\"earliest\",\"none\"]"), schema);
        assertTrue(schema.contains("\"x-micronaut-path\":\"camel.component.kafka.auto-offset-reset\""), schema);
        // booleans are not listed
        assertFalse(schema.contains("\"allow-manual-commit\""), schema);
    }

    @Test
    void listsTheValuesOfEnumOptionsAsStrings() {
        ComponentModel jms = catalog.componentModel("jms");
        Map<String, BaseOptionModel> options = ConfigurationGenerator.options(List.copyOf(jms.getComponentOptions()), BaseOptionModel::getName);
        String schema = ValuesSchemaGenerator.schema("test.JmsComponentConfiguration", "camel.component.jms", options, ValuesSchemaGeneratorTest::isEnum);

        // an enum option of an int type is a String property, as Camel converts the value
        assertTrue(schema.contains("\"priority\":{\"type\":\"string\",\"enum\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]"), schema);
    }

    @Test
    void listsThePossibleValuesOfMainOptions() {
        List<MainModel.MainOptionModel> main = CamelConfigVisitor.mainGroups(catalog.mainModel()).get("camel.main");
        String schema = ValuesSchemaGenerator.schema("test.CamelMainConfiguration", "camel.main",
            ConfigurationGenerator.options(main, option -> option.getName().substring("camel.main.".length())),
            ValuesSchemaGeneratorTest::isEnum);

        // an option of a Java enum type, which Camel converts ignoring case
        assertTrue(schema.contains("\"jmx-management-statistics-level\":{\"type\":\"string\","
            + "\"pattern\":\"^(?:[Ee][Xx][Tt][Ee][Nn][Dd][Ee][Dd]|[Dd][Ee][Ff][Aa][Uu][Ll][Tt]|[Rr][Oo][Uu][Tt][Ee][Ss][Oo][Nn][Ll][Yy]|[Oo][Ff][Ff])$\","
            + "\"examples\":[\"Extended\",\"Default\",\"RoutesOnly\",\"Off\"],"
            + "\"description\":\"One of Extended, Default, RoutesOnly, Off, ignoring case.\""), schema);
        // an option of type String, which Camel compares exactly
        assertTrue(schema.contains("\"uuid-generator\":{\"type\":\"string\",\"enum\":[\"classic\",\"default\",\"short\",\"simple\",\"off\"]"), schema);
    }

    @Test
    void matchesTheValuesOfEnumOptionsIgnoringCase() {
        Pattern pattern = Pattern.compile(ValuesSchemaGenerator.ignoreCasePattern(List.of("INFO", "WARN", "JSON_XML", "a.b")));

        for (String value : List.of("INFO", "info", "Info", "warn", "JSON_XML", "json_xml", "json-xml", "a.b")) {
            assertTrue(pattern.matcher(value).matches(), value);
        }
        for (String value : List.of("bogus", "INFOS", "jsonxml", "axb", "")) {
            assertFalse(pattern.matcher(value).matches(), value);
        }
    }

    @Test
    void findsTheElementTypeOfLists() {
        assertEquals("org.apache.camel.LoggingLevel", ValuesSchemaGenerator.elementType("java.util.List<org.apache.camel.LoggingLevel>"));
        assertEquals("", ValuesSchemaGenerator.elementType("java.lang.String"));
    }

    private static boolean isEnum(String type) {
        try {
            return Class.forName(type).isEnum();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    void hasNoSchemaWithoutPossibleValues() {
        ComponentModel direct = catalog.componentModel("direct");
        assertNull(ValuesSchemaGenerator.schema("test.DirectComponentConfiguration", "camel.component.direct",
            ConfigurationGenerator.options(List.copyOf(direct.getComponentOptions()), BaseOptionModel::getName),
            ValuesSchemaGeneratorTest::isEnum));
    }
}
