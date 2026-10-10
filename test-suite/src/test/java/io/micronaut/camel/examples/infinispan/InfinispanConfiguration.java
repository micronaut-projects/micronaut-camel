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
package io.micronaut.camel.examples.infinispan;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;

/**
 * Creates the cache of the example once micronaut-cache-infinispan has created the {@link RemoteCacheManager},
 * from {@code infinispan.client.hotrod.*}. Camel autowires the manager into the {@code infinispan} component.
 */
@Singleton
@Requires(env = "example-infinispan")
public class InfinispanConfiguration implements BeanCreatedEventListener<RemoteCacheManager> {

    public static final String CACHE_NAME = "default";

    /**
     * An Infinispan server only provisions the "default" cache-container, not a cache inside it, so the
     * cache must be created on first connect.
     */
    @Override
    public RemoteCacheManager onCreated(BeanCreatedEvent<RemoteCacheManager> event) {
        RemoteCacheManager cacheManager = event.getBean();
        cacheManager.administration().getOrCreateCache(
                CACHE_NAME,
                new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.DIST_SYNC).build());
        return cacheManager;
    }
}
