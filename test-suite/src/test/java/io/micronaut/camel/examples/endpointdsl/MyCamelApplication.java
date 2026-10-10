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
package io.micronaut.camel.examples.endpointdsl;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder;

/**
 * Replaces the {@code @Bean} method of the Spring Boot application class.
 */
@Factory
@Requires(env = "example-endpointdsl")
public class MyCamelApplication {

    // embed route directly using type-safe endpoint-dsl using java lambda style
    @Singleton
    public LambdaEndpointRouteBuilder myRoute() {
        return rb -> rb
                .from(rb.timer("timer").period(2000))
                    .to(rb.bean("myBean").method("saySomething"))
                    .log("${body}");
    }

}
