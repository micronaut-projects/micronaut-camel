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
package io.micronaut.camel;

import io.micronaut.camel.properties.MicronautPropertiesSource;
import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.camel.registry.MicronautBeanRepository;
import io.micronaut.camel.registry.MicronautRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Creates the {@link CamelContext} and the Camel templates as beans.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@Internal
final class CamelContextFactory {

    /**
     * Creates camel-main for a new {@link CamelContext} whose registry is the Micronaut bean context
     * and whose properties come from the Micronaut environment.
     *
     * @param beanContext       The bean context
     * @param environment       The environment
     * @param conversionService The conversion service
     * @return The camel-main bootstrap
     */
    @Singleton
    @CamelInternal
    @Bean(preDestroy = "shutdownCamel")
    MicronautCamelMain camelMain(BeanContext beanContext, Environment environment, ConversionService conversionService) {
        DefaultCamelContext camelContext = new DefaultCamelContext(false);
        // camel-main sets the name once the context is built, too late for the names of Camel's threads
        environment.getProperty("camel.main.name", String.class).ifPresent(camelContext.getCamelContextExtension()::setName);
        MicronautRegistry registry = new MicronautRegistry(new MicronautBeanRepository(beanContext));
        registry.setCamelContext(camelContext);
        camelContext.getCamelContextExtension().setRegistry(registry);
        camelContext.getPropertiesComponent().addPropertiesSource(new MicronautPropertiesSource(environment));
        camelContext.getTypeConverterRegistry().addFallbackTypeConverter(new ConversionServiceTypeConverter(conversionService), false);
        return new MicronautCamelMain(camelContext);
    }

    /**
     * The Camel context, configured from the {@code camel.*} properties. Routes are added when the
     * application starts.
     *
     * @param main The camel-main bootstrap
     * @return The Camel context
     */
    @Singleton
    CamelContext camelContext(MicronautCamelMain main) {
        main.init();
        return main.getCamelContext();
    }

    /**
     * @param camelContext The Camel context
     * @return A producer template
     */
    @Singleton
    @Bean(preDestroy = "stop")
    ProducerTemplate producerTemplate(CamelContext camelContext) {
        return camelContext.createProducerTemplate();
    }

    /**
     * @param camelContext The Camel context
     * @return A fluent producer template
     */
    @Singleton
    @Bean(preDestroy = "stop")
    FluentProducerTemplate fluentProducerTemplate(CamelContext camelContext) {
        return camelContext.createFluentProducerTemplate();
    }

    /**
     * @param camelContext The Camel context
     * @return A consumer template
     */
    @Singleton
    @Bean(preDestroy = "stop")
    ConsumerTemplate consumerTemplate(CamelContext camelContext) {
        return camelContext.createConsumerTemplate();
    }

    /**
     * @param camelContext The Camel context
     * @return Camel's type converter
     */
    @Singleton
    TypeConverter typeConverter(CamelContext camelContext) {
        return camelContext.getTypeConverter();
    }
}
