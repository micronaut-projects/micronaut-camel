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
package io.micronaut.camel.examples.rabbitmq;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code rabbitmq} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The route is a Micronaut singleton. Spring Boot's {@code spring.rabbitmq.*} connection settings became
 * those of Micronaut RabbitMQ ({@code rabbitmq.*}), from which micronaut-camel creates the Spring AMQP
 * connection factory and admin that the {@code spring-rabbitmq} component autowires. The broker is a
 * RabbitMQ container instead of one on localhost, so the test sets the settings. The commented out
 * connection factory bean of the application class was dropped.
 */
@MicronautTest(environments = "example-rabbitmq")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleCamelRouterTest implements TestPropertyProvider {

    private final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Inject
    ApplicationContext applicationContext;

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        rabbit.start();
        return Map.of(
            "rabbitmq.host", rabbit.getHost(),
            "rabbitmq.port", String.valueOf(rabbit.getAmqpPort()),
            "rabbitmq.username", rabbit.getAdminUsername(),
            "rabbitmq.password", rabbit.getAdminPassword()
        );
    }

    @AfterAll
    void stopRabbit() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
        rabbit.stop();
    }

    @Test
    void numbersGoThroughRabbitmq() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).from("spring-rabbitmq:*").whenDone(2)
            .and().whenAnyDoneMatches(body().startsWith("Random number"))
            .create();

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
        assertEquals("RabbitCamel", camelContext.getName());
    }
}
