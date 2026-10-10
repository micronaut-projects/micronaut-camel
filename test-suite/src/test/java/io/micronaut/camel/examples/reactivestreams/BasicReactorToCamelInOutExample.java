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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This example shows how a reactive stream framework can asynchronously request data to a Camel stream
 * and continue processing.
 *
 * The exchange pattern is in-out from Reactor to Camel.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
public class BasicReactorToCamelInOutExample {

    /**
     * The reactor streams.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.basic.reactor-to-camel-in-out", value = "true")
    public static class BasicReactorToCamelInOutExampleStreams {
        private static final Logger LOG = LoggerFactory.getLogger(BasicReactorToCamelInOutExample.class);

        final List<String> received = new CopyOnWriteArrayList<>();

        private final CamelReactiveStreamsService camel;

        BasicReactorToCamelInOutExampleStreams(CamelReactiveStreamsService camel) {
            this.camel = camel;
        }

        @EventListener
        @Order(1) // once Camel has started
        void setupStreams(StartupEvent event) {

            Flux.interval(Duration.ofSeconds(8))
                    .map(i -> i + 1) // to start from 1
                    .flatMap(camel.toStream("sqrt", Double.class)) // call Camel and continue
                    .map(d -> "BasicReactorToCamelInOut - sqrt=" + d)
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
    @Requires(property = "examples.basic.reactor-to-camel-in-out", value = "true")
    public static class BasicReactorToCamelInOutExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Transform the body of every exchange into its square root
            from("reactive-streams:sqrt")
                    .setBody().body(Double.class, Math::sqrt);

            // This route can be much more complex: it can use any Camel component to compute the output data

        }

    }

}
