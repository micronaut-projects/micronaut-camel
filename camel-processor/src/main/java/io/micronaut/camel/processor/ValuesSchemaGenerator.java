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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.sourcegen.model.TypeDef;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The possible values of the Camel options that the catalog lists, such as {@code auto-offset-reset} of
 * {@code camel.component.kafka}, for the configuration schemas. The generated configuration classes keep the
 * types of Camel, often {@code String}, so the values are declared for Micronaut:
 *
 * <ul>
 * <li>Options whose values Camel compares exactly, such as those of type {@code String}, get the
 * {@code @AllowedValues} annotation of Micronaut on their property ({@link #allowedValues}), which Micronaut
 * writes as the {@code enum} of the property in the configuration schema of the class.</li>
 * <li>The values of an option of a Java enum type are matched as Camel's {@code EnumTypeConverter} matches
 * them, ignoring case (and with {@code -} for {@code _}), which an {@code enum} cannot express: such options
 * get a {@code pattern} that ignores case, written with character classes, as the regular expressions of JSON
 * Schema (ECMA-262) have no flag to ignore case, and list their values as {@code examples}, in a schema of the
 * values that complements the configuration schema of the class ({@link #schema}). The schema has the prefix of
 * the class, so the Micronaut JSON Schema configuration validator validates the values with both schemas; it
 * accepts any other property, which the schema of the class validates.</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ValuesSchemaGenerator {

    /**
     * The folder of the configuration schemas, under {@code META-INF}.
     */
    static final String SCHEMAS = "micronaut-configuration-schemas/";
    /**
     * The suffix of the generated schemas, after the name of the configuration class.
     */
    static final String SUFFIX = ".values.json";

    // the characters to escape in a regular expression: escaping others is an error with the Unicode flag of ECMA-262
    private static final String SYNTAX_CHARACTERS = "^$\\.*+?()[]{}|/";

    private ValuesSchemaGenerator() {
    }

    /**
     * The values of an option that Camel compares exactly, for the {@code @AllowedValues} annotation of its
     * property.
     *
     * @param option   The option
     * @param enumType Whether a Java type, by name, is an enum
     * @return The values, or {@code null} when the option has no list of values, or its values are matched
     * ignoring case or do not convert to the type of the property, which {@link #schema} covers
     */
    static @Nullable List<String> allowedValues(BaseOptionModel option, Predicate<String> enumType) {
        OptionValues values = optionValues(option, enumType);
        if (values == null || values.ignoreCase()) {
            return null;
        }
        TypeDef type = ConfigurationGenerator.type(option);
        for (String value : values.values()) {
            if (!converts(value, type)) {
                return null;
            }
        }
        return values.values();
    }

    /**
     * @param className The name of the generated configuration class
     * @param prefix    Its prefix
     * @param options   The options of the class, by property name
     * @param enumType  Whether a Java type, by name, is an enum
     * @return The schema of the values of the options that {@code @AllowedValues} cannot express, or {@code null}
     * when there are none
     */
    static @Nullable String schema(String className, String prefix, Map<String, ? extends BaseOptionModel> options,
                                   Predicate<String> enumType) {
        Map<String, String> properties = new LinkedHashMap<>();
        options.forEach((name, option) -> {
            OptionValues optionValues = optionValues(option, enumType);
            if (optionValues == null || allowedValues(option, enumType) != null) {
                return;
            }
            List<String> values = optionValues.values();
            String property = NameUtils.hyphenate(name);
            boolean list = optionValues.list();
            String valueType = optionValues.valueType();
            boolean ignoreCase = optionValues.ignoreCase();
            StringBuilder schema = new StringBuilder("{");
            StringBuilder valuesSchema = new StringBuilder();
            valuesSchema.append("\"type\":").append(string(valueType));
            if (ignoreCase) {
                // Camel converts the text to the enum constant ignoring case
                valuesSchema.append(",\"pattern\":").append(string(ignoreCasePattern(values)))
                    .append(",\"examples\":").append(array(values, valueType));
            } else {
                valuesSchema.append(",\"enum\":").append(array(values, valueType));
            }
            if (list) {
                // a comma separated list of values, or a list
                schema.append("\"anyOf\":[{\"type\":\"string\"},{\"type\":\"array\",\"items\":{").append(valuesSchema).append("}}]");
            } else {
                schema.append(valuesSchema);
            }
            if (ignoreCase) {
                schema.append(",\"description\":").append(string("One of " + String.join(", ", values) + ", ignoring case."));
            }
            schema.append(",\"x-micronaut-path\":").append(string(prefix + "." + property)).append('}');
            properties.put(property, schema.toString());
        });
        if (properties.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"");
        json.append(",\"$id\":").append(string("urn:micronaut:config:" + className + ":values"))
            .append(",\"title\":").append(string(className + " values"))
            .append(",\"description\":").append(string("The possible values of the options under " + prefix + ", from the Camel catalog."))
            .append(",\"x-micronaut\":{\"prefix\":").append(string(prefix))
            .append(",\"type\":").append(string(className))
            .append(",\"kind\":\"configuration-properties\"}")
            .append(",\"type\":\"object\",\"properties\":{");
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(string(property.getKey())).append(':').append(property.getValue());
        }
        // the other options are those of the schema of the class: unknown options are reported for that one only
        return json.append("},\"additionalProperties\":{}}").toString();
    }

    /**
     * @return The possible values of an option, or {@code null} when the catalog lists none, the option is a
     * boolean, or a map
     */
    private static @Nullable OptionValues optionValues(BaseOptionModel option, Predicate<String> enumType) {
        List<String> values = option.getEnums();
        if (values == null || values.isEmpty() || "boolean".equals(option.getType())) {
            return null;
        }
        String javaType = option.getJavaType() == null ? "" : option.getJavaType();
        if (option.isMultiValue() || javaType.startsWith("java.util.Map") || javaType.startsWith("java.util.Properties")) {
            return null;
        }
        boolean list = "array".equals(option.getType()) || javaType.startsWith("java.util.List")
            || javaType.startsWith("java.util.Set") || javaType.startsWith("java.util.Collection");
        String valueType = "integer".equals(option.getType()) || "number".equals(option.getType()) ? option.getType() : "string";
        String elementType = list ? elementType(javaType) : javaType;
        boolean ignoreCase = "string".equals(valueType) && !elementType.isEmpty() && enumType.test(elementType);
        return new OptionValues(List.copyOf(values), list, valueType, ignoreCase);
    }

    /**
     * @return Whether a value converts to the type of the generated property, as Micronaut writes the values of
     * {@code @AllowedValues} with the type of the property, and fails the compilation for others
     */
    private static boolean converts(String value, TypeDef type) {
        try {
            if (TypeDef.of(Integer.class).equals(type)) {
                Integer.parseInt(value.trim());
            } else if (TypeDef.of(Long.class).equals(type)) {
                Long.parseLong(value.trim());
            } else if (TypeDef.of(Float.class).equals(type)) {
                return Float.isFinite(Float.parseFloat(value.trim()));
            } else if (TypeDef.of(Double.class).equals(type)) {
                return Double.isFinite(Double.parseDouble(value.trim()));
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * @param javaType The Java type of an option, such as {@code java.util.List<org.apache.camel.LoggingLevel>}
     * @return The type of its elements, without type arguments, or an empty string when it has none
     */
    static String elementType(String javaType) {
        int start = javaType.indexOf('<');
        int end = javaType.lastIndexOf('>');
        if (start < 0 || end < start) {
            return "";
        }
        String argument = javaType.substring(start + 1, end).trim();
        return argument.indexOf('<') >= 0 || argument.indexOf(',') >= 0 ? "" : argument;
    }

    /**
     * A regular expression matching the values ignoring case, as Camel's {@code EnumTypeConverter}, written
     * with a character class per letter (such as {@code [Ii][Nn][Ff][Oo]}), which every regular expression
     * dialect of JSON Schema supports. An underscore also matches a dash, as Camel converts {@code json-xml}
     * to {@code JSON_XML}.
     *
     * @param values The values
     * @return The pattern
     */
    static String ignoreCasePattern(List<String> values) {
        StringBuilder pattern = new StringBuilder("^(?:");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                pattern.append('|');
            }
            for (char c : values.get(i).toCharArray()) {
                char upper = Character.toUpperCase(c);
                char lower = Character.toLowerCase(c);
                if (upper != lower) {
                    pattern.append('[').append(upper).append(lower).append(']');
                } else if (c == '_' || c == '-') {
                    pattern.append("[_-]");
                } else if (SYNTAX_CHARACTERS.indexOf(c) >= 0) {
                    pattern.append('\\').append(c);
                } else {
                    pattern.append(c);
                }
            }
        }
        return pattern.append(")$").toString();
    }

    private static String array(List<String> values, String type) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                array.append(',');
            }
            array.append(value(values.get(i), type));
        }
        return array.append(']').toString();
    }

    private static String value(String value, String type) {
        if (!"string".equals(type)) {
            try {
                return "integer".equals(type) ? Long.toString(Long.parseLong(value)) : Double.toString(Double.parseDouble(value));
            } catch (NumberFormatException e) {
                // not a number after all
            }
        }
        return string(value);
    }

    private static String string(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    /**
     * The possible values of an option.
     *
     * @param values     The values
     * @param list       Whether the option is a list of values
     * @param valueType  The JSON type of the values
     * @param ignoreCase Whether Camel matches the values ignoring case, as for the constants of a Java enum
     */
    private record OptionValues(List<String> values, boolean list, String valueType, boolean ignoreCase) {
    }
}
