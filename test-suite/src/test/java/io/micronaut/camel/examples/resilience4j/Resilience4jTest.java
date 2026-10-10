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
package io.micronaut.camel.examples.resilience4j;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.resilience4j.ResilienceProcessor;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.builder.Builder.exchangeProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code resilience4j} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The example has a client application, in Spring Boot and in Camel Main, that calls {@code service1} with a
 * circuit breaker and falls back to {@code service2}, each service being an application of its own. Here they are
 * one application: the services are {@code platform-http} routes on the Micronaut HTTP server, which the client
 * route calls with camel-http, and stopping the {@code service1} route plays stopping the {@code service1}
 * application. The circuit breaker is configured with the {@code camel.resilience4j.*} options of
 * {@code application-example-resilience4j.yml}, which camel-main applies, and the test shortens the time the
 * circuit stays open from 20 to 2 seconds.
 * <p>
 * The test checks the configuration, then the fallback: the client gets the response of {@code service1}, then of
 * {@code service2} once {@code service1} is stopped, until the circuit opens and the client no longer calls
 * {@code service1}, and the circuit closes again once {@code service1} is back.
 */
@MicronautTest(environments = "example-resilience4j")
@Property(name = "camel.resilience4j.wait-duration-in-open-state", value = "2000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Resilience4jTest implements TestPropertyProvider {

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        return Map.of("micronaut.server.port", String.valueOf(SocketUtils.findAvailableTcpPort()));
    }

    @Test
    void circuitBreakerIsConfiguredFromMicronautConfiguration() {
        Resilience4jConfigurationDefinition configuration = ((ModelCamelContext) camelContext).getResilience4jConfiguration(null);

        assertEquals("5", configuration.getMinimumNumberOfCalls());
        assertEquals("10", configuration.getSlidingWindowSize());
        assertEquals("TIME_BASED", configuration.getSlidingWindowType());
        assertEquals("50.0", configuration.getFailureRateThreshold());
        assertEquals("2000", configuration.getWaitDurationInOpenState());
    }

    @Test
    void fallsBackToService2WhileService1IsDown() throws Exception {
        ResilienceProcessor circuitBreaker = camelContext.getProcessor("circuitBreaker", ResilienceProcessor.class);

        // service1 answers
        assertTrue(response("Service1-").matches(10, TimeUnit.SECONDS));
        assertEquals("CLOSED", circuitBreaker.getCircuitBreakerState());

        // service1 is down: the fallback calls service2
        camelContext.getRouteController().stopRoute("service1");
        assertTrue(response("Service2-").matches(10, TimeUnit.SECONDS));

        // once half of the calls fail, the circuit opens and the client calls service2 without trying service1
        await(circuitBreaker, "OPEN");
        NotifyBuilder shortCircuited = new NotifyBuilder(camelContext).fromRoute("client")
            .whenAnyDoneMatches(exchangeProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED).isEqualTo(true))
            .and().whenAnyDoneMatches(body().startsWith("Service2-"))
            .create();
        assertTrue(shortCircuited.matches(10, TimeUnit.SECONDS));

        // service1 is back: the circuit half opens after 2 seconds, and closes once the calls succeed
        camelContext.getRouteController().startRoute("service1");
        await(circuitBreaker, "CLOSED");
        assertTrue(response("Service1-").matches(10, TimeUnit.SECONDS));
    }

    private NotifyBuilder response(String prefix) {
        return new NotifyBuilder(camelContext).fromRoute("client").whenAnyDoneMatches(body().startsWith(prefix)).create();
    }

    private static void await(ResilienceProcessor circuitBreaker, String state) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (!state.equals(circuitBreaker.getCircuitBreakerState())) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("The circuit breaker is " + circuitBreaker.getCircuitBreakerState() + ", not " + state);
            }
            Thread.sleep(100);
        }
    }
}
