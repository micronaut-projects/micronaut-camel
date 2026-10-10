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
package io.micronaut.camel.test;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@Property(name = "camel.test.routes", value = "orders")
@ExcludeRoutes(TestRoutes.AuditRoutes.class)
@AutoStartupExclude("warehouse")
class ExcludeRoutesTest {

    @Inject
    CamelContext camelContext;

    @Test
    void excludedRouteBuildersAreNotAdded() {
        assertNotNull(camelContext.getRoute("orders"));
        assertNull(camelContext.getRoute("audit"));
    }

    @Test
    void excludedRoutesDoNotStart() {
        assertEquals(ServiceStatus.Started, camelContext.getRouteController().getRouteStatus("orders"));
        assertEquals(ServiceStatus.Stopped, camelContext.getRouteController().getRouteStatus("warehouse"));
    }
}
