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
package io.micronaut.camel.docs.jsonschema

import jakarta.inject.Singleton
import org.apache.camel.builder.RouteBuilder

@Singleton
class OrderRoutes : RouteBuilder() {

    override fun configure() {
        // tag::validator[]
        from("direct:checked-orders")
            .to("json-validator:classpath:META-INF/schemas/order.schema.json") // <1>
            .to("mock:valid-orders")
        // end::validator[]

        // tag::resource[]
        from("direct:typed-orders")
            .to("json-validator:jsonschema:Order") // <1>
            .to("mock:valid-orders")
        // end::resource[]

        // tag::contract[]
        from("direct:contract-orders")
            .inputTypeWithValidate(Order::class.java) // <1>
            .to("mock:valid-orders")
        // end::contract[]
    }
}
