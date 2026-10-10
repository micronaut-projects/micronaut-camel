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
package io.micronaut.camel.docs.integrations;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class IntegrationRoutesTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void filesAreImportedOnce() throws Exception {
        MockEndpoint imported = camelContext.getEndpoint("mock:imported", MockEndpoint.class);
        imported.expectedBodiesReceived("order-1");

        Path inbox = Files.createDirectories(Path.of("build/tmp/docs-inbox"));
        Files.writeString(inbox.resolve("a.txt"), "order-1");
        imported.assertIsSatisfied();

        producerTemplate.sendBody("direct:import", "order-1");
        imported.assertIsSatisfied();
    }

    @Test
    void ordersAreWrittenAsJson() {
        assertEquals("{\"item\":\"book\"}", producerTemplate.requestBody("direct:order-json", Map.of("item", "book"), String.class));
    }
}
