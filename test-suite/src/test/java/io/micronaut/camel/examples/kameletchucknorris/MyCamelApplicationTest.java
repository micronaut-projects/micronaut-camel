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
package io.micronaut.camel.examples.kameletchucknorris;

import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code kamelet-chucknorris} example of camel-spring-boot-examples.
 * <p>
 * The route is a Micronaut singleton, and the Kamelet is loaded from {@code classpath:kamelets}, the default
 * location of camel-kamelet, by the YAML DSL. The joke service of the Kamelet, {@code https://api.chucknorris.io},
 * is the {@link JokesController} of the same application: the Kamelet got a {@code url} property, whose default
 * is the original URL, and {@code application-example-kamelet-chucknorris.yml} sets it, and a shorter period,
 * with the {@code camel.kamelet.chuck-norris-source.*} options. The test runs the server on a known free port to
 * point the Kamelet at it. {@code camel.main.run-controller} was dropped: Micronaut owns the application lifecycle.
 */
@MicronautTest(environments = "example-kamelet-chucknorris")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyCamelApplicationTest implements TestPropertyProvider {

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        return Map.of("micronaut.server.port", String.valueOf(SocketUtils.findAvailableTcpPort()));
    }

    @Test
    void shouldProduceMessages() {
        // we expect that one or more messages is automatic done by the Camel
        // route as it uses a timer to trigger
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .whenDone(1)
            .and().whenAnyDoneMatches(body().isEqualTo(JokesController.JOKE))
            .create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }
}
