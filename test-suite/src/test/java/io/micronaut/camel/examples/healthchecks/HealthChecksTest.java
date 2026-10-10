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
package io.micronaut.camel.examples.healthchecks;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code health-checks} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The Spring {@code @Bean} and {@code @Component} became Micronaut singletons, field injection became
 * constructor injection, and {@code application.properties} became
 * {@code application-example-health-checks.yml}. The actuator settings map to Micronaut's health
 * endpoint: Camel's health checks are reported by {@code /health}, and its readiness and liveness checks by
 * {@code /health/readiness} and {@code /health/liveness}, which Micronaut always exposes. The route that
 * fails to start used {@code platform-http}, which needs an HTTP engine; it consumes from
 * {@code undertow} on an address that cannot be bound instead. The test waits for the timer to unleash the
 * chaos monkey, and checks that the health and readiness endpoints report it and the failing route, which the
 * supervising route controller keeps restarting, while the liveness endpoint, which only runs Camel's
 * liveness checks, reports UP.
 */
@MicronautTest(environments = "example-health-checks")
class HealthChecksTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    CamelContext camelContext;

    @Inject
    MonkeyHealthCheck monkey;

    @Test
    @SuppressWarnings("unchecked")
    void healthEndpointsReportTheChaosMonkey() {
        assertEquals("MyCamel", camelContext.getName());
        assertInstanceOf(SupervisingRouteController.class, camelContext.getRouteController());

        // the first run of the timer route makes the monkey report DOWN, until the next run
        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("timer").whenDone(1).create();
        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        for (String path : new String[] {"/health", "/health/readiness"}) {
            Map<String, Object> checks = camelChecks(path);
            assertEquals("DOWN", checks.get("status"));
            Map<String, Object> monkeyCheck = (Map<String, Object>) ((Map<String, Object>) checks.get("details")).get("monkey");
            assertEquals("DOWN", monkeyCheck.get("status"));
            assertEquals("The chaos monkey was here", monkeyCheck.get("monkey"));
        }
        // custom checks are readiness checks, so the application is still live
        Map<String, Object> liveness = client.toBlocking().retrieve("/health/liveness", Map.class);
        assertEquals("UP", liveness.get("status"));

        // calm the monkey down: Camel reports only the checks that are DOWN, the failing route
        assertEquals("All is okay", monkey.chaos());
        Map<String, Object> checks = (Map<String, Object>) camelChecks("/health/readiness").get("details");
        assertFalse(checks.containsKey("monkey"));
        assertEquals("DOWN", ((Map<String, Object>) checks.get("route:health-route")).get("status"));

        // the supervising route controller keeps trying to start the route that fails
        assertNotEquals(ServiceStatus.Started, camelContext.getRouteController().getRouteStatus("health-route"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> camelChecks(String path) {
        // the failing route makes the application DOWN
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(path, Map.class));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        Map<String, Object> details = (Map<String, Object>) e.getResponse().getBody(Map.class).orElseThrow().get("details");
        return (Map<String, Object>) details.get("camel");
    }
}
