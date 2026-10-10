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
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Collects the route builders in the Camel registry, including the lambda-style
 * {@code LambdaEndpointRouteBuilder} beans of the Endpoint DSL when camel-endpointdsl is on the classpath,
 * and rejects the XML routes that {@code camel.main.routes-include-pattern} finds with a clear message.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautRoutesCollector extends DefaultRoutesCollector {

    private static final boolean ENDPOINT_DSL_PRESENT = ClassUtils.isPresent(
        "org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder", MicronautRoutesCollector.class.getClassLoader());

    @Override
    public Collection<RoutesBuilder> collectRoutesFromDirectory(CamelContext camelContext, String excludePattern, String includePattern) {
        try {
            return super.collectRoutesFromDirectory(camelContext, excludePattern, includePattern);
        } catch (IllegalArgumentException e) {
            rejectXmlRoutes(camelContext, excludePattern, includePattern);
            throw e;
        }
    }

    /**
     * Fails with a clear message when the route resources include XML files, which need the XML DSL
     * (camel-xml-io-dsl), not supported by Micronaut Camel. Camel would only report that it cannot find
     * a loader for the {@code xml} extension.
     *
     * @param camelContext   The Camel context
     * @param excludePattern The {@code camel.main.routes-exclude-pattern}
     * @param includePattern The {@code camel.main.routes-include-pattern}
     */
    void rejectXmlRoutes(CamelContext camelContext, @Nullable String excludePattern, @Nullable String includePattern) {
        if (includePattern == null || includePattern.isBlank() || hasXmlLoader(camelContext)) {
            return;
        }
        List<String> xmlRoutes = findRouteResourcesFromDirectory(camelContext, excludePattern, includePattern).stream()
            .map(Resource::getLocation)
            .filter(location -> location.toLowerCase(Locale.ROOT).endsWith(".xml"))
            .toList();
        if (!xmlRoutes.isEmpty()) {
            throw new IllegalStateException("XML routes are not supported by Micronaut Camel: camel.main.routes-include-pattern ("
                + includePattern + ") matches " + String.join(", ", xmlRoutes) + ". Write the routes with the Java DSL, "
                + "as RouteBuilder beans, or with the YAML DSL (camel-yaml-dsl), or exclude the XML files from "
                + "camel.main.routes-include-pattern");
        }
    }

    private static boolean hasXmlLoader(CamelContext camelContext) {
        return camelContext.getCamelContextExtension().getBootstrapFactoryFinder(RoutesBuilderLoader.FACTORY_PATH)
            .findClass("xml").isPresent();
    }

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
