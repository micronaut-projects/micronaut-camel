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

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A second instance of a component, configured from Micronaut configuration with camel-main's
 * {@code camel.beans.*}.
 */
@MicronautTest
@Property(name = "camel.component.seda.queue-size", value = "10")
@Property(name = "camel.beans.bulk", value = "#class:org.apache.camel.component.seda.SedaComponent")
@Property(name = "camel.beans.bulk.queue-size", value = "5000")
class NamedComponentsTest {

    @Inject
    CamelContext camelContext;

    @Test
    void componentsCanHaveSeveralConfiguredInstances() {
        assertEquals(10, camelContext.getComponent("seda", SedaComponent.class).getQueueSize());
        assertEquals(5000, camelContext.getComponent("bulk", SedaComponent.class).getQueueSize());
    }
}
