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
package io.micronaut.camel.examples.springjdbc;

import io.micronaut.camel.examples.Containers;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RollbackExchangeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ports the {@code spring-jdbc} example of camel-spring-boot-examples.
 * <p>
 * The routes use the {@code sql} component instead of {@code spring-jdbc}: {@code transacted()} runs them in a
 * Micronaut transaction, which runs on Spring's {@code DataSourceTransactionManager} as the data source sets
 * {@code transaction-manager: springJdbc} (micronaut-data-spring-jdbc), and the {@code sql} component joins it. The
 * insert uses the {@code name} and {@code age} headers as query parameters instead of building the SQL with Simple.
 * <p>
 * The REST DSL and the servlet were dropped, as this branch has no Camel HTTP support: the test sends to the
 * {@code direct:persist} and {@code direct:read} routes that the REST endpoints called, with a {@code ProducerTemplate}.
 * As in the original, the database is PostgreSQL, started by Testcontainers instead of by hand, and the container's
 * init script replaces Spring Boot's {@code spring.sql.init}. The test is skipped without a container runtime.
 */
@MicronautTest(environments = "example-spring-jdbc", transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringJdbcTest implements TestPropertyProvider {

    private PostgreSQLContainer postgres;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public Map<String, String> getProperties() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("mirror.gcr.io/library/postgres:17-alpine").asCompatibleSubstituteFor("postgres"))
            .withInitScript("examples/spring-jdbc/schema.sql");
        Containers.start(postgres);
        return Map.of(
            "datasources.default.url", postgres.getJdbcUrl(),
            "datasources.default.username", postgres.getUsername(),
            "datasources.default.password", postgres.getPassword()
        );
    }

    @AfterAll
    void stopPostgres() {
        postgres.stop();
    }

    @Test
    void shouldCommitAndRollback() {
        // 1. verify the table is empty
        assertEquals(List.of(), horses());

        // 2. insert data
        assertEquals("executed", producerTemplate.requestBodyAndHeaders("direct:persist", null, Map.of("name", "Varenne", "age", 8), String.class));

        // 3. verify the data has been persisted
        assertEquals(List.of("Varenne"), horses());

        // 4. force to rollback after the insert (setting header "fail: true")
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> producerTemplate.requestBodyAndHeaders(
            "direct:persist", null, Map.of("name", "Seabiscuit", "age", 9, "fail", "true")));
        assertInstanceOf(RollbackExchangeException.class, e.getCause());

        // 5. verify the same content of the step 3
        assertEquals(List.of("Varenne"), horses());
    }

    private List<String> horses() {
        List<?> rows = producerTemplate.requestBody("direct:read", null, List.class);
        return rows.stream().map(row -> (String) ((Map<?, ?>) row).get("name")).toList();
    }
}
