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

import io.micronaut.sourcegen.model.TypeDef;
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
    void declaresTheValuesOfComponentOptionsWithAllowedValues() {
        ComponentModel kafka = catalog.componentModel("kafka");
        Map<String, BaseOptionModel> options = ConfigurationGenerator.options(List.copyOf(kafka.getComponentOptions()), BaseOptionModel::getName);

        // an option of type String, which Camel compares exactly: Micronaut writes @AllowedValues as the enum
        assertEquals(List.of("latest", "earliest", "none"),
            ValuesSchemaGenerator.allowedValues(options.get("autoOffsetReset"), ValuesSchemaGeneratorTest::isEnum));
        // booleans are not listed
        assertNull(ValuesSchemaGenerator.allowedValues(options.get("allowManualCommit"), ValuesSchemaGeneratorTest::isEnum));
        String schema = ValuesSchemaGenerator.schema("test.KafkaComponentConfiguration", "camel.component.kafka", options, ValuesSchemaGeneratorTest::isEnum);
        assertTrue(schema == null || !schema.contains("\"auto-offset-reset\""), schema);
    }

    @Test
    void declaresTheValuesOfEnumOptionsAsStrings() {
        ComponentModel jms = catalog.componentModel("jms");
        Map<String, BaseOptionModel> options = ConfigurationGenerator.options(List.copyOf(jms.getComponentOptions()), BaseOptionModel::getName);

        // an enum option of an int type is a String property, as Camel converts the value
        assertEquals(TypeDef.STRING, ConfigurationGenerator.type(options.get("priority")));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            ValuesSchemaGenerator.allowedValues(options.get("priority"), ValuesSchemaGeneratorTest::isEnum));
    }

    @Test
    void listsTheValuesOfEnumOptionsIgnoringCase() {
        List<MainModel.MainOptionModel> main = CamelConfigVisitor.mainGroups(catalog.mainModel()).get("camel.main");
        Map<String, MainModel.MainOptionModel> options = ConfigurationGenerator.options(main, option -> option.getName().substring("camel.main.".length()));
        String schema = ValuesSchemaGenerator.schema("test.CamelMainConfiguration", "camel.main", options, ValuesSchemaGeneratorTest::isEnum);

        assertTrue(schema.contains("\"x-micronaut\":{\"prefix\":\"camel.main\",\"type\":\"test.CamelMainConfiguration\""), schema);
        // an option of a Java enum type, which Camel converts ignoring case, what @AllowedValues cannot express
        assertNull(ValuesSchemaGenerator.allowedValues(options.get("jmxManagementStatisticsLevel"), ValuesSchemaGeneratorTest::isEnum));
        assertTrue(schema.contains("\"jmx-management-statistics-level\":{\"type\":\"string\","
            + "\"pattern\":\"^(?:[Ee][Xx][Tt][Ee][Nn][Dd][Ee][Dd]|[Dd][Ee][Ff][Aa][Uu][Ll][Tt]|[Rr][Oo][Uu][Tt][Ee][Ss][Oo][Nn][Ll][Yy]|[Oo][Ff][Ff])$\","
            + "\"examples\":[\"Extended\",\"Default\",\"RoutesOnly\",\"Off\"],"
            + "\"description\":\"One of Extended, Default, RoutesOnly, Off, ignoring case.\""), schema);
        assertTrue(schema.contains("\"x-micronaut-path\":\"camel.main.jmx-management-statistics-level\""), schema);
        // an option of type String, which Camel compares exactly, has @AllowedValues instead
        assertEquals(List.of("classic", "default", "short", "simple", "off"),
            ValuesSchemaGenerator.allowedValues(options.get("uuidGenerator"), ValuesSchemaGeneratorTest::isEnum));
        assertFalse(schema.contains("\"uuid-generator\""), schema);
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
