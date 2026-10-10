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
package io.micronaut.camel.httpclient;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MicronautHttpComponentTest implements TestPropertyProvider {

    @Inject
    FluentProducerTemplate producer;

    @Override
    public Map<String, String> getProperties() {
        String port = String.valueOf(SocketUtils.findAvailableTcpPort());
        return Map.of(
            "spec.name", "MicronautHttpComponentTest",
            "micronaut.server.port", port,
            "micronaut.http.services.stock.url", "http://localhost:" + port);
    }

    @Test
    void getsFromAServiceId() {
        Exchange exchange = producer.to("micronaut-http:stock/stock/{{stock.item:book}}?warehouse=north")
            .withHeader("X-Trace", "abc")
            .send();

        assertNull(exchange.getException());
        assertEquals("book:north:abc", exchange.getMessage().getBody(String.class));
        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/plain", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    void postsTheBodyToAUrl() {
        int port = Integer.parseInt(producer.getCamelContext().resolvePropertyPlaceholders("{{micronaut.server.port}}"));
        String reply = producer.to("micronaut-http:http://localhost:" + port + "/stock")
            .withHeader(Exchange.HTTP_PATH, "pen")
            .withHeader(Exchange.CONTENT_TYPE, "text/plain")
            .withBody("5")
            .request(String.class);

        assertEquals("pen=5", reply);
    }

    @Test
    void errorsFailTheExchange() {
        Exchange exchange = producer.to("micronaut-http:stock/stock/missing").send();

        MicronautHttpOperationFailedException e = assertInstanceOf(MicronautHttpOperationFailedException.class, exchange.getException());
        assertEquals(404, e.getStatusCode());
        assertEquals("no missing", e.getResponseBody());
    }

    @Test
    void errorsCanBeResponses() {
        Exchange exchange = producer.to("micronaut-http:stock?throwExceptionOnFailure=false")
            .withHeader(Exchange.HTTP_PATH, "/stock/missing")
            .withHeader(Exchange.HTTP_METHOD, "GET")
            .send();

        assertNull(exchange.getException());
        assertEquals(404, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("no missing", exchange.getMessage().getBody(String.class));
    }

    @Controller("/stock")
    @Requires(property = "spec.name", value = "MicronautHttpComponentTest")
    static class StockController {

        @Get("/{item}")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> stock(@PathVariable String item, @Nullable @QueryValue String warehouse,
                                   @Nullable @Header("X-Trace") String trace) {
            if ("missing".equals(item)) {
                return HttpResponse.<String>status(HttpStatus.NOT_FOUND).body("no missing");
            }
            return HttpResponse.ok(item + ":" + warehouse + ":" + trace);
        }

        @Post(value = "/{item}", consumes = MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String add(@PathVariable String item, @Body String quantity) {
            return item + "=" + quantity;
        }
    }
}
