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
package io.micronaut.camel.graal;

import io.micronaut.camel.jdbc.JsonJdbcCamelCodec;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The sql component and camel-sql's aggregation repository on an H2 data source of micronaut-jdbc-hikari.
 */
@MicronautTest(environments = "sql")
class NativeSqlTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void sqlRoutesQueryTheDatabase() {
        producerTemplate.sendBody("direct:add-book", Map.of("title", "Camel", "pages", 100));

        List<?> books = producerTemplate.requestBody("direct:books", null, List.class);

        assertEquals(List.of(Map.of("TITLE", "Camel", "PAGES", 100)), books);
    }

    @Test
    void aggregationRepositoriesUseJavaSerialization() throws Exception {
        MockEndpoint aggregated = camelContext.getEndpoint("mock:serialized", MockEndpoint.class);
        aggregated.expectedBodiesReceived("a,b");
        aggregated.expectedHeaderReceived("count", 2);

        producerTemplate.sendBodyAndHeader("direct:serialized", "a", "count", 1);
        producerTemplate.sendBodyAndHeader("direct:serialized", "b", "count", 2);

        aggregated.assertIsSatisfied();
    }

    @Test
    void aggregationRepositoriesUseMicronautSerialization() throws Exception {
        MockEndpoint aggregated = camelContext.getEndpoint("mock:json", MockEndpoint.class);
        aggregated.expectedMessageCount(1);

        producerTemplate.sendBody("direct:json", new Order("book", 1));
        producerTemplate.sendBody("direct:json", new Order("pen", 2));

        aggregated.assertIsSatisfied();
        assertEquals(List.of(new Order("book", 1), new Order("pen", 2)), aggregated.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @Serdeable
    public record Order(String item, int quantity) {
    }

    @Singleton
    @Requires(env = "sql")
    public static class Schema {
        public Schema(DataSource dataSource) throws SQLException {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS books (title VARCHAR(255), pages INT)");
                for (String table : List.of("serialized", "serialized_completed", "json", "json_completed")) {
                    statement.execute("CREATE TABLE IF NOT EXISTS " + table
                        + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BLOB NOT NULL, version BIGINT NOT NULL)");
                }
            }
        }
    }

    @Factory
    @Requires(env = "sql")
    public static class Repositories {

        @Singleton
        @Named("serialized")
        @Bean(typed = AggregationRepository.class)
        JdbcAggregationRepository serialized(DataSource dataSource, Schema schema) {
            return new JdbcAggregationRepository(new DataSourceTransactionManager(dataSource), "serialized", dataSource);
        }

        @Singleton
        @Named("json")
        @Bean(typed = AggregationRepository.class)
        JdbcAggregationRepository json(DataSource dataSource, Schema schema, JsonJdbcCamelCodec codec) {
            JdbcAggregationRepository repository = new JdbcAggregationRepository(new DataSourceTransactionManager(dataSource), "json", dataSource);
            repository.setJdbcCamelCodec(codec);
            return repository;
        }
    }

    @Singleton
    @Requires(env = "sql")
    public static class SqlRoutes extends RouteBuilder {

        private final AggregationRepository serialized;
        private final AggregationRepository json;

        public SqlRoutes(Schema schema,
                         @Named("serialized") AggregationRepository serialized,
                         @Named("json") AggregationRepository json) {
            this.serialized = serialized;
            this.json = json;
        }

        @Override
        public void configure() {
            from("direct:add-book").to("sql:insert into books (title, pages) values (:#title, :#pages)");

            from("direct:books").to("sql:select title, pages from books");

            from("direct:serialized")
                .aggregate(constant("all"), (oldExchange, newExchange) -> {
                    if (oldExchange == null) {
                        return newExchange;
                    }
                    oldExchange.getMessage().setBody(oldExchange.getMessage().getBody(String.class) + "," + newExchange.getMessage().getBody(String.class));
                    oldExchange.getMessage().setHeader("count", newExchange.getMessage().getHeader("count"));
                    return oldExchange;
                })
                .aggregationRepository(serialized)
                .completionSize(2)
                .to("mock:serialized");

            from("direct:json")
                .aggregate(constant("all"), new OrderList())
                .aggregationRepository(json)
                .completionSize(2)
                .to("mock:json");
        }
    }

    static class OrderList implements AggregationStrategy {
        @Override
        @SuppressWarnings("unchecked")
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Order order = newExchange.getMessage().getBody(Order.class);
            if (oldExchange == null) {
                List<Order> orders = new ArrayList<>();
                orders.add(order);
                newExchange.getMessage().setBody(orders);
                return newExchange;
            }
            oldExchange.getMessage().getBody(List.class).add(order);
            return oldExchange;
        }
    }
}
