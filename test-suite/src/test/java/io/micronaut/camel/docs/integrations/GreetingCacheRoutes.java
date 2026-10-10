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
package io.micronaut.camel.docs.integrations;

// tag::routes[]
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

@Singleton
public class GreetingCacheRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:remember-greeting")
            .setHeader("CamelRedis.Key", simple("greeting:${header.name}"))
            .setHeader("CamelRedis.Value", body())
            .to("spring-redis://localhost:6379?command=SET"); // <1>

        from("direct:recall-greeting")
            .setHeader("CamelRedis.Key", simple("greeting:${header.name}"))
            .to("spring-redis://localhost:6379?command=GET"); // <2>
    }
}
// end::routes[]
