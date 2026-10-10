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
package io.micronaut.camel.cli;

import io.micronaut.context.ApplicationContext;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Camel CLI connector. The test suite runs with its own {@code user.home}, where the connector
 * writes its files.
 */
class CliConnectorTest {

    private static final File LOCK_FILE = new File(System.getProperty("user.home"),
        ".camel/" + ProcessHandle.current().pid());

    @Test
    void theConnectorReportsAMicronautApplication() {
        try (ApplicationContext context = ApplicationContext.run()) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertInstanceOf(MicronautCliConnector.class, camelContext.hasService(CliConnector.class));
            CliConnectorFactory factory = context.getBean(CliConnectorFactory.class);
            assertEquals("Micronaut", factory.getRuntime());
            assertTrue(LOCK_FILE.isFile(), "the CLI finds the application with its lock file");
        }
        // the files are removed when the application stops
        assertFalse(LOCK_FILE.exists());
    }

    @Test
    void stoppingTheApplicationFromTheCliClosesTheApplicationContext() throws Exception {
        ApplicationContext context = ApplicationContext.run();
        try {
            assertTrue(LOCK_FILE.isFile());

            // camel stop <pid> deletes the lock file
            assertTrue(LOCK_FILE.delete());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (context.isRunning()) {
                assertTrue(System.nanoTime() < deadline, "the application context was not closed");
                Thread.sleep(50);
            }
        } finally {
            if (context.isRunning()) {
                context.close();
            }
        }
    }

    @Test
    void theConnectorCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("camel.cli.enabled", "false"))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertNull(camelContext.hasService(CliConnector.class));
            assertFalse(LOCK_FILE.exists());
        }
    }
}
