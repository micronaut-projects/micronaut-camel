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
package io.micronaut.camel.examples.reactivestreams;

import io.micronaut.camel.test.MockEndpoints;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code reactive-streams} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The Reactor streams and the routes are Micronaut beans, enabled by the {@code examples.*} flags of
 * {@code application-example-reactive-streams.yml} with {@code @Requires(property = ...)} instead of
 * {@code @ConditionalOnProperty}. The streams inject the {@link CamelReactiveStreamsService} bean of
 * {@link ReactiveStreamsServiceFactory}, and set themselves up on the startup event, once Camel has started,
 * instead of in {@code @PostConstruct}. The REST endpoints are served by the Micronaut HTTP server, at the root
 * instead of under {@code /camel}, and the REST DSL service does not bind JSON. The workflow reads the
 * {@code examples.client-api.input} directory, a temporary directory in the test, instead of {@code input}.
 * <p>
 * The test checks each example: the pairs of the Camel streams that Reactor zips, the elements of Reactor that
 * Camel logs, the user that Reactor builds for Camel, the square roots that Camel computes for Reactor, the REST
 * endpoints of the README and the files that the workflow sends.
 */
@MicronautTest(environments = "example-reactive-streams")
@MockEndpoints("direct:send")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveStreamsTest implements TestPropertyProvider {

    private final Path input = createTempDirectory();

    @Inject
    CamelContext camelContext;

    @Inject
    CamelReactiveStreamsService camelReactiveStreamsService;

    @Inject
    BasicCamelToReactorExample.BasicCamelToReactorExampleStreams camelToReactor;

    @Inject
    BasicReactorToCamelInOutExample.BasicReactorToCamelInOutExampleStreams reactorToCamelInOut;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
            "micronaut.server.port", String.valueOf(SocketUtils.findAvailableTcpPort()),
            "examples.client-api.input", input.toString());
    }

    @Test
    void theComponentUsesTheServiceBean() {
        assertSame(camelReactiveStreamsService, CamelReactiveStreams.get(camelContext));
    }

    @Test
    void reactorAndCamelExchangeData() throws Exception {
        NotifyBuilder elements = new NotifyBuilder(camelContext)
            .whenAnyDoneMatches(body().isEqualTo("BasicReactorToCamel - Camel received element 0"))
            .create();
        NotifyBuilder userInfo = new NotifyBuilder(camelContext)
            .fromRoute("camel-to-reactor-in-out")
            .whenAnyDoneMatches(body().convertToString().isEqualTo("UserInfo{id=1, name='Name1', address='Address1'}"))
            .create();

        await(() -> camelToReactor.received, "BasicCamelToReactor - 1 -> Hello World 1!");
        await(() -> reactorToCamelInOut.received, "BasicReactorToCamelInOut - sqrt=1.0");
        assertTrue(elements.matches(15, TimeUnit.SECONDS));
        assertTrue(userInfo.matches(15, TimeUnit.SECONDS));
    }

    @Test
    void restEndpointsUseTheClientApi() {
        assertEquals("Detailed Info on 1", httpClient.toBlocking().retrieve("/orders/1"));
        assertEquals("Detailed Info on 2", httpClient.toBlocking().retrieve("/orders/2"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> httpClient.toBlocking().retrieve("/orders/3"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("Not found", e.getResponse().getBody(String.class).orElseThrow());
    }

    @Test
    void restEndpointCallsAReactorBean() {
        assertEquals("The result is: 54", httpClient.toBlocking().retrieve("/sum/23/31"));
    }

    @Test
    void workflowSendsTheFilesAboutCamel() throws Exception {
        MockEndpoint send = camelContext.getEndpoint("mock:direct:send", MockEndpoint.class);
        send.expectedBodiesReceived("Hello camel");
        send.setResultWaitTime(TimeUnit.SECONDS.toMillis(10));

        Files.writeString(input.resolve("other.txt"), "Hello world");
        Files.writeString(input.resolve("camel.txt"), "Hello camel");

        send.assertIsSatisfied();
        // the file without the word was filtered out
        send.setAssertPeriod(TimeUnit.SECONDS.toMillis(1));
        send.assertIsSatisfied();
    }

    private static void await(Supplier<List<String>> received, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        while (!received.get().contains(expected)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Expected " + expected + " in " + received.get());
            }
            Thread.sleep(100);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("camel-reactive-streams");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
