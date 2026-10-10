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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Selects the {@link ConnectionFactory} bean of a Camel JMS component.
 *
 * <p>Micronaut JMS registers two connection factories per broker: the factory of the JMS client and
 * a {@link JMSConnectionPool} that decorates it, both named after the broker, such as
 * {@code activeMqArtemisConnectionFactory}. Camel only autowires a connection factory when there is
 * exactly one, so the components are given the client's factory: Spring JMS, which camel-jms is built
 * on, manages connections and sessions itself, whereas the pool hands out shared, already started
 * connections and fails when it is exhausted. {@code micronaut.camel.jms.pooled=true} selects the
 * pool instead, and {@code micronaut.camel.jms.connection-factory} selects a broker by name when
 * there are several.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = {ConnectionFactory.class, JMSConnectionPool.class})
@Requires(property = JmsConnectionFactories.ENABLED, notEquals = StringUtils.FALSE)
final class JmsConnectionFactories {

    /**
     * The property that disables the integration with Micronaut JMS when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.jms.enabled";

    private static final Logger LOG = LoggerFactory.getLogger(JmsConnectionFactories.class);

    private final BeanContext beanContext;
    private final @Nullable String name;
    private final boolean pooled;

    /**
     * @param beanContext The bean context
     * @param name        The name of the connection factory to use, when there are several
     * @param pooled      Whether to use the connection pool of Micronaut JMS
     */
    JmsConnectionFactories(BeanContext beanContext,
                           @Property(name = "micronaut.camel.jms.connection-factory") @Nullable String name,
                           @Property(name = "micronaut.camel.jms.pooled", defaultValue = StringUtils.FALSE) boolean pooled) {
        this.beanContext = beanContext;
        this.name = name;
        this.pooled = pooled;
    }

    /**
     * Selects a connection factory of Micronaut JMS: the factory of the JMS client, or its pool when
     * {@code micronaut.camel.jms.pooled} is {@code true}, of the broker named by
     * {@code micronaut.camel.jms.connection-factory} when it is set.
     *
     * @return The connection factory, or {@code null} when none or several match
     */
    @Nullable ConnectionFactory select() {
        Collection<BeanDefinition<ConnectionFactory>> candidates = name == null
            ? beanContext.getBeanDefinitions(ConnectionFactory.class)
            : beanContext.getBeanDefinitions(ConnectionFactory.class, Qualifiers.byName(name));
        return single(ConnectionFactory.class, candidates.stream()
            .filter(definition -> JMSConnectionPool.class.isAssignableFrom(definition.getBeanType()) == pooled)
            .toList());
    }

    /**
     * Selects the only connection factory of a type, or the primary one when there are several.
     *
     * @param type The type of connection factory
     * @param <T>  The type of connection factory
     * @return The connection factory, or {@code null} when none or several match
     */
    <T extends ConnectionFactory> @Nullable T select(Class<T> type) {
        return single(type, List.copyOf(beanContext.getBeanDefinitions(type)));
    }

    private <T> @Nullable T single(Class<T> type, List<BeanDefinition<T>> candidates) {
        List<BeanDefinition<T>> matching = candidates;
        if (matching.size() > 1) {
            matching = matching.stream().filter(BeanDefinition::isPrimary).toList();
        }
        if (matching.size() == 1) {
            return beanContext.getBean(matching.get(0));
        }
        if (!candidates.isEmpty()) {
            LOG.debug("Cannot select a {} for Camel among {} beans; set micronaut.camel.jms.connection-factory", type.getSimpleName(), candidates.size());
        }
        return null;
    }
}
