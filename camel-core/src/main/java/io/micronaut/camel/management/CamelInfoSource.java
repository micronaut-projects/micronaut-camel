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
package io.micronaut.camel.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * Adds the name, version, status and uptime of the {@link CamelContext} to the {@code info} endpoint,
 * under {@code camel}. Disable it with {@code endpoints.info.camel.enabled=false}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = InfoEndpoint.class)
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.camel.enabled", notEquals = StringUtils.FALSE)
@Internal
final class CamelInfoSource implements InfoSource {

    private final CamelContext camelContext;

    /**
     * @param camelContext The Camel context
     */
    CamelInfoSource(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Publisher<PropertySource> getSource() {
        return Publishers.just(PropertySource.of("camel", Map.of("camel", CamelEndpoint.contextInfo(camelContext))));
    }
}
