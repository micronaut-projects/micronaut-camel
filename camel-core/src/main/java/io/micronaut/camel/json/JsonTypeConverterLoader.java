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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * Adds the {@link JsonTypeConverter} to Camel's fallback type converters.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = JsonMapper.class)
@Requires(beans = JsonMapper.class)
@Requires(property = JsonTypeConverterLoader.ENABLED, notEquals = StringUtils.FALSE)
final class JsonTypeConverterLoader implements TypeConverterLoader {

    /**
     * Whether the JSON type converter is enabled.
     */
    static final String ENABLED = "micronaut.camel.json.type-converter.enabled";

    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper The JSON mapper
     */
    JsonTypeConverterLoader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
        registry.addFallbackTypeConverter(new JsonTypeConverter(jsonMapper), false);
    }
}
