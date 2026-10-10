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
package io.micronaut.camel.docs.messaging

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.apache.camel.ConsumerTemplate
import org.apache.camel.ProducerTemplate
import org.testcontainers.kafka.KafkaContainer
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@Property(name = 'spec.name', value = 'MessagingTest')
class MessagingTest extends Specification implements TestPropertyProvider {

    @Shared
    KafkaContainer kafka = new KafkaContainer('apache/kafka:4.2.0')

    @Inject
    ProducerTemplate producerTemplate

    @Inject
    ConsumerTemplate consumerTemplate

    @Override
    Map<String, String> getProperties() {
        kafka.start()
        // what Micronaut Kafka is configured with, here the broker of the test
        ['kafka.bootstrap.servers': kafka.bootstrapServers]
    }

    void cleanupSpec() {
        // the application context, and so Camel, is already stopped
        kafka.stop()
    }

    void 'routes use the brokers of Micronaut Kafka'() {
        when:
        producerTemplate.sendBody('direct:send-order', 'order-1')

        then:
        consumerTemplate.receiveBody('seda:shipping', 30_000, String) == 'order-1'
    }
}
