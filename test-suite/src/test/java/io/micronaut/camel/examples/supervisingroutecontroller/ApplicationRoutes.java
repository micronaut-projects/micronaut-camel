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
package io.micronaut.camel.examples.supervisingroutecontroller;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

@Singleton
@Requires(env = "example-supervising-route-controller")
public class ApplicationRoutes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:foo?period=5000")
            .id("foo")
            .startupOrder(2)
            .log("From timer (foo) ...");

        from("timer:bar?period=5000")
            .id("bar")
            .startupOrder(1)
            .log("From timer (bar) ...");

        from("undertow:http://localhost:9011")
            .id("undertow")
            .log("From undertow ...");

        from("undertow:http://localhost:9012")
            .id("undertow2")
            .autoStartup(false)
            .log("From undertow 2...");

        from("undertow:http://localhost:9013")
            .id("undertow3")
            .log("From undertow 3...");
    }
}
