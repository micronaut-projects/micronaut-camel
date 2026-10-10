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
package io.micronaut.camel.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "KafkaBridgeTest")
class KafkaBridgeTest implements TestPropertyProvider {

    private final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public @NonNull Map<String, String> getProperties() {
        kafka.start();
        return Map.of("kafka.bootstrap.servers", kafka.getBootstrapServers());
    }

    @AfterAll
    void stopKafka() {
        // stop Camel before the broker
        if (applicationContext != null) {
            applicationContext.stop();
        }
        kafka.stop();
    }

    @Test
    void routesUseTheBrokersOfMicronautKafka() throws Exception {
        KafkaComponent component = camelContext.getComponent("kafka", KafkaComponent.class);
        assertEquals(kafka.getBootstrapServers(), component.getConfiguration().getBrokers());
        assertInstanceOf(MicronautKafkaClientFactory.class, component.getKafkaClientFactory());

        MockEndpoint orders = camelContext.getEndpoint("mock:orders", MockEndpoint.class);
        orders.expectedBodiesReceived("order-1");
        orders.setResultWaitTime(TimeUnit.SECONDS.toMillis(30));

        producerTemplate.sendBody("direct:orders", "order-1");

        orders.assertIsSatisfied();
    }

    @Singleton
    @Requires(property = "spec.name", value = "KafkaBridgeTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:orders").to("kafka:bridge-orders");
            from("kafka:bridge-orders?groupId=kafka-bridge-test&autoOffsetReset=earliest").to("mock:orders");
        }
    }
}
