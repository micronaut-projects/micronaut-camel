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
package io.micronaut.camel.examples.variables;

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
 * Ports the {@code variables} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The global variables are set with {@code camel.variable.*} in the Micronaut configuration, and the
 * route reads them. The test configuration fires the timer more often.
 */
@MicronautTest(environments = "example-variables")
class VariablesTest {

    @Inject
    CamelContext camelContext;

    @Test
    void variablesAreSetFromConfiguration() {
        assertEquals("Random number", camelContext.getVariable("greeting"));
    }

    @Test
    void routesReadTheVariables() {
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .from("timer:random*")
            .whenAnyDoneMatches(body().regex("Random number: \\d+"))
            .create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }
}
