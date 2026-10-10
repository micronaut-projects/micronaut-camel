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
import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects the methods annotated with Camel's {@code @Consume} at startup, and subscribes them to
 * their endpoints before Camel starts.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
public final class CamelConsumeMethods implements ExecutableMethodProcessor<CamelConsume> {

    private final BeanContext beanContext;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean subscribed;

    /**
     * @param beanContext The bean context
     */
    public CamelConsumeMethods(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        synchronized (subscriptions) {
            subscriptions.add(new Subscription(beanDefinition, method));
        }
    }

    /**
     * Subscribes the methods to their endpoints. The consumers start with Camel.
     *
     * @param camelContext The Camel context
     */
    public void subscribe(CamelContext camelContext) {
        List<Subscription> toSubscribe;
        synchronized (subscriptions) {
            if (subscribed) {
                return;
            }
            subscribed = true;
            toSubscribe = List.copyOf(subscriptions);
        }
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(camelContext);
        for (Subscription subscription : toSubscribe) {
            Object bean = beanContext.getBean(subscription.beanDefinition());
            String beanName = subscription.beanDefinition().getBeanType().getName();
            Method method = subscription.method().getTargetMethod();
            if (method.isAnnotationPresent(Consume.class)) {
                helper.consumerInjection(method, bean, beanName);
            } else {
                // the classes generated for Python beans do not carry @Consume: read it from the metadata,
                // unresolved, as the predicate is a Camel expression such as ${body} > 100
                AnnotationValue<Consume> consume = subscription.method().getAnnotation(Consume.class);
                Map<CharSequence, Object> values = consume != null ? consume.getValues() : Map.of();
                helper.subscribeMethod(method, bean, beanName, member(values, "value"), member(values, "property"),
                    member(values, "predicate"));
            }
        }
    }

    private static String member(Map<CharSequence, Object> values, String name) {
        Object value = values.get(name);
        return value != null ? value.toString() : "";
    }

    private record Subscription(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
    }
}
