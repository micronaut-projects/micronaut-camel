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
package io.micronaut.camel.nats;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.nats.client.Connection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "NatsBridgeTest")
// Micronaut Kafka, NATS and RabbitMQ each define the consumer executor, unless it is configured
@Property(name = "micronaut.executors.consumer.type", value = "fixed")
class NatsBridgeTest implements TestPropertyProvider {

    private final GenericContainer<?> nats = new GenericContainer<>("nats:latest")
        .withExposedPorts(4222)
        .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    Connection connection;

    @Override
    public @NonNull Map<String, String> getProperties() {
        nats.start();
        return Map.of("nats.default.addresses", "nats://" + nats.getHost() + ':' + nats.getMappedPort(4222));
    }

    @AfterAll
    void stopNats() {
        // stop Camel before the broker
        if (applicationContext != null) {
            applicationContext.stop();
        }
        nats.stop();
    }

    @Test
    void routesUseTheConnectionOfMicronautNats() throws Exception {
        assertSame(connection, camelContext.getComponent("nats", NatsComponent.class).getConfiguration().getConnection());

        MockEndpoint orders = camelContext.getEndpoint("mock:orders", MockEndpoint.class);
        orders.expectedMinimumMessageCount(1);
        orders.setResultWaitTime(TimeUnit.SECONDS.toMillis(1));
        // the consumer subscribes asynchronously, so a message published before may not be received
        for (int attempt = 0; attempt < 10 && orders.getReceivedCounter() == 0; attempt++) {
            producerTemplate.sendBody("direct:orders", "order");
            orders.await(1, TimeUnit.SECONDS);
        }

        orders.assertIsSatisfied();
    }

    @Singleton
    @Requires(property = "spec.name", value = "NatsBridgeTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:orders").to("nats:bridge-orders");
            from("nats:bridge-orders").to("mock:orders");
        }
    }
}
