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

import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ehcache.EhcacheComponent;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.ehcache.CacheManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
@Property(name = "spec.name", value = "EhcacheBridgeTest")
@Property(name = "ehcache.caches.orders.key-type", value = "java.lang.String")
@Property(name = "ehcache.caches.orders.value-type", value = "java.lang.String")
class EhcacheBridgeTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    CacheManager cacheManager;

    @Inject
    @Named("orders")
    SyncCache<?> orders;

    @Test
    void routesShareTheMicronautCaches() {
        assertSame(cacheManager, camelContext.getComponent("ehcache", EhcacheComponent.class).getCacheManager());

        producerTemplate.sendBodyAndHeaders("direct:put", "pending", Map.of(
            EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT,
            EhcacheConstants.KEY, "order-1"));

        // a route writes what @Cacheable methods read, and the other way around
        assertEquals("pending", orders.get("order-1", String.class).orElseThrow());
        orders.put("order-2", "shipped");
        assertEquals("shipped", producerTemplate.requestBodyAndHeaders("direct:put", null, Map.of(
            EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET,
            EhcacheConstants.KEY, "order-2"), String.class));
    }

    @Singleton
    @Requires(property = "spec.name", value = "EhcacheBridgeTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:put").to("ehcache:orders");
        }
    }
}
