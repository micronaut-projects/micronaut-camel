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
import org.apache.camel.CamelContext;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Camel's type converter, which finds the type converter loaders in a {@link CamelServiceIndex}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class IndexedTypeConverter extends DefaultTypeConverter {

    private final Supplier<@Nullable CamelServiceIndex> index;

    IndexedTypeConverter(Supplier<@Nullable CamelServiceIndex> index, CamelContext camelContext, PackageScanClassResolver resolver,
                         Injector injector, boolean loadTypeConverters, boolean statisticsEnabled) {
        super(camelContext, resolver, injector, loadTypeConverters, statisticsEnabled);
        this.index = index;
    }

    @Override
    protected void findTypeConverterLoaderClasses(Collection<String> loaders, String basePath) throws IOException {
        Optional<List<String>> indexed = indexed(basePath);
        if (indexed.isPresent()) {
            loaders.addAll(indexed.get());
        } else {
            super.findTypeConverterLoaderClasses(loaders, basePath);
        }
    }

    @Override
    protected Collection<String> findFallbackTypeConverterClasses() throws IOException {
        Optional<List<String>> indexed = indexed(META_INF_SERVICES_FALLBACK_TYPE_CONVERTER);
        return indexed.<Collection<String>>map(LinkedHashSet::new).orElse(super.findFallbackTypeConverterClasses());
    }

    private Optional<List<String>> indexed(String path) {
        CamelServiceIndex serviceIndex = index.get();
        return serviceIndex != null ? serviceIndex.list(path) : Optional.empty();
    }
}
