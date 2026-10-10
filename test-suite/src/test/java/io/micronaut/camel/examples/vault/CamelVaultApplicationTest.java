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
package io.micronaut.camel.examples.vault;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.vault.VaultContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ports the {@code vault} example of camel-spring-boot-examples.
 * <p>
 * The YAML routes and REST services are unchanged, and served by micronaut-camel-http. Camel's HashiCorp
 * Vault support is configured from the Micronaut Vault client configuration ({@code vault.client.uri} and
 * {@code vault.client.token}) instead of {@code camel.vault.hashicorp.*}. The {@code datasource} bean of
 * the YAML is the PostgreSQL driver's {@code PGSimpleDataSource} instead of Spring's
 * {@code DriverManagerDataSource}, the REST services turn off the JSON binding that the guide samples of this
 * test suite configure, and the cars are written as JSON with the {@code micronaut-json} data
 * format instead of camel-jackson. The example has no test: this one runs Vault and PostgreSQL with
 * Testcontainers, stores the {@code database} secret of its README in Vault, and makes the requests of
 * its README.
 */
@MicronautTest(environments = "example-vault")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CamelVaultApplicationTest implements TestPropertyProvider {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.16.1").withVaultToken("myroot");

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public Map<String, String> getProperties() {
        POSTGRES.start();
        VAULT.withInitCommand("kv put secret/database myJdbcURL=" + POSTGRES.getJdbcUrl()
            + " myUsername=" + POSTGRES.getUsername() + " myPassword=" + POSTGRES.getPassword());
        VAULT.start();
        return Map.of(
            "vault.client.uri", VAULT.getHttpHostAddress(),
            "vault.client.token", "myroot");
    }

    @AfterAll
    void stopContainers() {
        VAULT.stop();
        POSTGRES.stop();
    }

    @Test
    void carsArePersisted() throws Exception {
        // the table is created by a timer route when Camel starts
        assertEquals("[]", awaitCars());

        client.toBlocking().exchange(HttpRequest.POST("/api/cars", "{\"brand\": \"Ford\", \"model\": \"Mustang\", \"year\": 2024}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));

        assertEquals("[{\"brand\":\"Ford\",\"model\":\"Mustang\",\"year\":2024}]", awaitCars());
    }

    private String awaitCars() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (true) {
            try {
                return client.toBlocking().retrieve(HttpRequest.GET("/api/cars"));
            } catch (HttpClientResponseException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                Thread.sleep(200);
            }
        }
    }
}
