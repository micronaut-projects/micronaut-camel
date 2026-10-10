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
package io.micronaut.camel.examples.infinispan;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;

/**
 * A simple Camel Infinispan route example using Micronaut.
 */
@Singleton
@Requires(env = "example-infinispan")
public class CamelInfinispanRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("timer://foo?period=10000&repeatCount=1")
            .to("direct:put-cache");

        from("direct:put-cache")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
            .setHeader(InfinispanConstants.KEY).constant("1")
            .setHeader(InfinispanConstants.VALUE).constant("test")
            .to("infinispan://default")
            .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GET)
            .setHeader(InfinispanConstants.KEY).constant("1")
            .to("infinispan://default")
            .log("Received body: ${body}");
    }

}
