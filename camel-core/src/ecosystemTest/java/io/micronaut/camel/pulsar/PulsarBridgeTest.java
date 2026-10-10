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
package io.micronaut.camel.pulsar;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Camel autowires the {@link PulsarClient} of Micronaut Pulsar into the {@code pulsar} component.
 * Building the client does not connect to a broker.
 */
@MicronautTest
@Property(name = "pulsar.service-url", value = "pulsar://localhost:6650")
class PulsarBridgeTest {

    @Inject
    CamelContext camelContext;

    @Inject
    PulsarClient pulsarClient;

    @Test
    void theComponentUsesTheMicronautClient() {
        assertSame(pulsarClient, camelContext.getComponent("pulsar", PulsarComponent.class).getPulsarClient());
        assertSame(pulsarClient, camelContext.getEndpoint("pulsar:persistent://public/default/orders",
            PulsarEndpoint.class).getPulsarClient());
    }
}
