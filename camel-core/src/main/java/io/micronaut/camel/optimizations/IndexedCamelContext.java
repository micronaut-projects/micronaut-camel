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
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.support.PluginHelper;
import org.jspecify.annotations.Nullable;

/**
 * A Camel context that finds its extensions and type converter loaders in a {@link CamelServiceIndex}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class IndexedCamelContext extends DefaultCamelContext {

    private final CamelServiceIndex index;

    /**
     * Creates a context that is not initialized.
     *
     * @param index The index of Camel's service files
     */
    public IndexedCamelContext(CamelServiceIndex index) {
        super(false);
        this.index = index;
    }

    /**
     * @return The index of Camel's service files
     */
    public @Nullable CamelServiceIndex getIndex() {
        return index;
    }

    @Override
    protected FactoryFinderResolver createFactoryFinderResolver() {
        // called while the super constructor runs, before the index is set
        return new IndexedFactoryFinderResolver(this::getIndex);
    }

    @Override
    protected TypeConverter createTypeConverter() {
        return new IndexedTypeConverter(this::getIndex, getCamelContextReference(), PluginHelper.getPackageScanClassResolver(this),
            getInjector(), isLoadTypeConverters(), isTypeConverterStatisticsEnabled());
    }
}
