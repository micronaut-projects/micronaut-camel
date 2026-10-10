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
package io.micronaut.camel.examples.pahomqtt5;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code paho-mqtt5-shared-subscriptions} example of camel-spring-boot-examples, which has no
 * test.
 * <p>
 * The route builder is a Micronaut singleton. The endpoints no longer set
 * {@code brokerUrl={{broker.url}}}, which pointed at the public {@code broker.emqx.io} broker:
 * micronaut-camel configures the {@code paho-mqtt5} component from the {@code mqtt.client.server-uri}
 * setting of Micronaut MQTT, which the test sets to a HiveMQ container. The test checks that the consumers
 * of the shared subscription receive each message once.
 */
@MicronautTest(environments = "example-paho-mqtt5-shared-subscriptions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PahoMqtt5SharedSubscriptionsTest implements TestPropertyProvider {

    private final HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2024.3"));

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ConsumedMessages consumedMessages;

    @Override
    public Map<String, String> getProperties() {
        hivemq.start();
        return Map.of("mqtt.client.server-uri", "tcp://" + hivemq.getHost() + ':' + hivemq.getMqttPort());
    }

    @AfterAll
    void stopHivemq() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
        hivemq.stop();
    }

    @Test
    void eachMessageIsConsumedOnceBySharedSubscribers() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).from("paho-mqtt5:*").whenDone(5).create();

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
        List<String> messages = List.copyOf(consumedMessages.messages);
        assertEquals(messages.size(), messages.stream().distinct().count(), () -> "duplicates in " + messages);
    }

    /**
     * Collects the messages the consumer routes complete.
     */
    @Singleton
    @Requires(env = "example-paho-mqtt5-shared-subscriptions")
    static class ConsumedMessages extends EventNotifierSupport {

        final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof ExchangeCompletedEvent completed
                && completed.getExchange().getFromRouteId().startsWith("consumer");
        }

        @Override
        public void notify(CamelEvent event) {
            messages.add(((ExchangeCompletedEvent) event).getExchange().getIn().getBody(String.class));
        }
    }
}
