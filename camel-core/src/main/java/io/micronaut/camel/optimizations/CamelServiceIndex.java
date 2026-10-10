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
package io.micronaut.camel.optimizations;

import io.micronaut.core.optim.StaticOptimizations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An index of Camel's service files under {@code META-INF/services/org/apache/camel}, computed when the
 * application is built by the {@code camel.service.index} optimization of Micronaut AOT
 * (micronaut-camel-aot), and loaded as a Micronaut static optimization.
 *
 * <p>With the index, Camel finds its components, data formats, languages, configurers and type converter
 * loaders without looking up the service files on the classpath. Camel still looks up the service files
 * that are not in the index, such as those of classes loaded at runtime.</p>
 *
 * @param classes The class names of the service files that name a class, by path relative to
 *                {@value #ROOT}
 * @param lists   The class names that the list files, such as {@code TypeConverterLoader}, list, by path
 *                relative to {@value #ROOT}
 * @author Graeme Rocher
 * @since 1.0.0
 */
public record CamelServiceIndex(Map<String, String> classes, Map<String, List<String>> lists) {

    /**
     * The root of Camel's service files.
     */
    public static final String ROOT = "META-INF/services/org/apache/camel/";

    /**
     * @param classes The class names of the service files that name a class, by relative path
     * @param lists   The class names of the list files, by relative path
     */
    public CamelServiceIndex {
        classes = Map.copyOf(classes);
        lists = Map.copyOf(lists);
    }

    /**
     * @return The index of the application, when Micronaut AOT computed one
     */
    public static Optional<CamelServiceIndex> find() {
        return StaticOptimizations.get(CamelServiceIndex.class);
    }

    /**
     * @param path The path of a service file
     * @return The class it names, if the index has the file
     */
    public Optional<String> className(String path) {
        return Optional.ofNullable(classes.get(relative(path)));
    }

    /**
     * @param path The path of a list file
     * @return The class names it lists, if the index has the file
     */
    public Optional<List<String>> list(String path) {
        return Optional.ofNullable(lists.get(relative(path)));
    }

    private static String relative(String path) {
        return path.startsWith(ROOT) ? path.substring(ROOT.length()) : path;
    }
}
