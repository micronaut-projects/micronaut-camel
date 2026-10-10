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
package io.micronaut.camel.examples.fhir;

import io.micronaut.camel.examples.Containers;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code fhir} example of camel-spring-boot-examples.
 * <p>
 * The routes are Micronaut singletons. The route catches {@code org.apache.http.ProtocolException} instead of the
 * httpclient5 {@code org.apache.hc.core5.http.ProtocolException}, as the HAPI FHIR client that camel-fhir brings
 * uses Apache HttpClient 4. As in the original, the test runs a HAPI FHIR server, the {@code hapiproject/hapi}
 * image (about 600 MB, ready in about 20 seconds), with Testcontainers instead of camel-test-infra, and is skipped
 * without a container runtime: the server stores the patient and the test searches it, which a stub could only
 * fake. The test sets {@code serverUrl} with a {@code TestPropertyProvider} instead of a
 * {@code CamelContextConfiguration}, sets the {@code input} directory to a temporary directory, copies the HL7 file
 * from the classpath with {@code java.nio} instead of commons-io, and waits for the file route with a
 * {@code NotifyBuilder} instead of sleeping. The actuator settings were dropped.
 */
@MicronautTest(environments = "example-fhir")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyCamelApplicationTest implements TestPropertyProvider {

    private static final int FHIR_PORT = 8080;

    private final Path input = createTempDirectory();

    private GenericContainer<?> fhir;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public Map<String, String> getProperties() {
        fhir = new GenericContainer<>("hapiproject/hapi:v8.8.0-1")
            .withExposedPorts(FHIR_PORT)
            .waitingFor(Wait.forHttp("/fhir/metadata").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
        Containers.start(fhir);
        return Map.of(
            "serverUrl", "http://" + fhir.getHost() + ":" + fhir.getMappedPort(FHIR_PORT) + "/fhir",
            "input", input.toString());
    }

    @AfterAll
    void stopFhir() {
        fhir.stop();
    }

    @Test
    void shouldPushConvertedHl7toFhir() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        NotifyBuilder created = new NotifyBuilder(camelContext).fromRoute("fhir-example").whenCompleted(1).create();
        try (InputStream patient = getClass().getResourceAsStream("/examples/fhir/data/hl7v2.patient")) {
            Files.copy(patient, input.resolve("hl7v2.patient"));
        }
        assertTrue(created.matches(30, TimeUnit.SECONDS));
        producerTemplate.sendBody("direct:start", null);

        mock.assertIsSatisfied();
        assertEquals("Freeman", mock.getExchanges().get(0).getIn().getBody(Patient.class).getName().get(0).getFamily());
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("camel-fhir-example");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
