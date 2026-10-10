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
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Property(name = "camel.test.routes", value = "orders")
@MockEndpoints("log:*")
@ShutdownTimeout(value = 5, timeUnit = TimeUnit.SECONDS)
class MockEndpointsTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void messagesToMatchingEndpointsAlsoReachAMock() throws Exception {
        MockEndpoint orders = camelContext.getEndpoint("mock:log:orders", MockEndpoint.class);
        orders.expectedBodiesReceived("book");

        assertEquals("shipped book", producerTemplate.requestBody("direct:order", "book", String.class));

        orders.assertIsSatisfied();
    }

    @Test
    void theShutdownTimeoutIsApplied() {
        assertEquals(5, camelContext.getShutdownStrategy().getTimeout());
    }
}
