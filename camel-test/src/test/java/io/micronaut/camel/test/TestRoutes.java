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
package io.micronaut.camel.test;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * Routes shared by the tests of the Camel test annotations.
 */
final class TestRoutes {

    private TestRoutes() {
    }

    @Singleton
    @Requires(property = "camel.test.routes", value = "orders")
    static class OrderRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:order").routeId("orders")
                .log("Order ${body}")
                .to("log:orders")
                .to("direct:warehouse");

            from("direct:warehouse").routeId("warehouse")
                .setBody(simple("shipped ${body}"));
        }
    }

    @Singleton
    @Requires(property = "camel.test.routes", value = "orders")
    static class AuditRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:audit").routeId("audit")
                .to("log:audit");
        }
    }
}
