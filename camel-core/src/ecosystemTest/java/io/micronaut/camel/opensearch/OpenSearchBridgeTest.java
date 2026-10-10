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
package io.micronaut.camel.opensearch;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.opensearch.OpensearchComponent;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Camel autowires the {@link RestClient} and the {@link OpenSearchClient} of Micronaut OpenSearch
 * (micronaut-opensearch-restclient) into the {@code opensearch} component. They are prototype beans, so the
 * component gets clients of its own, configured with {@code micronaut.opensearch.rest-client.*}. Creating the
 * clients does not connect to a server.
 */
@MicronautTest
@Property(name = "micronaut.opensearch.rest-client.http-hosts", value = "http://search.example.com:9201")
class OpenSearchBridgeTest {

    @Inject
    CamelContext camelContext;

    @Test
    void theComponentUsesClientsOfMicronautOpenSearch() {
        OpensearchComponent component = camelContext.getComponent("opensearch", OpensearchComponent.class);

        assertEquals(List.of("http://search.example.com:9201"), hosts(component.getClient()));
        RestClientTransport transport = assertInstanceOf(RestClientTransport.class, component.getOpenSearchClient()._transport());
        assertEquals(List.of("http://search.example.com:9201"), hosts(transport.restClient()));
    }

    private static List<String> hosts(RestClient client) {
        return client.getNodes().stream().map(node -> node.getHost().toURI()).toList();
    }
}
