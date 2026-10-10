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
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
@Property(name = "spec.name", value = "JsonExchangeCodecTest")
class JsonExchangeCodecTest {

    @Inject
    CamelContext camelContext;

    @Inject
    JsonExchangeCodec codec;

    @Test
    void storesTheBodyAndTheHeadersWithTheirTypes() throws IOException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Order("book", 2));
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-10T10:15:30Z");
        exchange.getMessage().setHeader("id", id);
        exchange.getMessage().setHeader("count", 3);
        exchange.getMessage().setHeader("total", 3L);
        exchange.getMessage().setHeader("price", new BigDecimal("12.50"));
        exchange.getMessage().setHeader("at", now);
        exchange.getMessage().setHeader("date", new Date(now.toEpochMilli()));
        exchange.getMessage().setHeader("bytes", "abc".getBytes(StandardCharsets.UTF_8));
        exchange.getMessage().setHeader("tags", List.of("a", "b"));
        exchange.getMessage().setHeader("sorted", new TreeMap<>(Map.of("b", 2, "a", 1)));
        exchange.getMessage().setHeader("order", new Order("pen", 1));
        exchange.getMessage().setHeader("thread", Thread.currentThread());
        exchange.setProperty(Exchange.AGGREGATED_SIZE, 2);
        exchange.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, "books");
        exchange.setProperty("other", "not stored");

        Exchange restored = codec.decode(camelContext, codec.encode(exchange, true));

        assertEquals(exchange.getExchangeId(), restored.getExchangeId());
        assertEquals(new Order("book", 2), restored.getMessage().getBody());
        assertEquals(id, restored.getMessage().getHeader("id"));
        assertEquals(3, restored.getMessage().getHeader("count"));
        assertEquals(3L, restored.getMessage().getHeader("total"));
        assertEquals(new BigDecimal("12.50"), restored.getMessage().getHeader("price"));
        assertEquals(now, restored.getMessage().getHeader("at"));
        assertEquals(new Date(now.toEpochMilli()), restored.getMessage().getHeader("date"));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), restored.getMessage().getHeader("bytes", byte[].class));
        assertEquals(List.of("a", "b"), restored.getMessage().getHeader("tags"));
        assertInstanceOf(TreeMap.class, restored.getMessage().getHeader("sorted"));
        assertEquals(Map.of("a", 1, "b", 2), restored.getMessage().getHeader("sorted"));
        assertEquals(new Order("pen", 1), restored.getMessage().getHeader("order"));
        assertFalse(restored.getMessage().hasHeaders() && restored.getMessage().getHeaders().containsKey("thread"));
        assertEquals(2, restored.getProperty(Exchange.AGGREGATED_SIZE));
        assertEquals("books", restored.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));
        assertNull(restored.getProperty("other"));
    }

    @Test
    void storesOnlyTheHeadersOfJdkValueTypesByDefault() throws IOException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("hello");
        exchange.getMessage().setHeader("name", "Fred");
        exchange.getMessage().setHeader("order", new Order("pen", 1));

        Exchange restored = codec.decode(camelContext, codec.encode(exchange, false));

        assertEquals("hello", restored.getMessage().getBody());
        assertEquals("Fred", restored.getMessage().getHeader("name"));
        assertNull(restored.getMessage().getHeader("order"));
    }

    @Test
    void storesCollectionsOfIntrospectedTypes() throws IOException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new ArrayList<>(List.of(new Order("book", 1), new Order("pen", 2))));

        Exchange restored = codec.decode(camelContext, codec.encode(exchange, false));

        assertEquals(List.of(new Order("book", 1), new Order("pen", 2)), restored.getMessage().getBody());

        exchange.getMessage().setBody(new LinkedHashSet<>(List.of("a", "b")));
        Object set = codec.decode(camelContext, codec.encode(exchange, false)).getMessage().getBody();
        assertInstanceOf(Set.class, set);
        assertEquals(Set.of("a", "b"), set);
    }

    @Test
    void failsForABodyThatCannotBeStored() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Object());

        assertThrows(IOException.class, () -> codec.encode(exchange, false));
    }

    @Test
    void readsOnlyAllowedTypes() {
        byte[] json = """
            {"exchangeId":"1","body":{"type":"java.lang.ProcessBuilder","value":{}}}
            """.getBytes(StandardCharsets.UTF_8);

        IOException e = assertThrows(IOException.class, () -> codec.decode(camelContext, json));
        assertTrue(e.getMessage().contains("java.lang.ProcessBuilder"));
    }

    @Serdeable
    public record Order(String item, int quantity) {
    }
}
