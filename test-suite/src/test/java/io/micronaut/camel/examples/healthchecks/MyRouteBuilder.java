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
package io.micronaut.camel.examples.healthchecks;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

@Singleton
@Requires(env = "example-health-checks")
public class MyRouteBuilder extends RouteBuilder {

    private final MonkeyHealthCheck monkey;

    MyRouteBuilder(MonkeyHealthCheck monkey) {
        this.monkey = monkey;
    }

    @Override
    public void configure() throws Exception {
        from("timer:foo?period={{myPeriod}}").routeId("timer")
            .bean(monkey, "chaos")
            .log("${body}");

        // this route is invalid and fails during startup
        // the supervising route controller will take over and attempt
        // to restart this route
        from("undertow:http://192.0.2.1:9020/unknownhost").routeId("health-route")
            .to("log:dummy");
    }
}
