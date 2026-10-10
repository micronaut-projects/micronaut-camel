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
package io.micronaut.camel.convert;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * Loads the type converter loader beans, such as those generated for {@code @Converter} classes,
 * and the converter beans marked with {@link CamelConverters}, into Camel's type converter registry
 * once Camel is configured.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
final class TypeConverterLoaders extends MainListenerSupport {

    private final BeanContext beanContext;

    /**
     * @param beanContext The bean context
     */
    TypeConverterLoaders(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
        TypeConverterRegistry registry = main.getCamelContext().getTypeConverterRegistry();
        for (TypeConverterLoader loader : beanContext.getBeansOfType(TypeConverterLoader.class)) {
            loader.load(registry);
        }
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(CamelConverters.class))) {
            registry.addTypeConverters(beanContext.getBean(definition));
        }
    }
}
