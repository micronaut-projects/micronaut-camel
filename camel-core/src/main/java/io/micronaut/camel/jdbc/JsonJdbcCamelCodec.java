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
package io.micronaut.camel.jdbc;

import io.micronaut.camel.json.JsonExchangeCodec;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository;
import org.apache.camel.processor.aggregate.jdbc.JdbcCamelCodec;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A codec of camel-sql's {@link JdbcAggregationRepository} that stores the exchanges as JSON with the
 * Micronaut JSON mapper, instead of Java serialization. Set it with
 * {@link JdbcAggregationRepository#setJdbcCamelCodec(JdbcCamelCodec)}.
 *
 * <p>The bodies and headers can be JDK value types, collections and types introspected for serialization,
 * such as {@code @Serdeable} types: see {@link JsonExchangeCodec}. The repository's
 * {@code allowSerializedHeaders} option stores the headers of introspected types too, and its
 * {@code deserializationFilter} does not apply, as no Java serialization is involved.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = JdbcCamelCodec.class)
@Requires(beans = JsonExchangeCodec.class)
public class JsonJdbcCamelCodec extends JdbcCamelCodec {

    private final JsonExchangeCodec codec;

    /**
     * @param codec The exchange codec
     */
    public JsonJdbcCamelCodec(JsonExchangeCodec codec) {
        this.codec = codec;
    }

    @Override
    public void marshallExchange(Exchange exchange, boolean allowSerializedHeaders, OutputStream outputStream) throws IOException {
        codec.encode(exchange, allowSerializedHeaders, outputStream);
    }

    @Override
    public Exchange unmarshallExchange(CamelContext camelContext, InputStream inputStream, @Nullable String deserializationFilter) throws IOException {
        return codec.decode(camelContext, inputStream);
    }
}
