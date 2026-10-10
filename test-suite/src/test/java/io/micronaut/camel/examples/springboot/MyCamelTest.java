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
package io.micronaut.camel.examples.springboot;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code spring-boot} example of camel-spring-boot-examples.
 * <p>
 * The Spring {@code @Component} beans are Micronaut singletons, {@code @Value} and {@code @Autowired} field
 * injection became constructor injection, and {@code application.properties} became
 * {@code application-example-spring-boot.yml} with a shorter timer period. The actuator and dev console
 * settings, {@code @EnableRouteCoverage} and the route diagram dump test were dropped.
 */
@MicronautTest(environments = "example-spring-boot")
class MyCamelTest {

    @Inject
    CamelContext camelContext;

    @Test
    void shouldProduceMessages() {
        // we expect that one or more messages is automatically done by the Camel
        // route as it uses a timer to trigger
        NotifyBuilder notify = new NotifyBuilder(camelContext).whenDone(1)
            // and the named bean was called
            .and().whenAnyDoneMatches(body().startsWith("Hello World I am invoked"))
            .create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
        assertEquals("MyCamel2", camelContext.getName());
    }

}
