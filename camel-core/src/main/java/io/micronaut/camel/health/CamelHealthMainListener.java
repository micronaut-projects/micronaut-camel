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
package io.micronaut.camel.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import jakarta.inject.Singleton;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.HealthConfigurationProperties;
import org.apache.camel.main.MainListenerSupport;

/**
 * Turns on Camel's health checks when the Micronaut health endpoint is present.
 *
 * <p>camel-main only registers the context, routes, consumers, producers and registry health checks when
 * a {@code camel.health.*} option is set. With the health endpoint present they are registered by default,
 * as in Camel Spring Boot, and camel-main still applies every {@code camel.health.*} option, including
 * {@code camel.health.enabled=false}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = {HealthEndpoint.class, DefaultHealthCheckRegistry.class})
@Requires(beans = HealthEndpoint.class)
@Internal
final class CamelHealthMainListener extends MainListenerSupport {

    /**
     * The id of the health check registry when Camel runs in Micronaut.
     */
    static final String REGISTRY_ID = "camel-micronaut";

    @Override
    public void beforeConfigure(BaseMainSupport main) {
        HealthConfigurationProperties health = main.configure().health();
        if (health.getEnabled() == null) {
            health.setEnabled(true);
        }
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
        HealthCheckRegistry registry = HealthCheckRegistry.get(main.getCamelContext());
        if (registry != null) {
            // signals that health checks are reported by Micronaut
            registry.setId(REGISTRY_ID);
        }
    }
}
