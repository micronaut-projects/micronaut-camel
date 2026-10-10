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
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A Camel {@link IdempotentRepository}, for the idempotent consumer EIP or the {@code file}
 * consumer, that stores the keys in a Micronaut cache: Caffeine, Redis, Hazelcast, Infinispan and
 * the other micronaut-cache implementations.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautCacheIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private final SyncCache<?> cache;

    /**
     * @param cache The cache that stores the keys
     */
    public MicronautCacheIdempotentRepository(SyncCache<?> cache) {
        this.cache = cache;
    }

    @Override
    public boolean add(String key) {
        return cache.putIfAbsent(key, Boolean.TRUE).isEmpty();
    }

    @Override
    public boolean contains(String key) {
        return cache.get(key, Boolean.class).isPresent();
    }

    @Override
    public boolean remove(String key) {
        boolean present = contains(key);
        cache.invalidate(key);
        return present;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    @ManagedOperation(description = "Clears the cache")
    public void clear() {
        cache.invalidateAll();
    }
}
