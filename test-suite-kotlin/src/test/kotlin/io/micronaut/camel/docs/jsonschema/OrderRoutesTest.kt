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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.CamelExecutionException
import org.apache.camel.ProducerTemplate
import org.apache.camel.ValidationException
import org.apache.camel.component.mock.MockEndpoint
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MicronautTest
class OrderRoutesTest {

    @Inject
    lateinit var camelContext: CamelContext

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @Test
    fun validatesWithTheSchemaResource() = assertValidates("direct:checked-orders")

    @Test
    fun validatesWithTheSchemaOfTheType() = assertValidates("direct:typed-orders")

    @Test
    fun validatesTheInputType() = assertValidates("direct:contract-orders")

    private fun assertValidates(uri: String) {
        val orders = camelContext.getEndpoint("mock:valid-orders", MockEndpoint::class.java)
        orders.reset()
        orders.expectedMessageCount(1)

        producerTemplate.sendBody(uri, """{"id":"A-1","quantity":2}""")

        orders.assertIsSatisfied()
        val e = assertThrows(CamelExecutionException::class.java) {
            producerTemplate.sendBody(uri, """{"id":"","quantity":0}""")
        }
        assertInstanceOf(ValidationException::class.java, e.cause)
        orders.assertIsSatisfied()
    }
}
