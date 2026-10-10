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
package io.micronaut.camel.examples.infinispan;

import io.micronaut.camel.examples.Containers;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ports the {@code infinispan} example of camel-spring-boot-examples.
 * <p>
 * The {@code RemoteCacheManager} is the bean of micronaut-cache-infinispan, configured with the
 * {@code infinispan.client.hotrod.*} options of {@code application-example-infinispan.yml} instead of the
 * {@code infinispan.*} properties of a bean method, and {@link InfinispanConfiguration} creates the cache once the
 * manager is created. The client uses the {@code BASIC} intelligence, from
 * {@code examples/infinispan/hotrod-client.properties}, as the server of the container announces addresses of the
 * container network. As in the original, an Infinispan server runs the test, started by Testcontainers instead of
 * camel-test-infra; the test is skipped without a container runtime.
 */
@MicronautTest(environments = "example-infinispan")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationTest implements TestPropertyProvider {

    private static final int HOTROD_PORT = 11222;

    private GenericContainer<?> infinispan;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        infinispan = new GenericContainer<>("quay.io/infinispan/server:16.0")
            .withEnv("USER", "admin")
            .withEnv("PASS", "password")
            .withExposedPorts(HOTROD_PORT)
            .waitingFor(Wait.forLogMessage(".*ISPN080001.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
        Containers.start(infinispan);
        return Map.of(
            "infinispan.client.hotrod.server.host", infinispan.getHost(),
            "infinispan.client.hotrod.server.port", String.valueOf(infinispan.getMappedPort(HOTROD_PORT)),
            "infinispan.client.hotrod.security.authentication.username", "admin",
            "infinispan.client.hotrod.security.authentication.password", "password");
    }

    @AfterAll
    void stopInfinispan() {
        infinispan.stop();
    }

    @Test
    void shouldPopulateCache() throws Exception {
        final MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        producerTemplate.sendBody("direct:test", null);
        mock.assertIsSatisfied();
        assertEquals("test", mock.getExchanges().get(0).getIn().getBody(String.class));
    }
}
