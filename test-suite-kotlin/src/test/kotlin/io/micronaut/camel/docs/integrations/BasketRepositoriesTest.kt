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

import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.CamelContext
import org.apache.camel.spi.AggregationRepository
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class BasketRepositoriesTest {

    @Inject
    lateinit var camelContext: CamelContext

    @Inject
    @field:Named("baskets")
    lateinit var baskets: AggregationRepository

    @Test
    fun storesExchangesInTheCache() {
        val exchange = DefaultExchange(camelContext)
        exchange.message.body = Item("pen", 3)

        baskets.add(camelContext, "basket-1", exchange)

        assertEquals(setOf("basket-1"), baskets.keys)
        assertEquals(Item("pen", 3), baskets.get(camelContext, "basket-1")!!.message.body)
    }

    @Introspected
    data class Item(var name: String = "", var quantity: Int = 0)
}
