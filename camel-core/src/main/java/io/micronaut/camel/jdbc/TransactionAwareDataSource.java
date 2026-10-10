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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link DataSource} for Camel that returns the connection of the current Micronaut transaction
 * or connection when there is one, and a new connection from the target data source otherwise.
 *
 * <p>Closing the connection of a transaction does nothing, as the transaction closes it once it
 * completes.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class TransactionAwareDataSource extends DelegatingDataSource {

    private final Supplier<Optional<ConnectionOperations<Connection>>> connectionOperations;

    /**
     * @param targetDataSource             The data source that opens new connections
     * @param connectionOperationsSupplier Finds the connection operations of the data source
     */
    TransactionAwareDataSource(DataSource targetDataSource,
                               Supplier<Optional<ConnectionOperations<Connection>>> connectionOperationsSupplier) {
        super(targetDataSource);
        this.connectionOperations = SupplierUtil.memoized(connectionOperationsSupplier);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Optional<ConnectionStatus<Connection>> status = connectionOperations.get().flatMap(ConnectionOperations::findConnectionStatus);
        if (status.isPresent()) {
            return currentConnection(status.get().getConnection());
        }
        return getTargetDataSource().getConnection();
    }

    @Override
    public String toString() {
        return "TransactionAwareDataSource[" + getTargetDataSource() + "]";
    }

    private static Connection currentConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
            TransactionAwareDataSource.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            new CurrentConnectionHandler(connection)
        );
    }

    /**
     * Delegates to the current connection, except for {@link Connection#close()}.
     *
     * @param connection The connection of the current transaction or connection
     */
    private record CurrentConnectionHandler(Connection connection) implements InvocationHandler {

        @Override
        public @Nullable Object invoke(Object proxy, Method method, @Nullable Object @Nullable [] args) throws Throwable {
            switch (method.getName()) {
                case "close":
                    return null;
                case "equals":
                    return args != null && proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "Current connection [" + connection + "]";
                default:
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
            }
        }
    }
}
