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
import reactor.core.publisher.Flux;

import java.io.InputStream;

/**
 * This example shows how to define a complex workflow using Camel direct client API.
 *
 * Note: the code is not Micronaut related and could have been placed in a standalone main().
 */
public class ClientAPIWorkflowExample {

    /**
     * The reactor streams.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.client-api.workflow", value = "true")
    public static class ClientAPIWorkflowExampleStreams {

        private final CamelReactiveStreamsService camel;

        ClientAPIWorkflowExampleStreams(CamelReactiveStreamsService camel) {
            this.camel = camel;
        }

        @EventListener
        @Order(1) // once Camel has started
        void setup(StartupEvent event) {

            /*
             * This workflow reads all files from the directory named "input",
             * marshals them using the Camel marshalling features (simulation)
             * and sends them to an external system (simulation)
             * only if they contain the word "camel".
             */
            Flux.from(camel.from("file:{{examples.client-api.input}}", InputStream.class))
                    .flatMap(camel.to("direct:unmarshal", String.class))
                    .filter(text -> text.contains("camel"))
                    .flatMap(camel.to("direct:send", String.class))
                    .subscribe();

        }

    }

    /**
     * The Camel Configuration.
     */
    @Singleton
    @Requires(env = "example-reactive-streams")
    @Requires(property = "examples.client-api.workflow", value = "true")
    public static class ClientAPIWorkflowExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            from("direct:unmarshal")
                    // This can be far more complex, using marshal()
                    .convertBodyTo(String.class)
                    .log("Content marshalled to string: ${body}");

            from("direct:send")
                    .log("Sending the file to an external system (simulation) with text: ${body}");

        }

    }

}
