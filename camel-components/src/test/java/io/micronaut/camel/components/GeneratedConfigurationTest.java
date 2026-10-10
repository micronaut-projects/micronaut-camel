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
package io.micronaut.camel.components;

import io.micronaut.camel.config.CamelMainConfiguration;
import io.micronaut.camel.requires.RequiresCamelKafka;
import io.micronaut.camel.requires.RequiresCamelSeda;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedConfigurationTest {

    @Test
    void componentOptionsAreBoundAndAppliedByCamel() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "GeneratedConfigurationTest",
            "camel.component.seda.queue-size", "42",
            "camel.component.seda.default-block-when-full", "true"))) {

            SedaComponentConfiguration configuration = context.getBean(SedaComponentConfiguration.class);
            assertEquals(42, configuration.getQueueSize());
            assertEquals(Boolean.TRUE, configuration.getDefaultBlockWhenFull());

            SedaComponent seda = context.getBean(CamelContext.class).getComponent("seda", SedaComponent.class);
            assertEquals(42, seda.getQueueSize());
            assertTrue(seda.isDefaultBlockWhenFull());
        }
    }

    @Test
    void configurationIsInactiveUntilAnOptionIsSet() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "GeneratedConfigurationTest"))) {
            assertFalse(context.containsBean(SedaComponentConfiguration.class));
            assertFalse(context.containsBean(CamelMainConfiguration.class));
        }
    }

    @Test
    void mainOptionsAreBound() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "GeneratedConfigurationTest",
            "camel.main.name", "orders",
            "camel.main.shutdown-timeout", "5"))) {

            CamelMainConfiguration configuration = context.getBean(CamelMainConfiguration.class);
            assertEquals("orders", configuration.getName());
            assertEquals(5, configuration.getShutdownTimeout());
            assertEquals("orders", context.getBean(CamelContext.class).getName());
        }
    }

    @Test
    void requirementAnnotationsDependOnTheComponentBeingPresent() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "GeneratedConfigurationTest"))) {
            assertTrue(context.containsBean(SedaIntegration.class));
            // camel-kafka is not on the test classpath
            assertFalse(context.containsBean(KafkaIntegration.class));
        }
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "GeneratedConfigurationTest",
            "camel.component.seda.enabled", "false"))) {
            assertFalse(context.containsBean(SedaIntegration.class));
        }
    }

    @Singleton
    @RequiresCamelSeda
    @Requires(property = "spec.name", value = "GeneratedConfigurationTest")
    static class SedaIntegration {
    }

    @Singleton
    @RequiresCamelKafka
    @Requires(property = "spec.name", value = "GeneratedConfigurationTest")
    static class KafkaIntegration {
    }
}
