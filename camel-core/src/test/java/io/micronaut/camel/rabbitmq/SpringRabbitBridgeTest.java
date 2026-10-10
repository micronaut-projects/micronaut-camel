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
package io.micronaut.camel.rabbitmq;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.rabbitmq.connect.RabbitConnectionFactoryConfig;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "spec.name", value = "SpringRabbitBridgeTest")
@Property(name = "camel.component.spring-rabbitmq.auto-declare", value = "true")
class SpringRabbitBridgeTest implements TestPropertyProvider {

    private final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    RabbitConnectionFactoryConfig rabbitConfig;

    @Override
    public @NonNull Map<String, String> getProperties() {
        rabbit.start();
        return Map.of(
            "rabbitmq.host", rabbit.getHost(),
            "rabbitmq.port", String.valueOf(rabbit.getAmqpPort()),
            "rabbitmq.username", rabbit.getAdminUsername(),
            "rabbitmq.password", rabbit.getAdminPassword()
        );
    }

    @AfterAll
    void stopRabbit() {
        // stop Camel before the broker
        if (applicationContext != null) {
            applicationContext.stop();
        }
        rabbit.stop();
    }

    @Test
    void routesUseTheConnectionOfMicronautRabbitmq() throws Exception {
        SpringRabbitMQComponent component = camelContext.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class);
        CachingConnectionFactory connectionFactory = assertInstanceOf(CachingConnectionFactory.class, component.getConnectionFactory());
        assertInstanceOf(RabbitAdmin.class, component.getAmqpAdmin());
        assertEquals(rabbit.getAmqpPort(), connectionFactory.getPort());
        assertFalse(connectionFactory.getRabbitConnectionFactory().isAutomaticRecoveryEnabled());
        // the configuration of Micronaut RabbitMQ is left unchanged
        assertTrue(rabbitConfig.isAutomaticRecoveryEnabled());

        MockEndpoint orders = camelContext.getEndpoint("mock:orders", MockEndpoint.class);
        orders.expectedBodiesReceived("order-1");

        producerTemplate.sendBody("direct:orders", "order-1");

        orders.assertIsSatisfied();
    }

    @Singleton
    @Requires(property = "spec.name", value = "SpringRabbitBridgeTest")
    static class OrderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:orders").to("spring-rabbitmq:bridge-orders?routingKey=orders");
            from("spring-rabbitmq:bridge-orders?queues=bridge-orders&routingKey=orders").to("mock:orders");
        }
    }
}
