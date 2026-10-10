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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "camel.health.enabled", value = "false")
class CamelHealthDisabledTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Test
    void camelHealthChecksAreDisabled() {
        assertFalse(applicationContext.containsBean(CamelHealthIndicator.class));
        assertFalse(applicationContext.containsBean(CamelLivenessHealthIndicator.class));
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        // camel-main registers none of its checks
        assertTrue(registry == null || registry.getCheck("context").isEmpty());
    }
}
