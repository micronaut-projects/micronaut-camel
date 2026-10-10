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
package io.micronaut.camel.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import jakarta.inject.Singleton;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.openapi.OpenApiRestApiProcessorFactory;
import org.apache.camel.spi.RestApiProcessorFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The API documents of the REST DSL, as camel-openapi-java renders them, with the JSON schemas that Micronaut
 * JSON Schema generated at build time for the {@code @JsonSchema} types of {@code type(...)} and
 * {@code outType(...)} in place of the schemas that camel-openapi-java resolves by reflection: the schemas of
 * {@code components.schemas} whose class is such a type are replaced, converted to the schema dialect of the
 * OpenAPI version of the document, and their references to other generated schemas point to the components of
 * those. An application that has a {@code RestApiProcessorFactory} bean of its own keeps it.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(beans = JsonSchemaTypes.class)
@Requires(classes = OpenApiRestApiProcessorFactory.class)
// Camel uses a factory of the registry only when there is one
@Requires(missingBeans = RestApiProcessorFactory.class)
@Requires(property = JsonSchemaRestApiProcessorFactory.ENABLED, notEquals = StringUtils.FALSE)
final class JsonSchemaRestApiProcessorFactory implements RestApiProcessorFactory {

    /**
     * Whether the API documents use the generated JSON schemas.
     */
    static final String ENABLED = "micronaut.camel.json-schema.openapi.enabled";
    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaRestApiProcessorFactory.class);
    private static final String CLASS_NAME_EXTENSION = "x-className";
    private static final String COMPONENTS_PREFIX = "#/components/schemas/";
    private static final String REF = "$ref";

    private final JsonSchemaTypes types;
    private final Environment environment;
    private final RestApiProcessorFactory openApi = new OpenApiRestApiProcessorFactory();
    private volatile @Nullable Map<String, Schema> schemas;

    /**
     * @param types       The {@code @JsonSchema} types
     * @param environment The environment, to read the schemas from
     */
    JsonSchemaRestApiProcessorFactory(JsonSchemaTypes types, Environment environment) {
        this.types = types;
        this.environment = environment;
    }

    @Override
    public Processor createApiProcessor(CamelContext camelContext, String contextPath, RestConfiguration configuration,
                                        Map<String, Object> parameters) throws Exception {
        return new DelegateSyncProcessor(openApi.createApiProcessor(camelContext, contextPath, configuration, parameters)) {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                try {
                    processor.process(exchange);
                    if (exchange.getException() == null) {
                        replaceSchemas(exchange.getMessage());
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                }
                callback.done(true);
                return true;
            }
        };
    }

    private void replaceSchemas(Message message) throws IOException {
        Map<String, Schema> generated = schemas();
        byte[] body = message.getBody(byte[].class);
        if (generated.isEmpty() || body == null || body.length == 0) {
            return;
        }
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        ObjectMapper mapper = contentType != null && contentType.contains("yaml") ? Yaml.mapper() : Json.mapper();
        if (!(mapper.readTree(body) instanceof ObjectNode document)
            || !(document.at("/components/schemas") instanceof ObjectNode components)) {
            return;
        }
        boolean openApi30 = document.path("openapi").asText("").startsWith("3.0");
        // the components of the types, named by camel-openapi-java after the simple or qualified name of the class
        Map<String, String> componentsByClass = new HashMap<>();
        components.properties().forEach(component -> types.find(className(component.getKey(), component.getValue()))
            .ifPresent(type -> componentsByClass.putIfAbsent(type.type().getName(), component.getKey())));
        boolean replaced = false;
        for (Map.Entry<String, String> component : componentsByClass.entrySet()) {
            Schema schema = generated.get(component.getKey());
            if (schema != null) {
                ObjectNode replacement = convert(schema.schema().deepCopy(), openApi30, generated, componentsByClass);
                JsonNode extension = components.path(component.getValue()).get(CLASS_NAME_EXTENSION);
                if (extension != null) {
                    replacement.set(CLASS_NAME_EXTENSION, extension);
                }
                components.set(component.getValue(), replacement);
                replaced = true;
            }
        }
        if (replaced) {
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
            message.setBody(bytes);
            message.setHeader(Exchange.CONTENT_LENGTH, Integer.toString(bytes.length));
        }
    }

    private static String className(String name, JsonNode component) {
        JsonNode extension = component.path(CLASS_NAME_EXTENSION).path("format");
        return extension.isTextual() ? extension.asText() : name;
    }

    /**
     * Converts a generated schema, of the 2020-12 dialect, to a schema of the components of an OpenAPI document.
     */
    private static ObjectNode convert(ObjectNode schema, boolean openApi30, Map<String, Schema> generated, Map<String, String> componentsByClass) {
        schema.remove("$schema");
        schema.remove("$id");
        JsonNode ref = schema.get(REF);
        if (ref != null && ref.isTextual()) {
            generated.values().stream()
                .filter(s -> ref.asText().equals(s.id()))
                .map(s -> componentsByClass.get(s.type().getName()))
                .filter(name -> name != null)
                .findFirst()
                .ifPresent(name -> schema.put(REF, COMPONENTS_PREFIX + name));
        }
        if (openApi30) {
            exclusiveBound(schema, "exclusiveMinimum", "minimum");
            exclusiveBound(schema, "exclusiveMaximum", "maximum");
            if (schema.get("type") instanceof ArrayNode typeNames) {
                List<String> names = new ArrayList<>();
                typeNames.forEach(t -> {
                    if (!"null".equals(t.asText())) {
                        names.add(t.asText());
                    }
                });
                if (names.size() < typeNames.size()) {
                    schema.put("nullable", true);
                }
                if (names.size() == 1) {
                    schema.put("type", names.getFirst());
                } else {
                    schema.remove("type");
                }
            }
            JsonNode constant = schema.remove("const");
            if (constant != null) {
                schema.putArray("enum").add(constant);
            }
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = schema.properties().iterator(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "properties", "patternProperties", "$defs" -> value.properties().forEach(property -> {
                    if (property.getValue() instanceof ObjectNode child) {
                        convert(child, openApi30, generated, componentsByClass);
                    }
                });
                case "items", "additionalProperties", "not", "contains", "propertyNames" -> {
                    if (value instanceof ObjectNode child) {
                        convert(child, openApi30, generated, componentsByClass);
                    }
                }
                case "allOf", "anyOf", "oneOf", "prefixItems" -> value.forEach(element -> {
                    if (element instanceof ObjectNode child) {
                        convert(child, openApi30, generated, componentsByClass);
                    }
                });
                default -> {
                    // a keyword without subschemas
                }
            }
        }
        return schema;
    }

    private static void exclusiveBound(ObjectNode schema, String exclusive, String inclusive) {
        JsonNode bound = schema.get(exclusive);
        if (bound != null && bound.isNumber()) {
            schema.set(inclusive, bound);
            schema.put(exclusive, true);
        }
    }

    private Map<String, Schema> schemas() {
        Map<String, Schema> result = schemas;
        if (result == null) {
            result = new HashMap<>();
            for (JsonSchemaTypes.JsonSchemaType type : types.types()) {
                try (InputStream in = environment.getResourceAsStream(type.resource()).orElse(null)) {
                    if (in != null && Json.mapper().readTree(in) instanceof ObjectNode schema) {
                        result.put(type.type().getName(), new Schema(type.type(), schema.path("$id").asText(null), schema));
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to read the JSON schema {} of {}: {}", type.resource(), type.type().getName(), e.getMessage());
                }
            }
            result = Map.copyOf(result);
            schemas = result;
        }
        return result;
    }

    /**
     * A generated schema.
     *
     * @param type   The type
     * @param id     Its {@code $id}
     * @param schema The schema
     */
    private record Schema(Class<?> type, @Nullable String id, ObjectNode schema) {
    }
}
