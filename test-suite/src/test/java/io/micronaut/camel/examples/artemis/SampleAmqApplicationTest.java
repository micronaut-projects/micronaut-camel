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
package io.micronaut.camel.examples.artemis;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.activemq.ArtemisContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code artemis} example of camel-spring-boot-examples.
 * <p>
 * The route is a Micronaut singleton. Spring Boot's embedded Artemis broker ({@code spring.artemis.*})
 * became an Artemis container, and Micronaut JMS ({@code micronaut.jms.activemq.artemis.*}) creates the
 * connection factory, which micronaut-camel gives the {@code jms} component although Micronaut JMS
 * registers a connection pool as well. The queue name moved from
 * {@code spring.artemis.embedded.queue} to {@code artemis.queue}, and the file the route sends is copied
 * to a temporary {@code input} directory instead of being read from {@code src/main/data}. The test
 * checks that the message went through the queue instead of only that an exchange completed.
 * {@code camel.main.run-controller} was dropped: Micronaut owns the application lifecycle.
 */
@MicronautTest(environments = "example-artemis")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleAmqApplicationTest implements TestPropertyProvider {

    private final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:latest");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        artemis.start();
        return Map.of(
            "input", copyData().toString(),
            "micronaut.jms.activemq.artemis.connection-string", artemis.getBrokerUrl(),
            "micronaut.jms.activemq.artemis.username", artemis.getUser(),
            "micronaut.jms.activemq.artemis.password", artemis.getPassword()
        );
    }

    @AfterAll
    void stopArtemis() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
        artemis.stop();
    }

    @Test
    void shouldProduceMessages() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).from("jms:*").whenDone(1)
            .and().whenAnyDoneMatches(body().convertToString().contains("The APACHE Camel riders!"))
            .create();

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
    }

    private static Path copyData() {
        try (InputStream readme = SampleAmqApplicationTest.class.getResourceAsStream("/examples/artemis/data/readme.adoc")) {
            Path input = Files.createTempDirectory("camel-artemis-example");
            Files.copy(readme, input.resolve("readme.adoc"));
            return input;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
