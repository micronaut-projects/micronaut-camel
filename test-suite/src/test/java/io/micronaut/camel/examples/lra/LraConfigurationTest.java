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
package io.micronaut.camel.examples.lra;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.service.lra.LRASagaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the {@code camel.lra.*} options of the {@code saga} example of camel-spring-boot-examples, which runs
 * its routes in sagas coordinated by a Narayana LRA coordinator and uses an Artemis broker. Neither runs here: the
 * test checks that camel-main creates the LRA saga service from the Micronaut configuration, before any saga
 * contacts the coordinator, and that the participant routes, which the coordinator calls back, are started. They
 * are REST DSL routes served by the Micronaut HTTP server, so the local participant URL is the server URL, without
 * the {@code /api} mapping of the Camel servlet.
 */
@MicronautTest(environments = "example-lra")
class LraConfigurationTest {

    @Inject
    CamelContext camelContext;

    @Test
    void sagaServiceIsConfiguredFromMicronautConfiguration() {
        LRASagaService sagaService = assertInstanceOf(LRASagaService.class, camelContext.hasService(CamelSagaService.class));

        assertTrue(sagaService.isStarted());
        assertEquals("http://localhost:8080", sagaService.getCoordinatorUrl());
        assertEquals("/lra-coordinator", sagaService.getCoordinatorContextPath());
        assertEquals("http://localhost:8084", sagaService.getLocalParticipantUrl());
        assertEquals("/lra-participant", sagaService.getLocalParticipantContextPath());
        assertEquals(ServiceStatus.Started, camelContext.getRouteController().getRouteStatus("lra-compensation"));
        assertEquals(ServiceStatus.Started, camelContext.getRouteController().getRouteStatus("lra-completion"));
    }
}
