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

import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.gcp.GoogleCloudConfiguration;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;

/**
 * Sets the {@code projectId} of the Camel Google components that have one, such as
 * {@code google-bigquery} and {@code google-firestore}, to the project of Micronaut GCP: the
 * {@code gcp.project-id} property, or the project that Google Cloud detects from the environment.
 * A {@code camel.component.<name>.project-id} setting takes precedence.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = GoogleCloudConfiguration.class)
@Requires(property = GoogleCloudPropertiesSource.ENABLED, notEquals = StringUtils.FALSE)
final class GoogleCloudComponentCustomizer implements ComponentCustomizer {

    private static final String PROJECT_ID = "projectId";
    private static final String GOOGLE_COMPONENTS = "org.apache.camel.component.google.";

    private final BeanProvider<GoogleCloudConfiguration> configuration;
    private final Environment environment;

    /**
     * @param configuration The Google Cloud configuration
     * @param environment   The environment
     */
    GoogleCloudComponentCustomizer(BeanProvider<GoogleCloudConfiguration> configuration, Environment environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target.getClass().getName().startsWith(GOOGLE_COMPONENTS)
            && target.getComponentPropertyConfigurer() instanceof PropertyConfigurerGetter getter
            && getter.getOptionType(PROJECT_ID, true) != null;
    }

    @Override
    public void configure(String name, Component target) {
        PropertyConfigurer configurer = target.getComponentPropertyConfigurer();
        if (configurer == null || CamelComponentOptions.isSet(environment, name, PROJECT_ID)
            || ((PropertyConfigurerGetter) configurer).getOptionValue(target, PROJECT_ID, true) != null) {
            return;
        }
        configuration.find(null)
            .filter(GoogleCloudConfiguration::hasProjectId)
            .ifPresent(gcp -> configurer.configure(target.getCamelContext(), target, PROJECT_ID, gcp.getProjectId(), true));
    }
}
