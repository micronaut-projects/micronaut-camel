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
import org.apache.camel.tooling.model.BaseOptionModel;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the JSON schema of the values of the Camel options that the catalog lists the possible values of,
 * such as {@code auto-offset-reset} of {@code camel.component.kafka}, as a complement to the configuration
 * schema that Micronaut writes for a generated {@code @ConfigurationProperties} class: Micronaut writes the
 * {@code enum} keyword only for properties of an enum type, while the generated classes keep the types of
 * Camel, often {@code String}. The schema has the prefix of the class, so the Micronaut JSON Schema
 * configuration validator validates the values with both schemas; it accepts any other property, which the
 * schema of the class validates.
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

    private ValuesSchemaGenerator() {
    }

    /**
     * @param className The name of the generated configuration class
     * @param prefix    Its prefix
     * @param options   The options of the class, by property name
     * @return The schema, or {@code null} when no option has a list of values
     */
    static @Nullable String schema(String className, String prefix, Map<String, ? extends BaseOptionModel> options) {
        Map<String, String> properties = new LinkedHashMap<>();
        options.forEach((name, option) -> {
            List<String> values = option.getEnums();
            if (values == null || values.isEmpty() || "boolean".equals(option.getType())) {
                return;
            }
            String javaType = option.getJavaType() == null ? "" : option.getJavaType();
            if (option.isMultiValue() || javaType.startsWith("java.util.Map") || javaType.startsWith("java.util.Properties")) {
                return;
            }
            String property = NameUtils.hyphenate(name);
            boolean list = "array".equals(option.getType()) || javaType.startsWith("java.util.List")
                || javaType.startsWith("java.util.Set") || javaType.startsWith("java.util.Collection");
            String valueType = "integer".equals(option.getType()) || "number".equals(option.getType()) ? option.getType() : "string";
            StringBuilder schema = new StringBuilder("{");
            StringBuilder enumSchema = new StringBuilder();
            enumSchema.append("\"type\":").append(string(valueType)).append(",\"enum\":[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    enumSchema.append(',');
                }
                enumSchema.append(value(values.get(i), valueType));
            }
            enumSchema.append(']');
            if (list) {
                // a comma separated list of values, or a list
                schema.append("\"anyOf\":[{\"type\":\"string\"},{\"type\":\"array\",\"items\":{").append(enumSchema).append("}}]");
            } else {
                schema.append(enumSchema);
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
}
