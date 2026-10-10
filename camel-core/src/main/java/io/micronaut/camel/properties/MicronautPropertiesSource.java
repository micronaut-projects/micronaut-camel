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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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

    /**
     * The camel-main options of type {@code Map<String, String>}. The whole key under one of these
     * options, which may contain dots, is the map key.
     */
    static final Set<String> VALUE_MAP_OPTIONS = Set.of(
        "camel.main.global-options",
        "camel.cluster.infinispan.configuration-properties",
        "camel.cluster.kubernetes.cluster-labels",
        "camel.rest.api-properties",
        "camel.rest.component-properties",
        "camel.rest.consumer-properties",
        "camel.rest.cors-headers",
        "camel.rest.data-format-properties",
        "camel.rest.endpoint-properties",
        "camel.rest.validation-levels"
    );

    /**
     * The camel-main options that map a name to a group of options, such as the thread pool profiles
     * of {@code camel.threadpool.config.<id>.*}: the first segment after the option is the map key.
     */
    static final Set<String> OBJECT_MAP_OPTIONS = Set.of("camel.threadpool.config");

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
        Map<String, Map<String, Object>> mapOptions = new LinkedHashMap<>();
        all.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String mapOption = mapOption(key);
            if (mapOption == null) {
                if (filter.test(key)) {
                    answer.put(LOCATION, key, asString(value));
                }
            } else if (value instanceof Map<?, ?> map && mapOption.equals(NameUtils.hyphenate(key, true))) {
                // Camel's option[key] syntax, which Micronaut reads as a map under the option
                Map<String, Object> entries = mapOptions.computeIfAbsent(mapOption, o -> new LinkedHashMap<>());
                map.forEach((name, entry) -> entries.put(String.valueOf(name), entry));
            }
        });
        // Micronaut flattens maps to dotted keys, while camel-main binds map entries with the
        // option[key] syntax: read each map option as a map with its keys as written
        for (String option : VALUE_MAP_OPTIONS) {
            Map<String, Object> entries = mapOptions.computeIfAbsent(option, o -> new LinkedHashMap<>());
            entries(option).forEach(entries::putIfAbsent);
            entries.forEach((key, value) -> putMapEntry(answer, filter, option + '[' + key + ']', value));
        }
        for (String option : OBJECT_MAP_OPTIONS) {
            Map<String, Object> entries = mapOptions.computeIfAbsent(option, o -> new LinkedHashMap<>());
            entries(option).forEach(entries::putIfAbsent);
            entries.forEach((key, value) -> {
                if (value instanceof Map<?, ?> group) {
                    // option[name].setting, read by Micronaut as a map of maps
                    group.forEach((setting, settingValue) ->
                        putMapEntry(answer, filter, option + '[' + key + "]." + setting, settingValue));
                } else {
                    int dot = key.indexOf('.');
                    String camelKey = dot < 0 ? option + '[' + key + ']' : option + '[' + key.substring(0, dot) + ']' + key.substring(dot);
                    putMapEntry(answer, filter, camelKey, value);
                }
            });
        }
        return answer;
    }

    /**
     * The entries of a map option, with their keys as written, whether the option is written in kebab
     * case or in camel case.
     */
    private Map<String, Object> entries(String option) {
        Map<String, Object> entries = new LinkedHashMap<>(environment.getProperties(option, StringConvention.RAW));
        String camelCase = Arrays.stream(option.split("\\."))
            .map(segment -> NameUtils.camelCase(segment, true))
            .collect(Collectors.joining("."));
        if (!camelCase.equals(option)) {
            environment.getProperties(camelCase, StringConvention.RAW).forEach(entries::putIfAbsent);
        }
        return entries;
    }

    private static void putMapEntry(OrderedLocationProperties answer, Predicate<String> filter, String key, @Nullable Object value) {
        if (value != null && filter.test(key)) {
            answer.put(LOCATION, key, asString(value));
        }
    }

    /**
     * The map option a key belongs to, if any.
     */
    private static @Nullable String mapOption(String key) {
        String normalized = NameUtils.hyphenate(key, true);
        for (Set<String> options : List.of(VALUE_MAP_OPTIONS, OBJECT_MAP_OPTIONS)) {
            for (String option : options) {
                if (normalized.equals(option) || normalized.startsWith(option + '.') || normalized.startsWith(option + '[')) {
                    return option;
                }
            }
        }
        return null;
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
