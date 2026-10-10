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
package io.micronaut.camel.docs.annotations

// tag::test[]
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.EndpointInject
import org.apache.camel.ProducerTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class AnnotationsTest {

    @field:EndpointInject("direct:orders") // <1>
    lateinit var orders: ProducerTemplate

    @Inject
    lateinit var checkout: Checkout

    @Inject
    lateinit var consumer: OrderConsumer

    @Test
    fun ordersReachTheConsumer() {
        checkout.checkout("book=40")
        orders.sendBody("pen=2")

        assertEquals(listOf(Price("book", 40), Price("pen", 2)), consumer.received)
    }
}
// end::test[]
