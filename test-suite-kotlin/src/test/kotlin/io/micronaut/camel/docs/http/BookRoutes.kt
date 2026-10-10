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
package io.micronaut.camel.docs.http

// tag::routes[]
import jakarta.inject.Singleton
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode
import java.util.concurrent.ConcurrentHashMap

@Singleton
class BookRoutes : RouteBuilder() {

    private val books = ConcurrentHashMap<String, Book>()

    override fun configure() {
        restConfiguration().bindingMode(RestBindingMode.json) // <1>

        rest("/books")
            .post().type(Book::class.java).outType(Book::class.java).to("direct:saveBook") // <2>
            .get("/{isbn}").outType(Book::class.java).to("direct:findBook") // <3>

        from("direct:saveBook")
            .process { exchange ->
                val book = exchange.message.getMandatoryBody(Book::class.java)
                books[book.isbn] = book
            }
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201)) // <4>

        from("direct:findBook")
            .process { exchange ->
                val isbn = exchange.message.getHeader("isbn", String::class.java)
                exchange.message.body = books[isbn]
            }
    }
}
// end::routes[]
