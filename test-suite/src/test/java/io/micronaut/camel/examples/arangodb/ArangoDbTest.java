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
package io.micronaut.camel.examples.arangodb;

import com.arangodb.ArangoDB;
import com.arangodb.entity.BaseDocument;
import io.micronaut.camel.examples.Containers;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code arangodb} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The route and the bean are Micronaut singletons, and the {@code arangodb} component is configured with the
 * {@code camel.component.arangodb.*} options of {@code application-example-arangodb.yml}. The ArangoDB server,
 * which the example starts with {@code camel infra run arangodb}, is started by Testcontainers, and the test
 * creates the {@code myDatabase} database and its {@code myCollection} collection before the application starts,
 * as the component does not create them. The test is skipped without a container runtime. It checks that the
 * route reads back the document it saved, and that the document is in ArangoDB.
 * {@code camel.main.run-controller} was dropped: Micronaut owns the application lifecycle.
 */
@MicronautTest(environments = "example-arangodb")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArangoDbTest implements TestPropertyProvider {

    private static final int ARANGODB_PORT = 8529;
    private static final String PASSWORD = "password";

    private GenericContainer<?> arangodb;
    private ArangoDB client;

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        arangodb = new GenericContainer<>("arangodb/arangodb:3.12")
            .withEnv("ARANGO_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(ARANGODB_PORT)
            .waitingFor(Wait.forLogMessage(".*is ready for business.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
        Containers.start(arangodb);
        client = new ArangoDB.Builder()
            .host(arangodb.getHost(), arangodb.getMappedPort(ARANGODB_PORT))
            .user("root")
            .password(PASSWORD)
            .build();
        client.createDatabase("myDatabase");
        client.db("myDatabase").createCollection("myCollection");
        return Map.of(
            "camel.component.arangodb.host", arangodb.getHost(),
            "camel.component.arangodb.port", String.valueOf(arangodb.getMappedPort(ARANGODB_PORT)));
    }

    @AfterAll
    void stopArangoDb() {
        client.shutdown();
        arangodb.stop();
    }

    @Test
    void savesAndReadsTheDocument() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("arangodb")
            .whenAnyDoneMatches(exchange -> {
                BaseDocument document = exchange.getMessage().getBody(BaseDocument.class);
                return document != null && "bar".equals(document.getAttribute("foo"));
            })
            .create();

        assertTrue(notify.matches(20, TimeUnit.SECONDS));
        BaseDocument saved = client.db("myDatabase").collection("myCollection").getDocument(MyBeanService.KEY, BaseDocument.class);
        assertEquals("bar", saved.getAttribute("foo"));
    }
}
