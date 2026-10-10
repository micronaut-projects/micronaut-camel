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
package io.micronaut.camel.transaction;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.spi.TransactedPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "TransactedRouteTest")
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:transactedRoutes;DB_CLOSE_DELAY=-1;INIT=CREATE TABLE IF NOT EXISTS horses(name VARCHAR(255))")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
class TransactedRouteTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    DataSource dataSource;

    @Inject
    TransactionOperations<Connection> transactionOperations;

    @BeforeEach
    void deleteHorses() throws SQLException {
        execute("delete from horses");
    }

    @Test
    void transactedUsesTheRequiredPolicyByDefault() {
        TransactedPolicy policy = camelContext.getRegistry().findSingleByType(TransactedPolicy.class);
        assertInstanceOf(MicronautTransactionPolicy.class, policy);
        assertSame(policy, camelContext.getRegistry().lookupByNameAndType("PROPAGATION_REQUIRED", TransactedPolicy.class));
    }

    @Test
    void camelGetsADataSourceThatWorksOutsideTransactions() {
        DataSource camelDataSource = camelContext.getComponent("sql", SqlComponent.class).getDataSource();
        assertInstanceOf(DelegatingDataSource.class, camelDataSource);
        assertSame(DelegatingDataSource.unwrapDataSource(dataSource), DelegatingDataSource.unwrapDataSource(camelDataSource));

        producerTemplate.sendBody("direct:insert", "Varenne");

        assertEquals(List.of("Varenne"), horses());
        assertEquals(1, producerTemplate.requestBody("direct:count", null, Integer.class));
    }

    @Test
    void successfulRouteCommits() {
        producerTemplate.sendBody("direct:transacted", "Varenne");

        assertEquals(List.of("Varenne"), horses());
    }

    @Test
    void failingRouteRollsBack() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeader("direct:transacted", "Seabiscuit", "fail", true));

        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals(List.of(), horses());
    }

    @Test
    void rollbackRollsBack() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:rollback", "Seabiscuit"));

        assertInstanceOf(RollbackExchangeException.class, e.getCause());
        assertEquals(List.of(), horses());
    }

    @Test
    void markRollbackOnlyRollsBackWithoutAnError() {
        producerTemplate.sendBody("direct:mark-rollback-only", "Seabiscuit");

        assertEquals(List.of(), horses());
    }

    @Test
    void handledExceptionCommits() {
        assertEquals("handled", producerTemplate.requestBody("direct:handled", "Varenne", String.class));

        assertEquals(List.of("Varenne"), horses());
    }

    @Test
    void requiresNewCommitsIndependently() {
        assertThrows(CamelExecutionException.class, () -> producerTemplate.sendBody("direct:outer", "Outer"));

        assertEquals(List.of("Inner"), horses());
    }

    @Test
    void routeJoinsTheCallersTransaction() {
        transactionOperations.executeWrite(status -> {
            producerTemplate.sendBody("direct:transacted", "Varenne");
            assertEquals(List.of("Varenne"), producerTemplate.requestBody("direct:names", null, List.class));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(List.of(), horses());
    }

    @Test
    void mandatoryRequiresATransaction() {
        assertThrows(CamelExecutionException.class, () -> producerTemplate.sendBody("direct:mandatory", "Varenne"));
        assertEquals(List.of(), horses());

        transactionOperations.executeWrite(status -> producerTemplate.requestBody("direct:mandatory", "Varenne"));
        assertEquals(List.of("Varenne"), horses());
    }

    @Test
    void jdbcComponentJoinsTheTransaction() {
        producerTemplate.sendBodyAndHeader("direct:jdbc", "insert into horses(name) values (:?name)", "name", "Varenne");
        assertThrows(CamelExecutionException.class, () -> producerTemplate.sendBodyAndHeaders("direct:jdbc",
            "insert into horses(name) values (:?name)", Map.of("name", "Seabiscuit", "fail", true)));

        assertEquals(List.of("Varenne"), horses());
    }

    @Test
    void configuredErrorHandlerRedeliversInTheTransaction() {
        RedeliveringRoutes.ATTEMPTS.set(0);

        producerTemplate.sendBody("direct:redelivered", "Varenne");

        assertEquals(3, RedeliveringRoutes.ATTEMPTS.get());
        assertEquals(List.of("Varenne"), horses());
    }

    private List<String> horses() {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select name from horses order by name")) {
            List<String> names = new ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
            return names;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "TransactedRouteTest")
    static class RedeliveringRoutes extends RouteBuilder {

        static final AtomicInteger ATTEMPTS = new AtomicInteger();

        @Override
        public void configure() {
            errorHandler(new MicronautTransactionErrorHandlerDefinition()
                .rollbackLoggingLevel(LoggingLevel.INFO)
                .maximumRedeliveries(2)
                .redeliveryDelay(0));

            from("direct:redelivered")
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .process(exchange -> {
                    if (ATTEMPTS.incrementAndGet() < 3) {
                        throw new IllegalStateException("Attempt " + ATTEMPTS.get());
                    }
                });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "TransactedRouteTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:insert")
                .to("sql:insert into horses(name) values (:#${body})");

            from("direct:count")
                .to("sql:select count(*) from horses?outputType=SelectOne");

            from("direct:names")
                .to("sql:select name from horses?outputType=SelectList")
                .process(exchange -> {
                    List<?> rows = exchange.getMessage().getBody(List.class);
                    exchange.getMessage().setBody(rows.stream().map(row -> ((Map<?, ?>) row).get("NAME")).toList());
                });

            from("direct:transacted")
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .filter(header("fail"))
                    .throwException(new IllegalStateException("Forced failure"));

            from("direct:rollback")
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .rollback("Forced rollback");

            from("direct:mark-rollback-only")
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .markRollbackOnly();

            from("direct:handled")
                .onException(IllegalStateException.class)
                    .handled(true)
                    .setBody(constant("handled"))
                .end()
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .throwException(new IllegalStateException("Handled failure"));

            from("direct:outer")
                .transacted()
                .to("sql:insert into horses(name) values (:#${body})")
                .setBody(constant("Inner"))
                .to("direct:inner")
                .throwException(new IllegalStateException("Outer failure"));

            from("direct:inner")
                .transacted("PROPAGATION_REQUIRES_NEW")
                .to("sql:insert into horses(name) values (:#${body})");

            from("direct:jdbc")
                .transacted()
                .to("jdbc:default?useHeadersAsParameters=true&resetAutoCommit=false")
                .filter(header("fail"))
                    .throwException(new IllegalStateException("Forced failure"));

            from("direct:mandatory")
                .transacted("PROPAGATION_MANDATORY")
                .to("sql:insert into horses(name) values (:#${body})");
        }
    }
}
