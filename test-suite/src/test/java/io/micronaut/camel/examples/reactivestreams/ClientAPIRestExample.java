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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * This example shows how to define Camel rest endpoints using the direct client API.
 *
 * Note: the code is not Micronaut related and could have been placed in a standalone main().
 */
public class ClientAPIRestExample {

    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.client-api.rest", value = "true")
    public static class ClientAPIRestExampleStreams {

        private final CamelReactiveStreamsService camel;

        ClientAPIRestExampleStreams(CamelReactiveStreamsService camel) {
            this.camel = camel;
        }

        @EventListener
        @Order(1) // once Camel has started
        void setup(StartupEvent event) {

            // Rest endpoint to retrieve all orders: http://localhost:8080/orders
            camel.process("rest:get:orders", exchange ->
                    Flux.from(exchange)
                            .flatMap(ex -> allOrders()));


            // Rest endpoint to retrieve an order.
            // Try: http://localhost:8080/orders/1
            // Or: http://localhost:8080/orders/xxx
            camel.process("rest:get:orders/{orderId}", exchange ->
                    Flux.from(exchange)
                            .map(ex -> ex.getIn().getHeader("orderId", String.class))
                            .flatMap(this::toOrderInfo)
                            .map(Object.class::cast)
                            .switchIfEmpty(
                                    Flux.from(exchange)
                                            .doOnNext(ex -> ex.getMessage().setBody("Not found"))
                                            .doOnNext(ex -> ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404))
                            ));

        }

        private Publisher<String> toOrderInfo(String orderId) {
            // Simulate a retrieval from DB
            return allOrders()
                    .filter(o -> o.equals(orderId)) // Ensure the order exists
                    .map("Detailed Info on "::concat) // Add detailed info
                    .next();
        }

        private Flux<String> allOrders() {
            return Flux.just("1", "2");
        }

    }

}
