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
package io.micronaut.camel.nats;

import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.camel.requires.RequiresCamelNats;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.util.StringUtils;
import io.nats.client.Connection;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.nats.NatsComponent;
import org.apache.camel.component.nats.NatsConfiguration;
import org.apache.camel.spi.ComponentCustomizer;

/**
 * Gives the Camel NATS component the NATS {@link Connection} bean, such as the connection of Micronaut
 * NATS ({@code nats.*}), unless {@code camel.component.nats.connection} or
 * {@code camel.component.nats.servers} is set. When there are several connections, the primary one
 * is used. Camel does not close a connection it is given.
 *
 * <p>Endpoints then use that connection, even when they set {@code servers}: give such an endpoint
 * its own {@code connection}, or set {@code micronaut.camel.nats.enabled} to {@code false}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelNats
@Requires(classes = {NatsComponent.class, Connection.class})
@Requires(beans = Connection.class)
@Requires(property = NatsComponentCustomizer.ENABLED, notEquals = StringUtils.FALSE)
final class NatsComponentCustomizer implements ComponentCustomizer {

    /**
     * The property that disables the integration with Micronaut NATS when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.nats.enabled";

    private final BeanProvider<Connection> connections;
    private final Environment environment;

    /**
     * @param connections The NATS connections
     * @param environment The environment
     */
    NatsComponentCustomizer(BeanProvider<Connection> connections, Environment environment) {
        this.connections = connections;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target instanceof NatsComponent;
    }

    @Override
    public void configure(String name, Component target) {
        NatsConfiguration configuration = ((NatsComponent) target).getConfiguration();
        if (configuration.getConnection() == null && configuration.getServers() == null
            && !CamelComponentOptions.isSet(environment, name, "connection", "servers")) {
            try {
                connections.find(null).ifPresent(configuration::setConnection);
            } catch (NonUniqueBeanException e) {
                // several connections and none is primary: the routes must choose
            }
        }
    }
}
