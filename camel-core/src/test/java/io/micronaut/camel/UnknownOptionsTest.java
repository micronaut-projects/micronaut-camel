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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import org.apache.camel.CamelContext;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.main.BaseMainSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A component option that the Camel version on the classpath does not know, as when the
 * configuration was written for a newer Camel catalog.
 */
class UnknownOptionsTest {

    @Test
    void anUnknownComponentOptionFailsTheStartupByDefault() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> ApplicationContext.run(Map.of(
            "camel.component.seda.queue-size", "42",
            "camel.component.seda.option-of-a-newer-camel", "true"
        )).close());

        assertTrue(causes(e).contains("optionOfANewerCamel"), causes(e));
    }

    @Test
    void anUnknownComponentOptionIsIgnoredWithoutFailFast() {
        Logger logger = (Logger) LoggerFactory.getLogger(BaseMainSupport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "camel.main.auto-configuration-fail-fast", "false",
            "camel.component.seda.queue-size", "42",
            "camel.component.seda.option-of-a-newer-camel", "true"
        ))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertTrue(camelContext.getStatus().isStarted());
            // the known options still apply
            assertEquals(42, camelContext.getComponent("seda", SedaComponent.class).getQueueSize());
            // Camel ignores the unknown option without a warning: it is only missing from the summary
            // of the options it applied
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage()
                .contains("camel.component.seda.queueSize = 42")), () -> appender.list.toString());
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("optionOfANewerCamel")
                || event.getFormattedMessage().contains("option-of-a-newer-camel")), () -> appender.list.toString());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void anOptionOfAMissingComponentFailsTheStartup() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> ApplicationContext.run(Map.of(
            "camel.main.auto-configuration-fail-fast", "false",
            "camel.component.not-on-the-classpath.some-option", "true"
        )).close());

        assertTrue(causes(e).contains("cannot find component with name not-on-the-classpath"), causes(e));
    }

    private static String causes(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
