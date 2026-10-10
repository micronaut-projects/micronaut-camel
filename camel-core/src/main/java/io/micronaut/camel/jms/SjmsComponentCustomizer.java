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
import io.micronaut.camel.requires.RequiresCamelSjms;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.apache.camel.Component;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.spi.ComponentCustomizer;

/**
 * Gives the Camel Simple JMS component ({@code camel-sjms}) the connection factory of Micronaut JMS,
 * unless {@code camel.component.sjms.connection-factory} is set.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelSjms
@Requires(classes = {SjmsComponent.class, JMSConnectionPool.class})
@Requires(beans = JmsConnectionFactories.class)
final class SjmsComponentCustomizer implements ComponentCustomizer {

    private final JmsConnectionFactories connectionFactories;
    private final Environment environment;

    /**
     * @param connectionFactories Selects the connection factory
     * @param environment         The environment
     */
    SjmsComponentCustomizer(JmsConnectionFactories connectionFactories, Environment environment) {
        this.connectionFactories = connectionFactories;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target.getClass() == SjmsComponent.class;
    }

    @Override
    public void configure(String name, Component target) {
        SjmsComponent component = (SjmsComponent) target;
        if (component.getConnectionFactory() == null
            && !CamelComponentOptions.isSet(environment, name, "connectionFactory")) {
            ConnectionFactory connectionFactory = connectionFactories.select();
            if (connectionFactory != null) {
                component.setConnectionFactory(connectionFactory);
            }
        }
    }
}
