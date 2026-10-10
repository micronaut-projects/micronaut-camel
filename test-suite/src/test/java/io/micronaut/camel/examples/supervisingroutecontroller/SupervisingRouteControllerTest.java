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
package io.micronaut.camel.examples.supervisingroutecontroller;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code supervising-route-controller} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The routes are a Micronaut singleton, and the {@code camel.routecontroller.*} options come from
 * {@code application-example-supervising-route-controller.yml}, with a shorter initial delay. The actuator
 * and logging settings were dropped. The undertow consumers are served by camel-undertow on their own
 * ports, not by Micronaut. The test checks that the supervising route controller starts the routes in the
 * background, leaving the route that is not auto-started stopped.
 */
@MicronautTest(environments = "example-supervising-route-controller")
class SupervisingRouteControllerTest {

    @Inject
    CamelContext camelContext;

    @Test
    void routesAreStartedBySupervisingRouteController() {
        assertInstanceOf(SupervisingRouteController.class, camelContext.getRouteController());

        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("foo").whenDone(1)
            .and().fromRoute("bar").whenDone(1)
            .create();

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
        assertEquals(ServiceStatus.Stopped, camelContext.getRouteController().getRouteStatus("undertow2"));
    }
}
