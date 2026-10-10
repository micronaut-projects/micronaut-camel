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
package io.micronaut.camel.examples.platformhttpproxy;

import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code platform-http-proxy} example of camel-spring-boot-examples.
 * <p>
 * The backend, {@code http://httpbin.org} in the example, is the {@link BackendController} of the same
 * application, so the test does not need the network, and the test runs the server on a known free port
 * to point {@code reverse-proxy.target-base-uri} at it. The {@code stripUriPrefix} endpoint option is
 * new in Camel 4.23, so the route strips the prefix from {@code CamelHttpPath} itself. The example has no
 * test: this one makes the request of its README.
 */
@MicronautTest(environments = "example-platform-http-proxy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CamelRouterTest implements TestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    CamelContext camelContext;

    @Override
    public Map<String, String> getProperties() {
        return Map.of("micronaut.server.port", String.valueOf(SocketUtils.findAvailableTcpPort()));
    }

    @Test
    void forwardsToTheBackend() {
        // the proxied exchange and the two wire taps that log the headers
        NotifyBuilder notify = new NotifyBuilder(camelContext).whenCompleted(3).create();

        HttpResponse<Map> response = httpClient.toBlocking().exchange(
            HttpRequest.GET("/reverse-proxy/get?arg1=val1").accept(MediaType.APPLICATION_JSON_TYPE), Map.class);

        Map<?, ?> body = response.body();
        assertEquals(Map.of("arg1", "val1"), body.get("args"));
        assertEquals("/backend/get", body.get("path"));
        assertEquals("application/json", body.get("accept"));
        assertTrue(response.getContentType().orElseThrow().matches(MediaType.APPLICATION_JSON_TYPE));
        assertTrue(notify.matches(5, TimeUnit.SECONDS));
    }
}
