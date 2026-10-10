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

import com.rabbitmq.client.Address;
import io.micronaut.camel.requires.RequiresCamelSpringRabbitmq;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.rabbitmq.connect.RabbitConnectionFactoryConfig;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates the Spring AMQP {@link ConnectionFactory} and {@link AmqpAdmin} that the Camel Spring
 * RabbitMQ component ({@code camel-spring-rabbitmq}) autowires, from the connection configuration of
 * Micronaut RabbitMQ ({@code rabbitmq.*}).
 *
 * <p>There is a connection factory, and an admin, per connection of Micronaut RabbitMQ, named after
 * it. An application that defines its own Spring AMQP connection factory marks it {@code @Primary},
 * so that Camel autowires it, or sets {@code micronaut.camel.rabbitmq.enabled} to {@code false}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@RequiresCamelSpringRabbitmq
@Requires(classes = {CachingConnectionFactory.class, RabbitConnectionFactoryConfig.class})
@Requires(property = SpringRabbitFactory.ENABLED, notEquals = StringUtils.FALSE)
final class SpringRabbitFactory {

    /**
     * The property that disables the integration with Micronaut RabbitMQ when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.rabbitmq.enabled";

    /**
     * A Spring AMQP connection factory that connects with the settings of a Micronaut RabbitMQ
     * connection. Spring AMQP recovers connections itself, so the automatic recovery of the RabbitMQ
     * client is disabled, on a copy of the settings that leaves Micronaut's connection unchanged.
     *
     * @param config The configuration of a Micronaut RabbitMQ connection
     * @return The connection factory
     */
    @EachBean(RabbitConnectionFactoryConfig.class)
    @Bean(preDestroy = "destroy")
    CachingConnectionFactory springRabbitConnectionFactory(RabbitConnectionFactoryConfig config) {
        com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory = config.clone();
        rabbitConnectionFactory.setAutomaticRecoveryEnabled(false);
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);
        config.getAddresses().filter(addresses -> !addresses.isEmpty()).ifPresent(addresses ->
            connectionFactory.setAddresses(format(addresses)));
        return connectionFactory;
    }

    /**
     * The admin with which Camel declares exchanges, queues and bindings.
     *
     * @param connectionFactory The connection factory
     * @return The admin
     */
    @EachBean(CachingConnectionFactory.class)
    RabbitAdmin springRabbitAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    private static String format(List<Address> addresses) {
        return addresses.stream()
            .map(address -> address.getPort() < 0 ? address.getHost() : address.getHost() + ':' + address.getPort())
            .collect(Collectors.joining(","));
    }
}
