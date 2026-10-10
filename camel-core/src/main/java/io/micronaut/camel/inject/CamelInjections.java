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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;

/**
 * Creates the values of the injection points that Camel's {@code @EndpointInject} and
 * {@code @Produce} declare, with Camel's own rules: an endpoint, a producer or consumer template, a
 * producer, a polling consumer, or a proxy of an interface that sends to the endpoint. The factories
 * that the annotation processor generates call it.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class CamelInjections {

    private CamelInjections() {
    }

    /**
     * @param type           The type of the injection point
     * @param injectionPoint The injection point
     * @param camelContext   The Camel context
     * @return The value to inject
     */
    public static Object inject(Class<?> type, InjectionPoint<?> injectionPoint, CamelContext camelContext) {
        AnnotationMetadata metadata = injectionPoint.getAnnotationMetadata();
        String uri = metadata.stringValue(CamelEndpoint.class, "uri").orElse("");
        if (uri.isEmpty()) {
            throw new IllegalStateException("No endpoint URI for the Camel injection point " + injectionPoint
                + ": set the value of @EndpointInject or @Produce");
        }
        boolean binding = metadata.booleanValue(CamelEndpoint.class, "binding").orElse(true);
        Object value = new CamelPostProcessorHelper(camelContext)
            .getInjectionValue(type, uri, null, name(injectionPoint), null, null, binding);
        if (value == null) {
            throw new IllegalStateException("Camel cannot inject " + type.getName() + " for " + uri + " into " + injectionPoint);
        }
        return value;
    }

    private static String name(InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof FieldInjectionPoint<?, ?> field) {
            return field.getName();
        }
        if (injectionPoint instanceof ArgumentInjectionPoint<?, ?> argument) {
            return argument.getArgument().getName();
        }
        return injectionPoint.toString();
    }
}
