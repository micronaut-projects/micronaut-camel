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
package io.micronaut.camel.ehcache;

import io.micronaut.cache.ehcache.EhcacheSyncCache;
import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.camel.requires.RequiresCamelEhcache;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.ehcache.EhcacheComponent;
import org.apache.camel.spi.ComponentCustomizer;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Sets the {@code cacheManager} of the Camel Ehcache component to the {@link CacheManager} of
 * Micronaut Cache for Ehcache, unless the component has a cache manager, a cache manager
 * configuration or a configuration URI of its own. The caches of {@code ehcache.caches.*} are then
 * shared by the routes and the Micronaut cache annotations: their configurations are given to the
 * component, so that the routes use them with the key and value types they were created with.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelEhcache
@Requires(classes = {EhcacheComponent.class, EhcacheSyncCache.class})
@Requires(beans = CacheManager.class)
@Requires(property = EhcacheComponentCustomizer.ENABLED, notEquals = StringUtils.FALSE)
final class EhcacheComponentCustomizer implements ComponentCustomizer {

    /**
     * The property that disables the integration with Micronaut Cache for Ehcache when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.ehcache.enabled";

    private final BeanProvider<CacheManager> cacheManager;
    private final BeanProvider<EhcacheSyncCache> caches;
    private final Environment environment;

    /**
     * @param cacheManager The Ehcache cache manager of Micronaut
     * @param caches       The caches of {@code ehcache.caches.*}
     * @param environment  The environment
     */
    EhcacheComponentCustomizer(BeanProvider<CacheManager> cacheManager, BeanProvider<EhcacheSyncCache> caches,
                               Environment environment) {
        this.cacheManager = cacheManager;
        this.caches = caches;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target instanceof EhcacheComponent;
    }

    @Override
    public void configure(String name, Component target) {
        EhcacheComponent component = (EhcacheComponent) target;
        if (component.getCacheManager() != null || component.getCacheManagerConfiguration() != null
            || component.getCacheConfigurationUri() != null
            || CamelComponentOptions.isSet(environment, name, "cacheManager", "cacheManagerConfiguration", "configurationUri")) {
            return;
        }
        component.setCacheManager(cacheManager.get());
        // creating the Micronaut caches creates them in the cache manager
        Map<String, CacheConfiguration> configurations = new HashMap<>();
        Map<String, CacheConfiguration> existing = component.getCachesConfigurations();
        caches.stream()
            .filter(cache -> existing == null || !existing.containsKey(cache.getName()))
            .forEach(cache -> configurations.put(cache.getName(), cache.getNativeCache().getRuntimeConfiguration()));
        component.addCachesConfigurations(configurations);
    }
}
