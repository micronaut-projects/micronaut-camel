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
package io.micronaut.camel.examples.fhirauthtx;

import io.micronaut.camel.test.MockEndpointsAndSkip;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ports the {@code fhir-auth-tx} example of camel-spring-boot-examples.
 * <p>
 * The route is a Micronaut singleton. It catches {@code org.apache.http.ProtocolException} instead of the
 * httpclient5 {@code org.apache.hc.core5.http.ProtocolException}, as the HAPI FHIR client that camel-fhir
 * brings uses Apache HttpClient 4 and the example only added httpclient5 explicitly. The test sets the
 * {@code input} directory to a temporary directory instead of {@code target/work/fhir/testinput}, copies
 * {@code patients.csv} from the classpath with {@code java.nio} instead of commons-io, and also checks the
 * bundle sent to FHIR. As in the original, {@code @MockEndpointsAndSkip("fhir*")} means no FHIR server is
 * contacted. The actuator and server settings were dropped.
 */
@MicronautTest(environments = "example-fhir-auth-tx")
@MockEndpointsAndSkip("fhir*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyCamelApplicationTest implements TestPropertyProvider {

    private final Path input = createTempDirectory();

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        return Map.of("input", input.toString());
    }

    @Test
    void shouldPushConvertedHl7toFhir() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:fhir:transaction/withBundle", MockEndpoint.class);
        mock.expectedMessageCount(1);

        try (InputStream patients = getClass().getResourceAsStream("/examples/fhir-auth-tx/data/patients.csv")) {
            Files.copy(patients, input.resolve("patients.csv"));
        }

        mock.assertIsSatisfied();

        Bundle bundle = mock.getReceivedExchanges().get(0).getIn().getBody(Bundle.class);
        assertEquals(2, bundle.getEntry().size());
        Patient bob = (Patient) bundle.getEntry().get(0).getResource();
        assertEquals("Bob", bob.getNameFirstRep().getGivenAsSingleString());
        assertEquals("Smith", bob.getNameFirstRep().getFamily());
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("camel-fhir-example");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
