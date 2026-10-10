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
package io.micronaut.camel.leveldb;

import io.micronaut.camel.json.JsonExchangeCodec;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.leveldb.LevelDBAggregationRepository;
import org.apache.camel.component.leveldb.LevelDBSerializer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A serializer of camel-leveldb's {@link LevelDBAggregationRepository} that stores the exchanges as JSON
 * with the Micronaut JSON mapper, instead of Java serialization or Jackson. Set it with
 * {@link LevelDBAggregationRepository#setSerializer(LevelDBSerializer)}.
 *
 * <p>The bodies and headers can be JDK value types, collections and types introspected for serialization,
 * such as {@code @Serdeable} types: see {@link JsonExchangeCodec}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = LevelDBSerializer.class)
@Requires(beans = JsonExchangeCodec.class)
public class JsonLevelDBSerializer implements LevelDBSerializer {

    private final JsonExchangeCodec codec;

    /**
     * @param codec The exchange codec
     */
    public JsonLevelDBSerializer(JsonExchangeCodec codec) {
        this.codec = codec;
    }

    @Override
    public byte[] serializeKey(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String deserializeKey(byte[] buffer) {
        return new String(buffer, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] serializeExchange(CamelContext camelContext, Exchange exchange, boolean allowSerializedHeaders) throws IOException {
        return codec.encode(exchange, allowSerializedHeaders);
    }

    @Override
    public Exchange deserializeExchange(CamelContext camelContext, byte[] buffer) throws IOException {
        return codec.decode(camelContext, buffer);
    }

    @Override
    public Exchange deserializeExchange(CamelContext camelContext, byte[] buffer, @Nullable String deserializationFilter) throws IOException {
        return deserializeExchange(camelContext, buffer);
    }
}
