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
package io.micronaut.camel.mqtt;

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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "PahoMqtt5BridgeTest")
class PahoMqtt5BridgeTest implements TestPropertyProvider {

    private final HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2024.3"));

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public @NonNull Map<String, String> getProperties() {
        hivemq.start();
        return Map.of("mqtt.client.server-uri", serverUri());
    }

    @AfterAll
    void stopHivemq() {
        // stop Camel before the broker
        if (applicationContext != null) {
            applicationContext.stop();
        }
        hivemq.stop();
    }

    @Test
    void routesUseTheServerOfMicronautMqtt() throws Exception {
        assertEquals(serverUri(), camelContext.getComponent("paho-mqtt5", PahoMqtt5Component.class).getConfiguration().getBrokerUrl());

        MockEndpoint readings = camelContext.getEndpoint("mock:readings", MockEndpoint.class);
        readings.expectedBodiesReceived("21.5");

        producerTemplate.sendBody("direct:readings", "21.5");

        readings.assertIsSatisfied();
    }

    private String serverUri() {
        return "tcp://" + hivemq.getHost() + ':' + hivemq.getMqttPort();
    }

    @Singleton
    @Requires(property = "spec.name", value = "PahoMqtt5BridgeTest")
    static class ReadingRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:readings").to("paho-mqtt5:bridge/readings?clientId=bridge-producer");
            from("paho-mqtt5:bridge/readings?clientId=bridge-consumer").to("mock:readings");
        }
    }
}
