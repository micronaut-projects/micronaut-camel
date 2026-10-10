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
package io.micronaut.camel.docs.transactions

import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.CamelContext
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class OrderAggregationsTest {

    @Inject
    lateinit var camelContext: CamelContext

    @Inject
    @field:Named("orderAggregations")
    lateinit var repository: JdbcAggregationRepository

    @Test
    fun storesExchangesAsJson() {
        // started by Camel when a route uses it
        camelContext.addService(repository)
        val exchange = DefaultExchange(camelContext)
        exchange.message.body = Line("book", 2)
        exchange.message.setHeader("customer", "Fred")

        repository.add(camelContext, "basket-1", exchange)

        val stored = repository.get(camelContext, "basket-1")!!
        assertEquals(Line("book", 2), stored.message.body)
        assertEquals("Fred", stored.message.getHeader("customer"))
        repository.remove(camelContext, "basket-1", stored)
        repository.confirm(camelContext, stored.exchangeId)
    }

    @Introspected
    data class Line(var item: String = "", var quantity: Int = 0)
}
