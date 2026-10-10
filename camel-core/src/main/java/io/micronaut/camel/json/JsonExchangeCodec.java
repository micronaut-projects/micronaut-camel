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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Writes the message of an exchange as JSON with the Micronaut {@link JsonMapper}, and reads it back,
 * for the repositories that persist exchanges, such as aggregation repositories. It replaces Java
 * serialization, which needs every stored type to be {@link java.io.Serializable} and, in a native image,
 * registered for serialization.
 *
 * <p>The JSON keeps the type name of every value, so that the body and the headers are read back with
 * their types. A value can be:</p>
 * <ul>
 *     <li>a JDK value type: strings, numbers, booleans, characters, {@code byte[]}, {@link UUID},
 *     {@link Date} and the {@code java.time} types</li>
 *     <li>a type introspected at compile time, such as a {@code @Serdeable} type with Micronaut
 *     Serialization</li>
 *     <li>a list, set or map with string keys of such values</li>
 * </ul>
 *
 * <p>Other types are never instantiated from the stored type names. The codec stores the exchange ID, the
 * body, the headers and the aggregation properties, like Camel's own codecs, and the list that the list
 * aggregation strategies of Camel, such as {@code AggregationStrategies.groupedBody()} and
 * {@code groupedExchange()}, keep in the {@code CamelGroupedExchange} property: the grouped bodies, or the
 * grouped exchanges with their ID, body and headers. Headers whose values cannot be stored are left out,
 * and a body (or grouped body) that cannot be stored fails.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = JsonMapper.class)
@Requires(beans = JsonMapper.class)
public class JsonExchangeCodec {

    private static final Logger LOG = LoggerFactory.getLogger(JsonExchangeCodec.class);

    private static final String EXCHANGE_ID = "exchangeId";
    private static final String BODY = "body";
    private static final String HEADERS = "headers";
    private static final String PROPERTIES = "properties";
    private static final String FROM_ENDPOINT = "fromEndpoint";
    private static final String TYPE = "type";
    private static final String VALUE = "value";
    private static final String ITEMS = "items";
    private static final String ENTRIES = "entries";
    private static final String EXCHANGE = "exchange";
    private static final String EXCHANGE_TYPE = Exchange.class.getName();
    // the list of the grouped bodies or exchanges of AggregationStrategies.groupedBody() and groupedExchange()
    private static final String GROUPED_EXCHANGE = ExchangePropertyKey.GROUPED_EXCHANGE.getName();

    private static final List<String> AGGREGATION_PROPERTIES = List.of(
        Exchange.AGGREGATED_SIZE,
        Exchange.AGGREGATED_TIMEOUT,
        Exchange.AGGREGATED_COMPLETED_BY,
        Exchange.AGGREGATED_CORRELATION_KEY,
        Exchange.AGGREGATED_COLLECTION_GUARD
    );

    private static final Map<String, Class<?>> VALUE_TYPES = valueTypes(
        String.class, Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class,
        Float.class, Double.class, BigInteger.class, BigDecimal.class, byte[].class, UUID.class, Date.class,
        Instant.class, LocalDate.class, LocalTime.class, LocalDateTime.class, OffsetDateTime.class,
        ZonedDateTime.class, Duration.class
    );

    private static final Map<String, Supplier<Collection<Object>>> COLLECTIONS = Map.of(
        ArrayList.class.getName(), ArrayList::new,
        LinkedList.class.getName(), LinkedList::new,
        LinkedHashSet.class.getName(), LinkedHashSet::new,
        TreeSet.class.getName(), TreeSet::new
    );

    private static final Map<String, Supplier<Map<String, Object>>> MAPS = Map.of(
        LinkedHashMap.class.getName(), LinkedHashMap::new,
        TreeMap.class.getName(), TreeMap::new
    );

    private final JsonMapper jsonMapper;
    private final Map<String, Optional<Class<?>>> introspectedTypes = new ConcurrentHashMap<>();

    /**
     * @param jsonMapper The JSON mapper
     */
    public JsonExchangeCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Writes an exchange.
     *
     * @param exchange   The exchange
     * @param allHeaders Whether to store the headers of any supported type, or only those of JDK value types
     * @return The JSON
     * @throws IOException if the body cannot be stored
     */
    public byte[] encode(Exchange exchange, boolean allHeaders) throws IOException {
        return jsonMapper.writeValueAsBytes(Argument.of(JsonNode.class), toTree(exchange, allHeaders));
    }

    /**
     * Writes an exchange.
     *
     * @param exchange   The exchange
     * @param allHeaders Whether to store the headers of any supported type, or only those of JDK value types
     * @param out        The stream to write to
     * @throws IOException if the body cannot be stored
     */
    public void encode(Exchange exchange, boolean allHeaders, OutputStream out) throws IOException {
        jsonMapper.writeValue(out, Argument.of(JsonNode.class), toTree(exchange, allHeaders));
    }

    /**
     * Reads an exchange written by this codec.
     *
     * @param camelContext The Camel context of the new exchange
     * @param data         The JSON
     * @return The exchange
     * @throws IOException if the JSON cannot be read
     */
    public Exchange decode(CamelContext camelContext, byte[] data) throws IOException {
        return fromTree(camelContext, jsonMapper.readValue(data, Argument.of(JsonNode.class)));
    }

    /**
     * Reads an exchange written by this codec.
     *
     * @param camelContext The Camel context of the new exchange
     * @param in           The JSON
     * @return The exchange
     * @throws IOException if the JSON cannot be read
     */
    public Exchange decode(CamelContext camelContext, InputStream in) throws IOException {
        return fromTree(camelContext, jsonMapper.readValue(in, Argument.of(JsonNode.class)));
    }

    /**
     * Resolves a type name of the stored JSON to a type that may be read. By default, the JDK value types
     * and the introspected types are allowed.
     *
     * @param name The type name
     * @return The type, empty if values of the type may not be read
     */
    protected Optional<Class<?>> resolveType(String name) {
        Class<?> type = VALUE_TYPES.get(name);
        if (type != null) {
            return Optional.of(type);
        }
        return introspectedTypes.computeIfAbsent(name, n -> BeanIntrospector.SHARED
            .findIntrospectedTypes(reference -> reference.isPresent() && reference.getName().equals(n))
            .stream()
            .findFirst());
    }

    /**
     * @param type A type
     * @return Whether values of the type may be stored with all the headers or as the body
     */
    protected boolean isSupported(Class<?> type) {
        return VALUE_TYPES.containsKey(type.getName()) || BeanIntrospector.SHARED.findIntrospection(type).isPresent();
    }

    private JsonNode toTree(Exchange exchange, boolean allHeaders) throws IOException {
        Map<String, JsonNode> root = writeMessage(exchange, allHeaders);
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String property : AGGREGATION_PROPERTIES) {
            Object value = exchange.getProperty(property);
            if (value != null) {
                properties.put(property, value);
            }
        }
        JsonNode propertiesNode = writeEntries(properties, false, "property");
        Object grouped = exchange.getProperty(GROUPED_EXCHANGE);
        if (grouped != null) {
            Map<String, JsonNode> entries = new LinkedHashMap<>();
            propertiesNode.entries().forEach(entry -> entries.put(entry.getKey(), entry.getValue()));
            entries.put(GROUPED_EXCHANGE, writeGrouped(grouped, allHeaders));
            propertiesNode = JsonNode.createObjectNode(entries);
        }
        if (propertiesNode.size() > 0) {
            root.put(PROPERTIES, propertiesNode);
        }
        Endpoint from = exchange.getFromEndpoint();
        if (from != null) {
            root.put(FROM_ENDPOINT, JsonNode.createStringNode(from.getEndpointUri()));
        }
        return JsonNode.createObjectNode(root);
    }

    /**
     * Writes the exchange ID, the body and the headers of an exchange.
     */
    private Map<String, JsonNode> writeMessage(Exchange exchange, boolean allHeaders) throws IOException {
        Map<String, JsonNode> root = new LinkedHashMap<>();
        root.put(EXCHANGE_ID, JsonNode.createStringNode(exchange.getExchangeId()));
        Message message = exchange.getMessage();
        Object body = message.getBody();
        JsonNode bodyNode = write(body, true);
        if (bodyNode == null) {
            throw new IOException("The body of type " + body.getClass().getName() + " cannot be stored as JSON: "
                + "use a JDK value type, a collection or a type introspected for serialization, such as a @Serdeable type");
        }
        root.put(BODY, bodyNode);
        if (message.hasHeaders()) {
            root.put(HEADERS, writeEntries(message.getHeaders(), allHeaders, "header"));
        }
        return root;
    }

    /**
     * Writes the list of the grouped bodies (stored like a body) or exchanges (stored with their ID, body and
     * headers) of Camel's list aggregation strategies, which fails like a body that cannot be stored.
     */
    private JsonNode writeGrouped(Object grouped, boolean allHeaders) throws IOException {
        if (!(grouped instanceof List<?> list)) {
            throw new IOException("The grouped exchanges of type " + grouped.getClass().getName() + " cannot be stored as JSON");
        }
        List<JsonNode> items = new ArrayList<>(list.size());
        for (Object item : list) {
            JsonNode node;
            if (item instanceof Exchange exchange) {
                node = JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(EXCHANGE_TYPE),
                    EXCHANGE, JsonNode.createObjectNode(writeMessage(exchange, allHeaders))));
            } else {
                node = write(item, true);
                if (node == null) {
                    throw new IOException("The grouped body of type " + item.getClass().getName() + " cannot be stored as JSON: "
                        + "use a JDK value type, a collection or a type introspected for serialization, such as a @Serdeable type");
                }
            }
            items.add(node);
        }
        return JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(ArrayList.class.getName()), ITEMS, JsonNode.createArrayNode(items)));
    }

    private JsonNode writeEntries(Map<String, Object> values, boolean allTypes, String kind) {
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            JsonNode node;
            try {
                node = write(entry.getValue(), allTypes);
            } catch (IOException | RuntimeException e) {
                node = null;
            }
            if (node != null) {
                entries.put(entry.getKey(), node);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Not storing the {} {} of type {}", kind, entry.getKey(), entry.getValue().getClass().getName());
            }
        }
        return JsonNode.createObjectNode(entries);
    }

    @SuppressWarnings("unchecked")
    private @Nullable JsonNode write(@Nullable Object value, boolean allTypes) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, JsonNode> entries = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                JsonNode node = entry.getKey() instanceof String key ? write(entry.getValue(), allTypes) : null;
                if (node == null) {
                    return null;
                }
                entries.put((String) entry.getKey(), node);
            }
            String type = value instanceof SortedMap<?, ?> ? TreeMap.class.getName() : LinkedHashMap.class.getName();
            return JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(type), ENTRIES, JsonNode.createObjectNode(entries)));
        }
        if (value instanceof Collection<?> collection) {
            List<JsonNode> items = new ArrayList<>(collection.size());
            for (Object item : collection) {
                JsonNode node = write(item, allTypes);
                if (node == null) {
                    return null;
                }
                items.add(node);
            }
            return JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(collectionType(collection)), ITEMS, JsonNode.createArrayNode(items)));
        }
        Class<Object> type = (Class<Object>) value.getClass();
        if (value instanceof BigDecimal || value instanceof BigInteger) {
            // as a string, which keeps the scale
            return JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(type.getName()), VALUE, JsonNode.createStringNode(value.toString())));
        }
        if (VALUE_TYPES.containsKey(type.getName()) || (allTypes && isSupported(type))) {
            JsonNode node = jsonMapper.writeValueToTree(Argument.of(type), value);
            return JsonNode.createObjectNode(Map.of(TYPE, JsonNode.createStringNode(type.getName()), VALUE, node));
        }
        return null;
    }

    private static String collectionType(Collection<?> collection) {
        if (collection instanceof SortedSet<?>) {
            return TreeSet.class.getName();
        } else if (collection instanceof Set<?>) {
            return LinkedHashSet.class.getName();
        } else if (collection instanceof LinkedList<?>) {
            return LinkedList.class.getName();
        }
        return ArrayList.class.getName();
    }

    private Exchange fromTree(CamelContext camelContext, JsonNode root) throws IOException {
        if (!root.isObject()) {
            throw new IOException("Not an exchange written by " + JsonExchangeCodec.class.getSimpleName());
        }
        Exchange exchange = readMessage(camelContext, root);
        JsonNode properties = root.get(PROPERTIES);
        if (properties != null) {
            for (Map.Entry<String, JsonNode> entry : properties.entries()) {
                exchange.setProperty(entry.getKey(), read(camelContext, entry.getValue()));
            }
        }
        JsonNode from = root.get(FROM_ENDPOINT);
        if (from != null && from.isString()) {
            Endpoint endpoint = camelContext.hasEndpoint(from.getStringValue());
            if (endpoint != null) {
                exchange.getExchangeExtension().setFromEndpoint(endpoint);
            }
        }
        return exchange;
    }

    private Exchange readMessage(CamelContext camelContext, JsonNode root) throws IOException {
        Exchange exchange = new DefaultExchange(camelContext);
        JsonNode exchangeId = root.get(EXCHANGE_ID);
        if (exchangeId != null && exchangeId.isString()) {
            exchange.setExchangeId(exchangeId.getStringValue());
        }
        Message message = exchange.getMessage();
        message.setBody(read(camelContext, root.get(BODY)));
        JsonNode headers = root.get(HEADERS);
        if (headers != null) {
            for (Map.Entry<String, JsonNode> entry : headers.entries()) {
                message.setHeader(entry.getKey(), read(camelContext, entry.getValue()));
            }
        }
        return exchange;
    }

    private @Nullable Object read(CamelContext camelContext, @Nullable JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode typeNode = node.get(TYPE);
        if (typeNode == null || !typeNode.isString()) {
            throw new IOException("A stored value has no type");
        }
        String typeName = typeNode.getStringValue();
        JsonNode exchange = node.get(EXCHANGE);
        if (exchange != null && EXCHANGE_TYPE.equals(typeName) && exchange.isObject()) {
            return readMessage(camelContext, exchange);
        }
        JsonNode items = node.get(ITEMS);
        if (items != null) {
            Supplier<Collection<Object>> factory = COLLECTIONS.get(typeName);
            if (factory == null) {
                throw new IOException("Unsupported collection type: " + typeName);
            }
            Collection<Object> collection = factory.get();
            for (JsonNode item : items.values()) {
                collection.add(read(camelContext, item));
            }
            return collection;
        }
        JsonNode entries = node.get(ENTRIES);
        if (entries != null) {
            Supplier<Map<String, Object>> factory = MAPS.get(typeName);
            if (factory == null) {
                throw new IOException("Unsupported map type: " + typeName);
            }
            Map<String, Object> map = factory.get();
            for (Map.Entry<String, JsonNode> entry : entries.entries()) {
                map.put(entry.getKey(), read(camelContext, entry.getValue()));
            }
            return map;
        }
        Class<?> type = resolveType(typeName)
            .orElseThrow(() -> new IOException("The stored type " + typeName + " is not allowed: only JDK value types and introspected types are read"));
        JsonNode value = node.get(VALUE);
        if (value == null || value.isNull()) {
            return null;
        } else if (type == BigDecimal.class && value.isString()) {
            return new BigDecimal(value.getStringValue());
        } else if (type == BigInteger.class && value.isString()) {
            return new BigInteger(value.getStringValue());
        }
        return jsonMapper.readValueFromTree(value, Argument.of(type));
    }

    private static Map<String, Class<?>> valueTypes(Class<?>... types) {
        Map<String, Class<?>> map = new HashMap<>();
        for (Class<?> type : types) {
            map.put(type.getName(), type);
        }
        return Map.copyOf(map);
    }
}
