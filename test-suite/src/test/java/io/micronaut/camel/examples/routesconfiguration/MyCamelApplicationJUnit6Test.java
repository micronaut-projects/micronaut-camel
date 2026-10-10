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
package io.micronaut.camel.examples.routesconfiguration;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code routes-configuration} example of camel-spring-boot-examples.
 * <p>
 * The Java route and route configuration are Micronaut singletons, and the YAML route and route
 * configuration are loaded from {@code examples/routes-configuration} through
 * {@code camel.main.routes-include-pattern}. The XML route, XML error handler and XML interceptor were
 * dropped (XML routes are not supported), as was {@code camel.main.run-controller} (Micronaut keeps the
 * application running). The test also checks that both routes complete, which only happens when their
 * route configuration handles the exception they throw.
 */
@MicronautTest(environments = "example-routes-configuration")
class MyCamelApplicationJUnit6Test {

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
    void routeConfigurationsHandleErrors() {
        assertEquals(
            Set.of("javaError", "yamlError"),
            camelContext.getCamelContextExtension().getContextPlugin(Model.class).getRouteConfigurationDefinitions().stream()
                .map(RouteConfigurationDefinition::getId)
                .collect(Collectors.toSet()));

        // the YAML route always throws, and the Java route throws for low numbers: the exchanges only
        // complete when the route configuration of each route handles the exception
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .from("timer:yaml*").whenCompleted(1)
            .and().from("timer:java*").whenCompleted(1)
            .create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }
}
