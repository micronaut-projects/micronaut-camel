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
package io.micronaut.camel.cluster;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The supervising route controller of {@code camel.routecontroller.*}: a route that fails to start
 * does not fail the application, and is restarted in the background.
 */
@MicronautTest
@Property(name = "spec.name", value = "SupervisingRouteControllerTest")
@Property(name = "camel.routecontroller.enabled", value = "true")
@Property(name = "camel.routecontroller.back-off-delay", value = "50")
@Property(name = "camel.routecontroller.back-off-max-attempts", value = "2")
class SupervisingRouteControllerTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void failingRoutesDoNotStopTheApplication() throws Exception {
        SupervisingRouteController controller = assertInstanceOf(SupervisingRouteController.class,
            camelContext.getRouteController());
        assertEquals(50, controller.getBackOffDelay());

        assertTrue(camelContext.getStatus().isStarted());
        assertEquals("ok", producerTemplate.requestBody("direct:healthy", "x", String.class));

        // the failing route is retried, then given up
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!controller.getExhaustedRoutes().stream().anyMatch(r -> r.getRouteId().equals("unreliable"))) {
            assertTrue(System.nanoTime() < deadline, "the route was not given up");
            Thread.sleep(50);
        }
        assertEquals(ServiceStatus.Stopped, camelContext.getRouteController().getRouteStatus("unreliable"));
    }

    @Singleton
    @Requires(property = "spec.name", value = "SupervisingRouteControllerTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:healthy").setBody(constant("ok"));
            from("unreliable:source").routeId("unreliable").to("log:unreliable");
        }
    }

    /**
     * A component whose consumers cannot start, as when a broker is down.
     */
    @Singleton
    @Named("unreliable")
    @Requires(property = "spec.name", value = "SupervisingRouteControllerTest")
    static class UnreliableComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {

                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() {
                            throw new IllegalStateException("The source is down");
                        }
                    };
                }
            };
        }
    }
}
