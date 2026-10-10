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
package io.micronaut.camel.cache;

import io.micronaut.cache.SyncCache;
import io.micronaut.camel.json.JsonExchangeCodec;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * An aggregation repository that stores the exchanges being aggregated in a Micronaut cache, as JSON
 * written by a {@link JsonExchangeCodec}: Caffeine, Redis, Hazelcast, Infinispan or any other
 * Micronaut Cache implementation.
 *
 * <p>The bodies and headers can be JDK value types, collections and types introspected for serialization,
 * such as {@code @Serdeable} types. With {@link #setUseRecovery(boolean) recovery}, which is enabled by
 * default, completed exchanges are kept in the cache until the aggregator confirms them, so that they are
 * sent again when the route fails.</p>
 *
 * <p>The repository keeps the list of its keys in the cache too, under {@code <name>:keys}, and updates
 * it from one application instance at a time only: use a repository name per instance with a shared
 * cache, and make sure the cache does not evict the entries of the repository.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautCacheAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {

    private static final String DEFAULT_NAME = "aggregation";

    private final String name;
    private final SyncCache<?> cache;
    private final JsonExchangeCodec codec;
    private boolean useRecovery = true;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries;
    private @Nullable String deadLetterUri;
    private boolean allowSerializedHeaders;
    private boolean returnOldExchange;

    /**
     * @param cache The cache that stores the exchanges
     * @param codec The codec of the exchanges
     */
    public MicronautCacheAggregationRepository(SyncCache<?> cache, JsonExchangeCodec codec) {
        this(DEFAULT_NAME, cache, codec);
    }

    /**
     * @param name  The name of the repository, which prefixes its keys in the cache
     * @param cache The cache that stores the exchanges
     * @param codec The codec of the exchanges
     */
    public MicronautCacheAggregationRepository(String name, SyncCache<?> cache, JsonExchangeCodec codec) {
        this.name = name;
        this.cache = cache;
        this.codec = codec;
    }

    /**
     * @return The name of the repository
     */
    public String getName() {
        return name;
    }

    @Override
    public @Nullable Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        Exchange old = returnOldExchange ? get(camelContext, key) : null;
        cache.put(aggregateKey(key), encode(exchange));
        updateIndex(keysKey(), key, true);
        return old;
    }

    @Override
    public @Nullable Exchange get(CamelContext camelContext, String key) {
        return read(camelContext, aggregateKey(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        if (useRecovery) {
            cache.put(completedKey(exchange.getExchangeId()), encode(exchange));
            updateIndex(completedKeysKey(), exchange.getExchangeId(), true);
        }
        cache.invalidate(aggregateKey(key));
        updateIndex(keysKey(), key, false);
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        confirmWithResult(camelContext, exchangeId);
    }

    @Override
    public boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
        boolean present = cache.get(completedKey(exchangeId), String.class).isPresent();
        cache.invalidate(completedKey(exchangeId));
        updateIndex(completedKeysKey(), exchangeId, false);
        return present;
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(readIndex(keysKey()));
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        return Collections.unmodifiableSet(readIndex(completedKeysKey()));
    }

    @Override
    public @Nullable Exchange recover(CamelContext camelContext, String exchangeId) {
        return read(camelContext, completedKey(exchangeId));
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    @Override
    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setDeadLetterUri(@Nullable String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    @Override
    public @Nullable String getDeadLetterUri() {
        return deadLetterUri;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    /**
     * @return Whether the headers of introspected types are stored, besides those of JDK value types
     */
    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    /**
     * @param allowSerializedHeaders Whether to store the headers of introspected types, besides those of JDK value types
     */
    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    /**
     * @return Whether {@link #add(CamelContext, String, Exchange)} reads and returns the exchange it replaces
     */
    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    /**
     * @param returnOldExchange Whether {@link #add(CamelContext, String, Exchange)} reads and returns the exchange it replaces
     */
    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    private String encode(Exchange exchange) {
        try {
            return new String(codec.encode(exchange, allowSerializedHeaders), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot store the exchange " + exchange.getExchangeId() + " in the repository " + name, e);
        }
    }

    private @Nullable Exchange read(CamelContext camelContext, String cacheKey) {
        String json = cache.get(cacheKey, String.class).orElse(null);
        if (json == null) {
            return null;
        }
        try {
            return codec.decode(camelContext, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot read the exchange " + cacheKey + " of the repository " + name, e);
        }
    }

    private synchronized void updateIndex(String indexKey, String key, boolean add) {
        Set<String> keys = readIndex(indexKey);
        boolean changed = add ? keys.add(key) : keys.remove(key);
        if (changed) {
            if (keys.isEmpty()) {
                cache.invalidate(indexKey);
            } else {
                cache.put(indexKey, writeIndex(keys));
            }
        }
    }

    private Set<String> readIndex(String indexKey) {
        Set<String> keys = new LinkedHashSet<>();
        String index = cache.get(indexKey, String.class).orElse("");
        StringBuilder key = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < index.length(); i++) {
            char c = index.charAt(i);
            if (escaped) {
                key.append(c == 'n' ? '\n' : c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\n') {
                keys.add(key.toString());
                key.setLength(0);
            } else {
                key.append(c);
            }
        }
        return keys;
    }

    private static String writeIndex(Set<String> keys) {
        StringBuilder index = new StringBuilder();
        for (String key : keys) {
            index.append(key.replace("\\", "\\\\").replace("\n", "\\n")).append('\n');
        }
        return index.toString();
    }

    private String aggregateKey(String key) {
        return name + ":aggregate:" + key;
    }

    private String completedKey(String exchangeId) {
        return name + ":completed:" + exchangeId;
    }

    private String keysKey() {
        return name + ":keys";
    }

    private String completedKeysKey() {
        return name + ":completed-keys";
    }
}
