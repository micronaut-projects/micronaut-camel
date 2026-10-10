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
package io.micronaut.camel;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogDebugger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel's debugger, enabled with {@code camel.debug.*}.
 */
@MicronautTest
@Property(name = "spec.name", value = "DebuggerTest")
@Property(name = "camel.debug.enabled", value = "true")
@Property(name = "camel.debug.breakpoints", value = "greeting")
@Property(name = "camel.debug.jmx-connector-enabled", value = "false")
class DebuggerTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void theDebuggerSuspendsExchangesAtItsBreakpoints() throws Exception {
        BacklogDebugger debugger = camelContext.hasService(BacklogDebugger.class);
        assertNotNull(debugger);
        assertTrue(camelContext.isDebugging());
        assertTrue(debugger.isEnabled());

        CompletableFuture<Object> reply = producerTemplate.asyncRequestBody("direct:greet", "Fred");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!debugger.getSuspendedBreakpointNodeIds().contains("greeting")) {
            assertTrue(System.nanoTime() < deadline, "the exchange was not suspended");
            Thread.sleep(20);
        }
        assertFalse(reply.isDone());

        debugger.resumeAll();

        assertEquals("Hello Fred", reply.get(30, TimeUnit.SECONDS));
    }

    @Singleton
    @Requires(property = "spec.name", value = "DebuggerTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:greet").setBody(simple("Hello ${body}")).id("greeting");
        }
    }
}
