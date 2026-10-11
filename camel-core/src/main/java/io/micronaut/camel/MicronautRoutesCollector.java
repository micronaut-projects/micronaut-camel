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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder;
import org.apache.camel.main.DefaultRoutesCollector;

import java.util.Collection;
import java.util.List;

/**
 * Collects the route builders in the Camel registry, including the lambda-style
 * {@code LambdaEndpointRouteBuilder} beans of the Endpoint DSL when camel-endpointdsl is on the classpath.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautRoutesCollector extends DefaultRoutesCollector {

    private static final boolean ENDPOINT_DSL_PRESENT = ClassUtils.isPresent(
        "org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder", MicronautRoutesCollector.class.getClassLoader());

    @Override
    protected Collection<RoutesBuilder> collectAdditionalRoutesFromRegistry(
        CamelContext camelContext, String excludePattern, String includePattern) {
        return ENDPOINT_DSL_PRESENT ? EndpointDsl.collect(this, camelContext) : List.of();
    }

    /**
     * Only loaded when camel-endpointdsl is on the classpath.
     */
    private static final class EndpointDsl {

        static Collection<RoutesBuilder> collect(MicronautRoutesCollector collector, CamelContext camelContext) {
            return collector.findByType(camelContext, LambdaEndpointRouteBuilder.class).stream()
                .<RoutesBuilder>map(lambda -> new EndpointRouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        lambda.accept(this);
                    }
                })
                .toList();
        }
    }
}
