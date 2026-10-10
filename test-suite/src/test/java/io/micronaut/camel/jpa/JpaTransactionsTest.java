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
package io.micronaut.camel.jpa;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks the setup the guide describes for the {@code jpa} component: Micronaut transactions run on Spring's
 * {@code HibernateTransactionManager} (micronaut-data-spring-jpa) and the endpoints use a shared entity manager, so
 * that they join the transactions of transacted routes and of Micronaut code.
 */
@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "JpaTransactionsTest")
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:jpa;DB_CLOSE_DELAY=-1")
@Property(name = "datasources.default.transaction-manager", value = "springHibernate")
@Property(name = "jpa.default.entity-scan.packages", value = "io.micronaut.camel.jpa")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class JpaTransactionsTest {

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    JdbcTemplate jdbcTemplate;

    @Inject
    TransactionOperations<?> transactionOperations;

    @BeforeEach
    void deleteHorses() {
        jdbcTemplate.update("delete from horse");
    }

    @Test
    void successfulRouteCommits() {
        producerTemplate.sendBody("direct:jpa-add", new Horse("Varenne"));

        assertEquals(1, horses());
    }

    @Test
    void failingRouteRollsBack() {
        assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeader("direct:jpa-add", new Horse("Seabiscuit"), "fail", true));

        assertEquals(0, horses());
    }

    @Test
    void routeJoinsTheTransactionOfTheCaller() {
        transactionOperations.executeWrite(status -> {
            producerTemplate.sendBody("direct:jpa-add", new Horse("Varenne"));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(0, horses());
    }

    @Test
    void endpointsWorkOutsideTransactions() {
        producerTemplate.sendBody("direct:jpa-save", new Horse("Varenne"));

        assertEquals(1, horses());
    }

    private int horses() {
        return jdbcTemplate.queryForObject("select count(*) from horse", Integer.class);
    }

    @Singleton
    @Requires(property = "spec.name", value = "JpaTransactionsTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:jpa-add")
                .transacted()
                .to("jpa:" + Horse.class.getName() + "?sharedEntityManager=true")
                .filter(header("fail"))
                    .throwException(new IllegalStateException("Forced failure"));

            from("direct:jpa-save")
                .to("jpa:" + Horse.class.getName() + "?sharedEntityManager=true");
        }
    }
}
