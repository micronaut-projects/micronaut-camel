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
package io.micronaut.camel.jdbc;

import io.micronaut.camel.registry.CamelBeanAdapter;
import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives Camel a {@link TransactionAwareDataSource} instead of a {@link DataSource} bean wrapped by
 * Micronaut Data.
 *
 * <p>Micronaut Data wraps each {@link DataSource} bean so that {@link DataSource#getConnection()}
 * returns the connection of the current Micronaut transaction or connection, and fails outside of
 * one. Camel components such as {@code sql} and {@code jdbc} open connections themselves, for
 * example when a consumer polls, so Camel gets a data source that returns the current connection
 * when there is one and a new connection from the pool otherwise.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
@Requires(classes = {DelegatingDataSource.class, ConnectionOperations.class})
final class CamelDataSourceAdapter implements CamelBeanAdapter {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Argument<ConnectionOperations<Connection>> CONNECTION_OPERATIONS =
        (Argument) Argument.of(ConnectionOperations.class, Connection.class);

    private final BeanContext beanContext;
    private final Map<DataSource, DataSource> dataSources = new ConcurrentHashMap<>();

    /**
     * @param beanContext The bean context
     */
    CamelDataSourceAdapter(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Object adapt(Object bean) {
        if (bean instanceof DelegatingDataSource dataSource && !(bean instanceof TransactionAwareDataSource)) {
            return dataSources.computeIfAbsent(dataSource, this::transactionAware);
        }
        return bean;
    }

    private DataSource transactionAware(DataSource dataSource) {
        String name = beanContext.findBeanRegistration(dataSource)
            .map(BeanRegistration::getIdentifier)
            .map(BeanIdentifier::getName)
            .filter(n -> !n.equalsIgnoreCase("primary"))
            .orElse("default");
        return new TransactionAwareDataSource(
            DelegatingDataSource.unwrapDataSource(dataSource),
            () -> findConnectionOperations(name)
        );
    }

    private Optional<ConnectionOperations<Connection>> findConnectionOperations(String name) {
        return beanContext.findBean(CONNECTION_OPERATIONS, Qualifiers.byName(name));
    }
}
