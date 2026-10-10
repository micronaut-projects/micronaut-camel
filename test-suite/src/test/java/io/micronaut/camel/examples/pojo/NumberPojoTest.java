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
package io.micronaut.camel.examples.pojo;

import io.micronaut.camel.test.MockEndpoints;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code pojo} example of camel-spring-boot-examples, which has no test.
 * <p>
 * {@code NumberPojo} is a Micronaut singleton. The annotation processor turns its {@code @Produce} field into
 * an injection point and its {@code @Consume} methods into executable methods that are subscribed to
 * {@code direct:numbers} when Camel starts. The timer period became a placeholder so the test runs faster;
 * {@code camel.main.run-controller} and {@code camel.main.duration-max-seconds} were dropped.
 */
@MicronautTest(environments = "example-pojo")
@MockEndpoints("log:out")
class NumberPojoTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void numbersAreSortedByTheConsumeMethods() throws Exception {
        MockEndpoint out = camelContext.getEndpoint("mock:log:out", MockEndpoint.class);
        out.expectedMinimumMessageCount(2);

        producerTemplate.sendBody("direct:numbers", 7);
        producerTemplate.sendBody("direct:numbers", 150);

        out.assertIsSatisfied();
        // the example's own timer may add a random number
        List<String> bodies = out.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList();
        assertTrue(bodies.containsAll(List.of("Got a low number 7", "Got a high number 150")), bodies::toString);
    }
}
