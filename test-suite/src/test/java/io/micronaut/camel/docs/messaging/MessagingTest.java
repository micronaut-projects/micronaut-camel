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
package io.micronaut.camel.docs.messaging;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "MessagingTest")
class MessagingTest implements TestPropertyProvider {

    private final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    @Override
    public @NonNull Map<String, String> getProperties() {
        kafka.start();
        // what Micronaut Kafka is configured with, here the broker of the test
        return Map.of("kafka.bootstrap.servers", kafka.getBootstrapServers());
    }

    @AfterAll
    void stopKafka() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
        kafka.stop();
    }

    @Test
    void routesUseTheBrokersOfMicronautKafka() {
        producerTemplate.sendBody("direct:send-order", "order-1");

        assertEquals("order-1", consumerTemplate.receiveBody("seda:shipping", 30_000, String.class));
    }
}
