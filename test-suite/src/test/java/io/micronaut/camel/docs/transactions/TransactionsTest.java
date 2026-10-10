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
package io.micronaut.camel.docs.transactions;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
class TransactionsTest {

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    JdbcTemplate jdbcTemplate;

    @Inject
    TransactionOperations<?> transactionOperations;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("delete from horses");
        jdbcTemplate.update("delete from audit");
        jdbcTemplate.update("delete from orders");
        jdbcTemplate.update("delete from CAMEL_MESSAGEPROCESSED");
    }

    @Test
    void successfulRouteCommits() {
        producerTemplate.sendBodyAndHeaders("direct:add-horse", null, Map.of("name", "Varenne", "age", 8));

        assertEquals(List.of("Varenne"), horses());
        assertEquals(List.of("Added Varenne"), names("audit"));
        assertEquals(1, producerTemplate.requestBody("direct:count-horses", null, Integer.class));
    }

    @Test
    void rollbackKeepsTheNewTransaction() {
        assertThrows(CamelExecutionException.class, () -> producerTemplate.sendBodyAndHeaders("direct:add-horse", null,
            Map.of("name", "Seabiscuit", "age", 9, "fail", true)));

        assertEquals(List.of(), horses());
        assertEquals(List.of("Added Seabiscuit"), names("audit"));
    }

    @Test
    void failingSplitRollsBackEverything() {
        assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody("direct:add-horses", List.of("Varenne", "Varenne")));

        assertEquals(List.of(), horses());
    }

    @Test
    void routeJoinsTheTransactionOfTheCaller() {
        transactionOperations.executeWrite(status -> {
            producerTemplate.sendBodyAndHeaders("direct:add-horse", null, Map.of("name", "Varenne", "age", 8));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(List.of(), horses());
    }

    @Test
    void failedOrdersAreNotRememberedAsProcessed() {
        assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeaders("direct:order", null, Map.of("orderId", "A1", "fail", true)));
        assertEquals(List.of(), names("orders"));

        producerTemplate.sendBodyAndHeader("direct:order", null, "orderId", "A1");
        producerTemplate.sendBodyAndHeader("direct:order", null, "orderId", "A1");

        assertEquals(List.of("A1"), names("orders"));
    }

    private List<String> horses() {
        return jdbcTemplate.queryForList("select name from horses", String.class);
    }

    private List<String> names(String table) {
        return jdbcTemplate.queryForList("select * from " + table, String.class);
    }
}
