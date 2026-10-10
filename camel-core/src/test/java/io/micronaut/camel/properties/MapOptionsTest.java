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
package io.micronaut.camel.properties;

import io.micronaut.context.ApplicationContext;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapOptionsTest {

    @Test
    void mapOptionsAreBoundFromNestedConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "camel.threadpool.config.big-pool.pool-size", "7",
            "camel.threadpool.config.big-pool.max-pool-size", "9",
            "camel.rest.api-properties.api.title", "Orders",
            "camel.main.global-options.greeting", "hello"))) {

            CamelContext camelContext = context.getBean(CamelContext.class);
            ThreadPoolProfile profile = camelContext.getExecutorServiceManager().getThreadPoolProfile("big-pool");
            assertNotNull(profile);
            assertEquals(7, profile.getPoolSize());
            assertEquals(9, profile.getMaxPoolSize());
            assertEquals("Orders", camelContext.getRestConfiguration().getApiProperties().get("api.title"));
            assertEquals("hello", camelContext.getGlobalOptions().get("greeting"));
        }
    }

    @Test
    void camelCaseKeysAndCamelsOwnSyntaxAreBound() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "camel.threadpool.config.smallPool.poolSize", "2",
            "camel.rest.apiProperties.api.version", "2.0",
            "camel.rest.cors-headers[Access-Control-Max-Age]", "3600",
            "camel.threadpool.config[tiny].pool-size", "1"))) {

            CamelContext camelContext = context.getBean(CamelContext.class);
            ThreadPoolProfile profile = camelContext.getExecutorServiceManager().getThreadPoolProfile("smallPool");
            assertNotNull(profile);
            assertEquals(2, profile.getPoolSize());
            assertEquals("2.0", camelContext.getRestConfiguration().getApiProperties().get("api.version"));
            assertEquals("3600", camelContext.getRestConfiguration().getCorsHeaders().get("Access-Control-Max-Age"));
            ThreadPoolProfile tiny = camelContext.getExecutorServiceManager().getThreadPoolProfile("tiny");
            assertNotNull(tiny);
            assertEquals(1, tiny.getPoolSize());
        }
    }

    @Test
    void everyMapOptionOfCamelMainIsRewritten() throws IOException {
        Set<String> mapOptions = new HashSet<>();
        try (InputStream in = CamelContext.class.getClassLoader().getResourceAsStream("META-INF/camel-main-configuration-metadata.json")) {
            assertNotNull(in, "camel-main metadata");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher option = Pattern.compile("\\{ \"name\": \"([^\"]+)\"[^}]*?\"javaType\": \"java\\.util\\.Map[\"<]").matcher(json);
            while (option.find()) {
                mapOptions.add(io.micronaut.core.naming.NameUtils.hyphenate(option.group(1)));
            }
        }
        assertTrue(mapOptions.contains("camel.threadpool.config"), mapOptions::toString);
        Set<String> rewritten = new HashSet<>(MicronautPropertiesSource.VALUE_MAP_OPTIONS);
        rewritten.addAll(MicronautPropertiesSource.OBJECT_MAP_OPTIONS);
        assertEquals(mapOptions, rewritten, "map options of camel-main");
    }
}
