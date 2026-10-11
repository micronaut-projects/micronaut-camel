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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import org.apache.camel.Ordered;
import org.apache.camel.component.properties.LocationPropertiesSource;
import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.util.OrderedLocationProperties;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Camel {@link LoadablePropertiesSource} backed by the Micronaut {@link Environment}.
 *
 * <p>Camel resolves {@code {{name}}} placeholders through it, and camel-main reads every
 * {@code camel.*} option from it, so {@code camel.main.*}, {@code camel.component.*} and the other
 * Camel configuration groups can be set in any Micronaut property source.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class MicronautPropertiesSource implements LoadablePropertiesSource, LocationPropertiesSource, Ordered {

    /**
     * The location reported to Camel for properties that come from Micronaut.
     */
    public static final String LOCATION = "micronaut";

    private static final PropertiesLocation PROPERTIES_LOCATION = new PropertiesLocation(LOCATION);

    private final Environment environment;

    /**
     * @param environment The environment
     */
    public MicronautPropertiesSource(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getName() {
        return "MicronautPropertiesSource";
    }

    @Override
    public PropertiesLocation getLocation() {
        return PROPERTIES_LOCATION;
    }

    @Override
    public @Nullable String getProperty(String name) {
        String value = lookup(name);
        if (value == null) {
            String hyphenated = NameUtils.hyphenate(name);
            if (!hyphenated.equals(name)) {
                value = lookup(hyphenated);
            }
        }
        return value;
    }

    @Override
    public Properties loadProperties() {
        return loadProperties(name -> true);
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        OrderedLocationProperties answer = new OrderedLocationProperties();
        Map<String, Object> all;
        if (environment instanceof PropertySourcePropertyResolver resolver) {
            all = resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT);
        } else {
            all = prefixed("camel", environment.getProperties("camel", StringConvention.RAW));
        }
        all.forEach((key, value) -> {
            if (value != null && filter.test(key)) {
                answer.put(LOCATION, key, asString(value));
            }
        });
        return answer;
    }

    @Override
    public void reloadProperties(String location) {
        // Micronaut owns the property sources; a refresh of the environment is visible on the next lookup
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST;
    }

    private @Nullable String lookup(String name) {
        return environment.getProperty(name, Object.class).map(MicronautPropertiesSource::asString).orElse(null);
    }

    private static Map<String, Object> prefixed(String prefix, Map<String, Object> properties) {
        return properties.entrySet().stream()
            .collect(Collectors.toMap(e -> prefix + '.' + e.getKey(), Map.Entry::getValue));
    }

    private static String asString(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return value.toString();
    }
}
