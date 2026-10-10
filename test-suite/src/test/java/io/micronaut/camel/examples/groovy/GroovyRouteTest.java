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
package io.micronaut.camel.examples.groovy;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code groovy} example of camel-spring-boot-examples, which has no test.
 * <p>
 * There is no Java code to port: the YAML route and the Groovy sources moved to {@code examples/groovy},
 * and the configuration points {@code camel.main.routes-include-pattern} and
 * {@code camel.main.groovy-script-pattern} at them. The directory the route reads orders from became the
 * {@code orders.dir} placeholder (defaulting to the original {@code data}) so the test can use a temporary
 * directory, into which it copies the example's {@code order.json} and a second, gold, order.
 * {@code camel.main.run-controller} was dropped: Micronaut keeps the application running.
 */
@MicronautTest(environments = "example-groovy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroovyRouteTest implements TestPropertyProvider {

    private final Path ordersDir = createTempDirectory();

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        return Map.of("orders.dir", ordersDir.toString());
    }

    @Test
    void ordersAreEnrichedByGroovy() throws IOException {
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .whenAnyDoneMatches(body().convertToString().isEqualTo("Order 432 from Acme (Silver)"))
            .and().whenAnyDoneMatches(body().convertToString().isEqualTo("Order 1234 from Globex (Gold)"))
            .create();

        try (InputStream order = getClass().getResourceAsStream("/examples/groovy/data/order.json")) {
            Files.copy(order, ordersDir.resolve("order.json"));
        }
        Files.writeString(ordersDir.resolve("gold-order.json"), """
            {
              "id": 1234,
              "name": "Globex"
            }
            """);

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }

    @AfterAll
    void deleteOrders() throws IOException {
        try (Stream<Path> files = Files.walk(ordersDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("camel-groovy-example");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
