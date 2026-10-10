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
package io.micronaut.camel.examples.kafkaavro;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.avro.generic.GenericRecord;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code kafka-avro} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The route builder is a Micronaut singleton. The {@code Employee} class that the original generates from
 * {@code employee.avsc} with the avro-maven-plugin was replaced by Avro {@code GenericRecord}s of the same
 * schema, so the build needs no Avro code generation. The endpoints no longer set
 * {@code brokers={{kafka.bootstrap.url}}}: micronaut-camel takes the brokers from the
 * {@code kafka.bootstrap.servers} of Micronaut Kafka, which the test sets to a Kafka container. The
 * consumer reads from the earliest offset, so the first employees are not missed, and its processor is a
 * bean that keeps the consumed employees for the test.
 */
@MicronautTest(environments = "example-kafka-avro")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaAvroTest implements TestPropertyProvider {

    private final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    KafkaAvroMessageConsumerProcessor consumerProcessor;

    @Override
    public Map<String, String> getProperties() {
        kafka.start();
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
    void employeesGoThroughKafkaAsAvro() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).from("kafka:*").whenDone(1).create();

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
        GenericRecord employee = consumerProcessor.getEmployees().get(0);
        assertEquals("Kakarla", employee.get("firstName").toString());
        assertEquals("Ranjith", employee.get("lastName").toString());
    }
}
