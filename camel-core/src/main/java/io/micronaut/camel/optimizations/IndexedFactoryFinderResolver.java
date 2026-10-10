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

import io.micronaut.core.annotation.Internal;
import org.apache.camel.impl.engine.BootstrapFactoryFinder;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the factory finders of Camel that find the classes of the service files in a
 * {@link CamelServiceIndex}, and on the classpath otherwise.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class IndexedFactoryFinderResolver implements FactoryFinderResolver {

    private final Supplier<@Nullable CamelServiceIndex> index;

    /**
     * @param index The index, which is looked up lazily, as Camel creates factory finders while the context
     *              is constructed
     */
    IndexedFactoryFinderResolver(Supplier<@Nullable CamelServiceIndex> index) {
        this.index = index;
    }

    @Override
    public FactoryFinder resolveFactoryFinder(ClassResolver classResolver, String resourcePath) {
        return new IndexedFactoryFinder(index, classResolver, resourcePath);
    }

    @Override
    public FactoryFinder resolveBootstrapFactoryFinder(ClassResolver classResolver, String resourcePath) {
        return new IndexedBootstrapFactoryFinder(index, classResolver, resourcePath);
    }

    private static Optional<Class<?>> find(Supplier<@Nullable CamelServiceIndex> indexSupplier, ClassResolver classResolver,
                                           String path, String key, Function<String, Optional<Class<?>>> fallback) {
        CamelServiceIndex index = indexSupplier.get();
        Optional<String> className = index != null ? index.className(path + key) : Optional.empty();
        if (className.isPresent()) {
            Class<?> type = classResolver.resolveClass(className.get());
            if (type != null) {
                return Optional.of(type);
            }
        }
        return fallback.apply(key);
    }

    /**
     * A factory finder on the index.
     */
    static final class IndexedFactoryFinder extends DefaultFactoryFinder {

        private final Supplier<@Nullable CamelServiceIndex> index;

        IndexedFactoryFinder(Supplier<@Nullable CamelServiceIndex> index, ClassResolver classResolver, String resourcePath) {
            super(classResolver, resourcePath);
            this.index = index;
        }

        @Override
        public Optional<Class<?>> findClass(String key) {
            return find(index, classResolver, getResourcePath(), key, super::findClass);
        }

        @Override
        public Optional<Class<?>> findOptionalClass(String key) {
            return find(index, classResolver, getResourcePath(), key, super::findOptionalClass);
        }
    }

    /**
     * A bootstrap factory finder on the index.
     */
    static final class IndexedBootstrapFactoryFinder extends BootstrapFactoryFinder {

        private final Supplier<@Nullable CamelServiceIndex> index;

        IndexedBootstrapFactoryFinder(Supplier<@Nullable CamelServiceIndex> index, ClassResolver classResolver, String resourcePath) {
            super(classResolver, resourcePath);
            this.index = index;
        }

        @Override
        public Optional<Class<?>> findClass(String key) {
            return find(index, classResolver, getResourcePath(), key, super::findClass);
        }

        @Override
        public Optional<Class<?>> findOptionalClass(String key) {
            return find(index, classResolver, getResourcePath(), key, super::findOptionalClass);
        }
    }
}
