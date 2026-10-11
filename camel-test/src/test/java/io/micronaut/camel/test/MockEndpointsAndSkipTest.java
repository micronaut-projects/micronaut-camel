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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Property(name = "camel.test.routes", value = "orders")
@MockEndpointsAndSkip("direct:warehouse")
class MockEndpointsAndSkipTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void matchingEndpointsAreSkipped() throws Exception {
        MockEndpoint warehouse = camelContext.getEndpoint("mock:direct:warehouse", MockEndpoint.class);
        warehouse.expectedBodiesReceived("book");

        // the warehouse route is skipped, so the body is not changed
        assertEquals("book", producerTemplate.requestBody("direct:order", "book", String.class));

        warehouse.assertIsSatisfied();
    }

    @Test
    void theDefaultShutdownTimeoutIsShorterForTests() {
        assertEquals(10, camelContext.getShutdownStrategy().getTimeout());
    }
}
