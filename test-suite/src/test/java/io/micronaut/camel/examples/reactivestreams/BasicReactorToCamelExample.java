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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * This example shows how a reactive stream framework can publish events that are consumed
 * by Camel routes.
 *
 * The exchange pattern is in-only from Reactor to Camel.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
public class BasicReactorToCamelExample {

    /**
     * The reactor streams.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.basic.reactor-to-camel", value = "true")
    public static class BasicReactorToCamelExampleStreams {

        private final CamelReactiveStreamsService camel;

        BasicReactorToCamelExampleStreams(CamelReactiveStreamsService camel) {
            this.camel = camel;
        }

        @EventListener
        @Order(1) // once Camel has started
        void setupStreams(StartupEvent event) {

            // Get a subscriber from camel
            Subscriber<String> elements = camel.streamSubscriber("elements", String.class);

            // Emit a string every 7 seconds and push it to the Camel "elements" stream
            Flux.interval(Duration.ofSeconds(7))
                    .map(item -> "element " + item)
                    .subscribe(elements);

        }
    }


    /**
     * The Camel Configuration.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.basic.reactor-to-camel", value = "true")
    public static class BasicReactorToCamelExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Transform the body of received items and log
            from("reactive-streams:elements")
                    .setBody().simple("BasicReactorToCamel - Camel received ${body}")
                    .to("log:INFO");

        }

    }

}
