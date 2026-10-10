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
package io.micronaut.camel.json;

import io.micronaut.cache.SyncCache;
import io.micronaut.camel.cache.MicronautCacheAggregationRepository;
import io.micronaut.camel.jdbc.JsonJdbcCamelCodec;
import io.micronaut.camel.leveldb.JsonLevelDBSerializer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
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
import org.apache.camel.component.leveldb.LevelDBAggregationRepository;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aggregation repositories that store exchanges as JSON with the Micronaut JSON mapper.
 */
@MicronautTest
@Property(name = "spec.name", value = "AggregationRepositoriesTest")
@Property(name = "micronaut.caches.aggregations.maximum-size", value = "100")
class AggregationRepositoriesTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    @Named("jdbc")
    RecoverableAggregationRepository jdbc;

    @Inject
    @Named("cache")
    RecoverableAggregationRepository cache;

    @Inject
    @Named("leveldb")
    RecoverableAggregationRepository leveldb;

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "cache", "leveldb"})
    void aggregatesSerializableBodies(String repository) throws Exception {
        MockEndpoint aggregated = camelContext.getEndpoint("mock:" + repository, MockEndpoint.class);
        aggregated.expectedMessageCount(1);

        producerTemplate.sendBodyAndHeader("direct:" + repository, new Order("book", 1), "basket", "a");
        producerTemplate.sendBodyAndHeader("direct:" + repository, new Order("pen", 2), "basket", "a");

        aggregated.assertIsSatisfied();
        assertEquals(List.of(new Order("book", 1), new Order("pen", 2)), aggregated.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "cache", "leveldb"})
    void recoversCompletedExchanges(String name) {
        RecoverableAggregationRepository repository = switch (name) {
            case "jdbc" -> jdbc;
            case "cache" -> cache;
            default -> leveldb;
        };
        String key = UUID.randomUUID().toString();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Order("cup", 3));
        exchange.getMessage().setHeader("customer", "Fred");

        repository.add(camelContext, key, exchange);

        assertTrue(repository.getKeys().contains(key));
        Exchange stored = repository.get(camelContext, key);
        assertEquals(new Order("cup", 3), stored.getMessage().getBody());
        assertEquals("Fred", stored.getMessage().getHeader("customer"));

        repository.remove(camelContext, key, stored);

        assertNull(repository.get(camelContext, key));
        Set<String> completed = repository.scan(camelContext);
        assertTrue(completed.contains(stored.getExchangeId()));
        assertEquals(new Order("cup", 3), repository.recover(camelContext, stored.getExchangeId()).getMessage().getBody());

        repository.confirm(camelContext, stored.getExchangeId());

        assertTrue(!repository.scan(camelContext).contains(stored.getExchangeId()));
    }

    @Serdeable
    public record Order(String item, int quantity) {
    }

    @Factory
    @Requires(property = "spec.name", value = "AggregationRepositoriesTest")
    static class Repositories {

        @Singleton
        @Named("jdbc")
        @Bean(typed = {RecoverableAggregationRepository.class, AggregationRepository.class})
        JdbcAggregationRepository jdbc(JsonJdbcCamelCodec codec) throws SQLException {
            DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:aggregations;DB_CLOSE_DELAY=-1", "sa", "");
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String table : List.of("orders", "orders_completed")) {
                    statement.execute("CREATE TABLE IF NOT EXISTS " + table
                        + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BLOB NOT NULL, version BIGINT NOT NULL)");
                }
            }
            JdbcAggregationRepository repository = new JdbcAggregationRepository(new DataSourceTransactionManager(dataSource), "orders", dataSource);
            repository.setJdbcCamelCodec(codec);
            return repository;
        }

        @Singleton
        @Named("cache")
        @Bean(typed = {RecoverableAggregationRepository.class, AggregationRepository.class})
        MicronautCacheAggregationRepository cache(@Named("aggregations") SyncCache<?> cache, JsonExchangeCodec codec) {
            return new MicronautCacheAggregationRepository(cache, codec);
        }

        @Singleton
        @Named("leveldb")
        @Bean(typed = {RecoverableAggregationRepository.class, AggregationRepository.class})
        LevelDBAggregationRepository leveldb(JsonLevelDBSerializer serializer) {
            String file = Path.of("build", "tmp", "leveldb", UUID.randomUUID().toString()).toString();
            LevelDBAggregationRepository repository = new LevelDBAggregationRepository("orders", file);
            repository.setSerializer(serializer);
            return repository;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "AggregationRepositoriesTest")
    static class Routes extends RouteBuilder {

        private final RecoverableAggregationRepository jdbc;
        private final RecoverableAggregationRepository cache;
        private final RecoverableAggregationRepository leveldb;

        Routes(@Named("jdbc") RecoverableAggregationRepository jdbc,
               @Named("cache") RecoverableAggregationRepository cache,
               @Named("leveldb") RecoverableAggregationRepository leveldb) {
            this.jdbc = jdbc;
            this.cache = cache;
            this.leveldb = leveldb;
        }

        @Override
        public void configure() {
            aggregate("jdbc", jdbc);
            aggregate("cache", cache);
            aggregate("leveldb", leveldb);
        }

        private void aggregate(String name, AggregationRepository repository) {
            from("direct:" + name)
                .aggregate(header("basket"), new OrderList())
                .aggregationRepository(repository)
                .completionSize(2)
                .to("mock:" + name);
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
