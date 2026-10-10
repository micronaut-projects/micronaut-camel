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
package io.micronaut.camel.docs.integrations

// tag::routes[]
import jakarta.inject.Singleton
import org.apache.camel.CamelAuthorizationException
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder

@Singleton
class AdminRoutes extends RouteBuilder {

    @Override
    void configure() {
        rest('/admin')
            .get('/stats').to('direct:admin-stats') // <1>

        from('direct:admin-stats')
            .onException(CamelAuthorizationException) // <2>
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setBody(constant('Forbidden'))
            .end()
            .policy('adminPolicy') // <3>
            .setBody(constant([orders: 42]))
    }
}
// end::routes[]
