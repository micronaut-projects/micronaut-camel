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
package io.micronaut.camel.jms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.activemq.ArtemisContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "JmsBridgeTest")
class JmsBridgeTest implements TestPropertyProvider {

    private final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:latest");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public @NonNull Map<String, String> getProperties() {
        artemis.start();
        return Map.of(
            "micronaut.jms.activemq.artemis.enabled", "true",
            "micronaut.jms.activemq.artemis.connection-string", artemis.getBrokerUrl(),
            "micronaut.jms.activemq.artemis.username", artemis.getUser(),
            "micronaut.jms.activemq.artemis.password", artemis.getPassword()
        );
    }

    @AfterAll
    void stopArtemis() {
        // stop Camel before the broker
        if (applicationContext != null) {
            applicationContext.stop();
        }
        artemis.stop();
    }

    @Test
    void jmsRoutesUseTheConnectionFactoryOfMicronautJms() throws Exception {
        assertInstanceOf(ActiveMQJMSConnectionFactory.class,
            camelContext.getComponent("jms", JmsComponent.class).getConfiguration().getConnectionFactory());

        MockEndpoint orders = camelContext.getEndpoint("mock:jms", MockEndpoint.class);
        orders.expectedBodiesReceived("order-1");

        producerTemplate.sendBody("direct:jms", "order-1");

        orders.assertIsSatisfied();
    }

    @Test
    void sjmsRoutesUseTheConnectionFactoryOfMicronautJms() throws Exception {
        assertInstanceOf(ActiveMQJMSConnectionFactory.class,
            camelContext.getComponent("sjms", SjmsComponent.class).getConnectionFactory());

        MockEndpoint orders = camelContext.getEndpoint("mock:sjms", MockEndpoint.class);
        orders.expectedBodiesReceived("order-2");

        producerTemplate.sendBody("direct:sjms", "order-2");

        orders.assertIsSatisfied();
    }

    @Singleton
    @Requires(property = "spec.name", value = "JmsBridgeTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:jms").to("jms:queue:bridge.jms");
            from("jms:queue:bridge.jms").to("mock:jms");
            from("direct:sjms").to("sjms:queue:bridge.sjms");
            from("sjms:queue:bridge.sjms").to("mock:sjms");
        }
    }
}
