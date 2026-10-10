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
package io.micronaut.camel.examples.resilience4j;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * The routes of the {@code service1} and {@code service2} applications of the example, served by the
 * Micronaut HTTP server of the same application.
 */
@Singleton
@Requires(env = "example-resilience4j")
public class ServiceRoutes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("platform-http:/service1").routeId("service1").streamCache("true")
            .log("Service1 request: ${body}")
            .transform(simple("Service1-${body}"))
            .log("Service1 response: ${body}");

        from("platform-http:/service2").routeId("service2").streamCache("true")
            .log(" Service2 request: ${body}")
            .transform(simple("Service2-${body}"))
            .log("Service2 response: ${body}");
    }

}
