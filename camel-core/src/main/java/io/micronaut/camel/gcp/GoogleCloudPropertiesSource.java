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
package io.micronaut.camel.gcp;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.gcp.GoogleCloudConfiguration;
import jakarta.inject.Singleton;
import org.apache.camel.Ordered;
import org.apache.camel.spi.PropertiesSource;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the {@code {{gcp.project-id}}} placeholder of Camel to the project of Micronaut GCP, when
 * Google Cloud detects it from the environment rather than from the {@code gcp.project-id}
 * property. The Google components whose project is part of the URI, such as
 * {@code google-pubsub:{{gcp.project-id}}:orders}, then use the project of the application. Camel
 * finds the {@link PropertiesSource} beans in the registry.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = GoogleCloudConfiguration.class)
@Requires(property = GoogleCloudPropertiesSource.ENABLED, notEquals = StringUtils.FALSE)
final class GoogleCloudPropertiesSource implements PropertiesSource, Ordered {

    /**
     * The property that disables the integration with Micronaut GCP when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.gcp.enabled";

    private final BeanProvider<GoogleCloudConfiguration> configuration;

    /**
     * @param configuration The Google Cloud configuration
     */
    GoogleCloudPropertiesSource(BeanProvider<GoogleCloudConfiguration> configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getName() {
        return "MicronautGoogleCloudPropertiesSource";
    }

    @Override
    public @Nullable String getProperty(String name) {
        if (!"gcp.project-id".equals(name) && !"gcp.projectId".equals(name)) {
            return null;
        }
        return configuration.find(null)
            .filter(GoogleCloudConfiguration::hasProjectId)
            .map(GoogleCloudConfiguration::getProjectId)
            .orElse(null);
    }

    @Override
    public int getOrder() {
        // after the Micronaut environment, which has the gcp.project-id property when it is set
        return Ordered.LOWEST;
    }
}
