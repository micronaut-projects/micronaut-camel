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

@Singleton
@Requires(env = "example-resilience4j")
public class ClientRoute extends RouteBuilder {

    @Override
    public void configure() {
        // you can configure the route rule with Java DSL here
        from("timer:trigger?period=500").routeId("client").streamCache("true")
            .bean("counterBean")
            .log(" Client request: ${body}")
            .circuitBreaker().id("circuitBreaker")
                // see application-example-resilience4j.yml how resilience is configured
                .to("{{service1.url}}")
            //.onFallback()
            // we use a fallback without network that provides a response message immediately
            //    .transform().simple("Fallback ${body}")
            .onFallback()
                // we use fallback via network where we call a 2nd service
                .to("{{service2.url}}")
            .end()
            .log("Client response: ${body}");
    }

}
