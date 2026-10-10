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

import io.micronaut.context.ApplicationContext;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.bigquery.GoogleBigQueryComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The project of Micronaut GCP in the Camel Google components, without Google Cloud.
 */
class GoogleCloudBridgeTest {

    @Test
    void theComponentsUseTheConfiguredProject() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("gcp.project-id", "configured-project"))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertEquals("configured-project", camelContext.getComponent("google-bigquery", GoogleBigQueryComponent.class).getProjectId());
            assertEquals("configured-project", camelContext.getEndpoint("google-pubsub:{{gcp.project-id}}:orders",
                GooglePubsubEndpoint.class).getProjectId());
        }
    }

    @Test
    void theComponentsUseTheDetectedProject() {
        // as when the application runs on Google Cloud, or with GOOGLE_CLOUD_PROJECT set
        System.setProperty("GOOGLE_CLOUD_PROJECT", "detected-project");
        try (ApplicationContext context = ApplicationContext.run()) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertEquals("detected-project", camelContext.getComponent("google-bigquery", GoogleBigQueryComponent.class).getProjectId());
            assertEquals("detected-project", camelContext.getEndpoint("google-pubsub:{{gcp.project-id}}:orders",
                GooglePubsubEndpoint.class).getProjectId());
        } finally {
            System.clearProperty("GOOGLE_CLOUD_PROJECT");
        }
    }

    @Test
    void theCamelOptionTakesPrecedence() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "gcp.project-id", "configured-project",
            "camel.component.google-bigquery.project-id", "camel-project"))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertEquals("camel-project", camelContext.getComponent("google-bigquery", GoogleBigQueryComponent.class).getProjectId());
        }
    }
}
