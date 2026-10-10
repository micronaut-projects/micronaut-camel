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
package io.micronaut.camel.examples.widgetgadget;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.ConsumerTemplate;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code widget-gadget} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The routes are Micronaut singletons and the Qpid JMS connection factory is a bean of a Micronaut
 * {@code @Factory}; the unused {@code @Autowired} connection factory fields of the routes were dropped. The
 * {@code AMQP_REMOTE_URI}, {@code AMQP_SERVICE_USERNAME} and {@code AMQP_SERVICE_PASSWORD} properties
 * became {@code amqp.remote-uri}, {@code amqp.service.username} and {@code amqp.service.password}, which
 * the same environment variables still set. The AMQP 1.0 broker is an Artemis container instead of a
 * broker on localhost, and the orders are copied to a temporary {@code input} directory instead of being
 * read from {@code src/main/data}. The test checks that each order reaches the queue of its product.
 */
@MicronautTest(environments = "example-widget-gadget")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WidgetGadgetTest implements TestPropertyProvider {

    private final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:latest");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    ConsumerTemplate consumerTemplate;

    @Override
    public Map<String, String> getProperties() {
        artemis.start();
        return Map.of(
            "input", copyOrders().toString(),
            "amqp.remote-uri", "amqp://" + artemis.getHost() + ':' + artemis.getMappedPort(61616),
            "amqp.service.username", artemis.getUser(),
            "amqp.service.password", artemis.getPassword()
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
    void ordersAreRoutedByProduct() {
        String widget = consumerTemplate.receiveBody("amqp:queue:widget.queue", 30_000, String.class);
        String gadget = consumerTemplate.receiveBody("amqp:queue:gadget.queue", 30_000, String.class);

        assertNotNull(widget);
        assertTrue(widget.contains("\"product\": \"widget\""));
        assertNotNull(gadget);
        assertTrue(gadget.contains("\"product\": \"gadget\""));
    }

    private static Path copyOrders() {
        try {
            Path input = Files.createTempDirectory("camel-widget-gadget-example");
            for (String order : new String[] {"order1.json", "order2.json"}) {
                try (InputStream data = WidgetGadgetTest.class.getResourceAsStream("/examples/widget-gadget/data/" + order)) {
                    Files.copy(data, input.resolve(order));
                }
            }
            return input;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
