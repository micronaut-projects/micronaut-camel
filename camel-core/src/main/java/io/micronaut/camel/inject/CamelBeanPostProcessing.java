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
package io.micronaut.camel.inject;

import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.PluginHelper;

/**
 * Applies Camel's bean post-processor to the beans marked with {@link CamelPostProcess}, which handles
 * their Camel annotations by reflection.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
final class CamelBeanPostProcessing implements BeanCreatedEventListener<Object> {

    private final BeanProvider<CamelContext> camelContext;

    /**
     * @param camelContext The Camel context
     */
    CamelBeanPostProcessing(BeanProvider<CamelContext> camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        BeanDefinition<Object> definition = event.getBeanDefinition();
        if (!definition.hasStereotype(CamelPostProcess.class)) {
            return event.getBean();
        }
        Object bean = event.getBean();
        String name = definition.getBeanType().getName();
        CamelBeanPostProcessor postProcessor = PluginHelper.getBeanPostProcessor(camelContext.get());
        try {
            Object processed = postProcessor.postProcessBeforeInitialization(bean, name);
            return postProcessor.postProcessAfterInitialization(processed, name);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
