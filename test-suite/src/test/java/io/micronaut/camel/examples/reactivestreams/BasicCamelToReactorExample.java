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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This example shows how a reactive stream framework can subscribe to events published
 * by Camel routes.
 *
 * The exchange pattern is in-only from Camel to Reactor.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
public class BasicCamelToReactorExample {

    /**
     * The reactor streams.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.basic.camel-to-reactor", value = "true")
    public static class BasicCamelToReactorExampleStreams {
        private static final Logger LOG = LoggerFactory.getLogger(BasicCamelToReactorExample.class);

        final List<String> received = new CopyOnWriteArrayList<>();

        private final CamelReactiveStreamsService camel;

        BasicCamelToReactorExampleStreams(CamelReactiveStreamsService camel) {
            this.camel = camel;
        }

        @EventListener
        @Order(1) // once Camel has started
        void setupStreams(StartupEvent event) {

            // Use two streams from Camel
            Publisher<Integer> numbers = camel.fromStream("numbers", Integer.class);
            Publisher<String> strings = camel.fromStream("strings", String.class);

            Flux.from(numbers)
                    .zipWith(strings) // emit items in pairs
                    .map(tuple -> "BasicCamelToReactor - " + tuple.getT1() + " -> " + tuple.getT2())
                    .doOnNext(LOG::info)
                    .doOnNext(received::add)
                    .subscribe();
        }

    }


    /**
     * The Camel Configuration.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.basic.camel-to-reactor", value = "true")
    public static class BasicCamelToReactorExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Generating numbers every 5 seconds and forwarding to the stream "numbers"
            from("timer:clock?period=5000&includeMetadata=true")
                    .setBody().header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:numbers");

            // Generating strings every 4.9 seconds and forwarding to the stream "strings"
            from("timer:clock2?period=4900&delay=2000&includeMetadata=true")
                    .setBody().simple("Hello World ${header.CamelTimerCounter}!")
                    .to("reactive-streams:strings");

        }

    }

}
