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
package io.micronaut.camel.testservices;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.socket.SocketUtils;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;
import java.util.Set;

/**
 * Supplies the services of the Python guide samples to the tests that select their environment, as
 * {@code TestPropertyProvider} does in the Java, Kotlin and Groovy suites: Micronaut Test calls a
 * {@code TestPropertyProvider} before the application context, and with it the GraalPy runtime, exists,
 * so a Python test class cannot provide the properties of the services it starts.
 *
 * <ul>
 *     <li>{@code docs-kafka}: a Kafka broker, for Micronaut Kafka</li>
 *     <li>{@code docs-redis}: a Redis server, for Micronaut Redis ({@code redis.uri} of {@code application-docs-redis.yml})</li>
 *     <li>{@code docs-inventory}: the server on a free port, which is the URL of the {@code inventory} service</li>
 * </ul>
 */
@ContextConfigurer
public final class DocsServices implements ApplicationContextConfigurer {

    private static @Nullable KafkaContainer kafka;
    private static @Nullable GenericContainer<?> redis;

    @Override
    public void configure(ApplicationContext applicationContext) {
        // the environments of @MicronautTest are only known once the context is built, and the property
        // sources added here are only read when the properties are, not when the bean configurations are enabled
        Environment environment = applicationContext.getEnvironment();
        Set<String> names = environment.getActiveNames();
        if (names.contains("docs-kafka")) {
            environment.addPropertySource(PropertySource.of("docs-kafka",
                Map.of("kafka.bootstrap.servers", startKafka().getBootstrapServers())));
        }
        if (names.contains("docs-redis")) {
            GenericContainer<?> container = startRedis();
            environment.addPropertySource(PropertySource.of("docs-redis",
                Map.of("docs.redis.uri", "redis://" + container.getHost() + ":" + container.getMappedPort(6379))));
        }
        if (names.contains("docs-inventory")) {
            String port = String.valueOf(SocketUtils.findAvailableTcpPort());
            environment.addPropertySource(PropertySource.of("docs-inventory", Map.of(
                "micronaut.server.port", port,
                "micronaut.http.services.inventory.url", "http://localhost:" + port)));
        }
    }

    /**
     * Stops the Kafka broker.
     */
    public static synchronized void stopKafka() {
        if (kafka != null) {
            kafka.stop();
            kafka = null;
        }
    }

    /**
     * Stops the Redis server.
     */
    public static synchronized void stopRedis() {
        if (redis != null) {
            redis.stop();
            redis = null;
        }
    }

    private static synchronized KafkaContainer startKafka() {
        if (kafka == null) {
            KafkaContainer container = new KafkaContainer("apache/kafka:4.2.0");
            container.start();
            kafka = container;
        }
        return kafka;
    }

    private static synchronized GenericContainer<?> startRedis() {
        if (redis == null) {
            GenericContainer<?> container = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
            container.start();
            redis = container;
        }
        return redis;
    }
}
