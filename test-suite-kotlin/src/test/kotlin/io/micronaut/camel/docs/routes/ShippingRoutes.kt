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
package io.micronaut.camel.docs.routes

// tag::routes[]
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.apache.camel.builder.LambdaRouteBuilder
import org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder

@Factory
class ShippingRoutes {

    @Singleton
    fun shipping() = LambdaRouteBuilder { rb -> // <1>
        rb.from("direct:ship").setBody(rb.simple("shipped \${body}"))
    }

    @Singleton
    fun tracking() = LambdaEndpointRouteBuilder { rb -> // <2>
        rb.from(rb.direct("track")).setBody(rb.simple("tracking \${body}"))
    }
}
// end::routes[]
