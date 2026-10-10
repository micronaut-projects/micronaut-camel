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
package io.micronaut.camel.examples.validator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code validator} example of camel-spring-boot-examples.
 * <p>
 * The named Spring components are Micronaut singletons with {@code @Named}, their {@code @Value} fields
 * became constructor parameters, and the timer period is shorter in the test configuration. The test also
 * checks that an exchange completes, which only happens when the output passes the validator.
 */
@MicronautTest(environments = "example-validator")
class SampleCamelApplicationTest {

    @Inject
    CamelContext camelContext;

    @Test
    void shouldProduceMessages() throws Exception {
        // we expect that one or more messages is automatic done by the Camel
        // route as it uses a timer to trigger
        NotifyBuilder notify = new NotifyBuilder(camelContext).whenDone(1).create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }

    @Test
    void outputIsValidated() {
        NotifyBuilder notify = new NotifyBuilder(camelContext).whenCompleted(1).create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }

}
