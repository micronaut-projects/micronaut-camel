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
package io.micronaut.camel;

import io.micronaut.cache.SyncCache;
import io.micronaut.camel.cache.MicronautCacheIdempotentRepository;
import io.micronaut.camel.json.MicronautJsonDataFormat;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "IntegrationsTest")
@Property(name = "micronaut.caches.processed.maximum-size", value = "100")
@Property(name = "vault.client.uri", value = "https://vault.example.com:8200")
@Property(name = "vault.client.token", value = "s3cret")
@Property(name = "inbox.dir", value = "build/tmp/integrations-inbox")
class IntegrationsTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ApplicationEventPublisher<Object> publisher;

    @Inject
    OrderEvents orderEvents;

    @Test
    void routesPublishApplicationEvents() {
        producerTemplate.sendBody("direct:place", new OrderPlaced("book"));

        assertEquals(List.of(new OrderPlaced("book")), orderEvents.placed);
    }

    @Test
    void routesConsumeApplicationEvents() throws Exception {
        MockEndpoint shipped = camelContext.getEndpoint("mock:shipped", MockEndpoint.class);
        shipped.expectedBodiesReceived(new OrderShipped("pen"));

        publisher.publishEvent(new OrderShipped("pen"));

        shipped.assertIsSatisfied();
    }

    @Test
    void pollingConsumersRunOnTheMicronautScheduler() throws Exception {
        MockEndpoint inbox = camelContext.getEndpoint("mock:inbox", MockEndpoint.class);
        inbox.expectedBodiesReceived("hello");

        Path dir = Path.of("build/tmp/integrations-inbox");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("message.txt"), "hello");

        inbox.assertIsSatisfied();
    }

    @Test
    void idempotentConsumersUseAMicronautCache() throws Exception {
        MockEndpoint unique = camelContext.getEndpoint("mock:unique", MockEndpoint.class);
        unique.expectedBodiesReceived("first", "third");

        producerTemplate.sendBodyAndHeader("direct:dedupe", "first", "id", "1");
        producerTemplate.sendBodyAndHeader("direct:dedupe", "second", "id", "1");
        producerTemplate.sendBodyAndHeader("direct:dedupe", "third", "id", "2");

        unique.assertIsSatisfied();
    }

    @Test
    void theJsonDataFormatUsesTheMicronautMapper() {
        String json = producerTemplate.requestBody("direct:to-json", new OrderPlaced("book"), String.class);
        assertEquals("{\"item\":\"book\"}", json);

        assertEquals(new OrderPlaced("cup"), producerTemplate.requestBody("direct:from-json", "{\"item\":\"cup\"}"));
        assertEquals(Map.of("item", "lamp"), producerTemplate.requestBody("direct:from-json-map", "{\"item\":\"lamp\"}"));
    }

    @Test
    void theVaultClientConfiguresCamel() {
        var hashicorp = camelContext.getVaultConfiguration().hashicorp();

        assertEquals("vault.example.com", hashicorp.getHost());
        assertEquals("8200", hashicorp.getPort());
        assertEquals("https", hashicorp.getScheme());
        assertEquals("s3cret", hashicorp.getToken());
    }

    @Serdeable
    public record OrderPlaced(String item) {
    }

    public record OrderShipped(String item) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "IntegrationsTest")
    static class OrderEvents {
        final List<OrderPlaced> placed = new CopyOnWriteArrayList<>();

        @EventListener
        void onPlaced(OrderPlaced event) {
            placed.add(event);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "IntegrationsTest")
    static class Repositories {
        @Singleton
        @Named("processed")
        IdempotentRepository processed(@Named("processed") SyncCache<?> cache) {
            return new MicronautCacheIdempotentRepository(cache);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "IntegrationsTest")
    static class Routes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:place").to("micronaut-event:" + OrderPlaced.class.getName());

            from("micronaut-event:" + OrderShipped.class.getName()).to("mock:shipped");

            from("file:{{inbox.dir}}?scheduler=micronaut&initialDelay=0&delay=50&delete=true")
                .convertBodyTo(String.class)
                .to("mock:inbox");

            from("direct:dedupe")
                .idempotentConsumer(header("id")).idempotentRepository("processed")
                .to("mock:unique");

            from("direct:to-json").marshal().custom(MicronautJsonDataFormat.NAME);

            MicronautJsonDataFormat orders = new MicronautJsonDataFormat();
            orders.setUnmarshalType(OrderPlaced.class);
            from("direct:from-json").unmarshal(orders);

            from("direct:from-json-map").unmarshal().custom(MicronautJsonDataFormat.NAME);
        }
    }
}
