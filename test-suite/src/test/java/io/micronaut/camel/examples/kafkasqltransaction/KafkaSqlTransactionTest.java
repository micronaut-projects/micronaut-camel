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
package io.micronaut.camel.examples.kafkasqltransaction;

import io.micronaut.camel.examples.Containers;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code kafka-sql-transaction} example of camel-spring-boot-examples.
 * <p>
 * The routes start from {@code direct} endpoints instead of {@code platform-http}, as this branch has no Camel HTTP
 * support, and the test sends the {@code word} header with a {@code ProducerTemplate}. Kafka and PostgreSQL are started
 * by Testcontainers instead of {@code camel infra run}, and the container's init script replaces Spring Boot's
 * {@code spring.sql.init}. The {@code sql} component gets the Micronaut data source, outside of any transaction, as the
 * routes rely on Kafka's local transactions only. The original only describes how to watch the topic with a
 * {@code read_committed} console consumer; the test does it with a route instead, and checks the table. The logging
 * settings were dropped. The test is skipped without a container runtime.
 */
@MicronautTest(environments = "example-kafka-sql-transaction", transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaSqlTransactionTest implements TestPropertyProvider {

    private KafkaContainer kafka;
    private PostgreSQLContainer postgres;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public Map<String, String> getProperties() {
        kafka = new KafkaContainer("apache/kafka:4.2.0");
        postgres = new PostgreSQLContainer(DockerImageName.parse("mirror.gcr.io/library/postgres:17-alpine").asCompatibleSubstituteFor("postgres"))
            .withInitScript("examples/kafka-sql-transaction/schema.sql");
        Containers.start(kafka, postgres);
        return Map.of(
            "camel.component.kafka.brokers", kafka.getBootstrapServers(),
            "datasources.default.url", postgres.getJdbcUrl(),
            "datasources.default.username", postgres.getUsername(),
            "datasources.default.password", postgres.getPassword()
        );
    }

    @AfterAll
    void stopContainers() {
        postgres.stop();
        kafka.stop();
    }

    @Test
    void kafkaTransactionsRollBackWhenTheInsertFails() throws Exception {
        MockEndpoint topic = camelContext.getEndpoint("mock:foo", MockEndpoint.class);
        topic.setResultWaitTime(TimeUnit.SECONDS.toMillis(60));
        topic.setAssertPeriod(TimeUnit.SECONDS.toMillis(2));
        // the message of the rolled back Kafka transaction is never committed
        topic.expectedBodiesReceived("{\"foo\": \"a\"}", "{\"foo\": \"b\"}", "{\"foo\": \"b\"}");

        // the row and the message are added
        assertNull(send("direct:send", "a").getException());
        // the insert of a duplicate fails, so the Kafka transaction is rolled back
        Exchange duplicate = send("direct:sendtx2", "a");
        assertTrue(duplicate.isRollbackOnly());
        // the transacted message is committed with the row
        assertNull(send("direct:sendtx2", "b").getException());
        // without a Kafka transaction a message is sent even though the insert fails
        assertNotNull(send("direct:send2", "b").getException());

        topic.assertIsSatisfied();
        List<?> rows = producerTemplate.requestBody("sql:select name from foo order by name", null, List.class);
        assertEquals(List.of("a", "b"), rows.stream().map(row -> ((Map<?, ?>) row).get("name")).toList());
    }

    private Exchange send(String uri, String word) {
        return producerTemplate.send(uri, exchange -> exchange.getMessage().setHeader("word", word));
    }

    /**
     * Follows the messages committed to the topic, like the console consumer of the original.
     */
    @Singleton
    @Requires(env = "example-kafka-sql-transaction")
    static class CommittedMessages extends RouteBuilder {

        @Override
        public void configure() {
            from("kafka:foo?groupId=committed&autoOffsetReset=earliest&isolationLevel=read_committed")
                .to("mock:foo");
        }
    }
}
