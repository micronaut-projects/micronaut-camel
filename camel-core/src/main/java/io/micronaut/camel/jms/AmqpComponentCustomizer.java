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

import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.camel.requires.RequiresCamelAmqp;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.qpid.jms.JmsConnectionFactory;

/**
 * Gives the Camel AMQP component ({@code camel-amqp}) the AMQP 1.0 connection factory bean, a Qpid
 * JMS {@link JmsConnectionFactory}, when the application also has the connection factories of
 * Micronaut JMS and so Camel cannot autowire one. It does nothing when
 * {@code camel.component.amqp.connection-factory} or one of the options from which the component
 * creates its own connection factory, such as {@code host} or {@code broker-url}, is set.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelAmqp
@Requires(classes = {AMQPComponent.class, JmsConnectionFactory.class, JMSConnectionPool.class})
@Requires(beans = JmsConnectionFactories.class)
final class AmqpComponentCustomizer implements ComponentCustomizer {

    private final JmsConnectionFactories connectionFactories;
    private final Environment environment;

    /**
     * @param connectionFactories Selects the connection factory
     * @param environment         The environment
     */
    AmqpComponentCustomizer(JmsConnectionFactories connectionFactories, Environment environment) {
        this.connectionFactories = connectionFactories;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target.getClass() == AMQPComponent.class;
    }

    @Override
    public void configure(String name, Component target) {
        JmsConfiguration configuration = ((AMQPComponent) target).getConfiguration();
        if (configuration.getConnectionFactory() == null
            && !CamelComponentOptions.isSet(environment, name, "connectionFactory", "brokerUrl", "host", "port",
            "username", "password", "useSsl", "useTopicPrefix")) {
            JmsConnectionFactory connectionFactory = connectionFactories.select(JmsConnectionFactory.class);
            if (connectionFactory != null) {
                configuration.setConnectionFactory(connectionFactory);
            }
        }
    }
}
