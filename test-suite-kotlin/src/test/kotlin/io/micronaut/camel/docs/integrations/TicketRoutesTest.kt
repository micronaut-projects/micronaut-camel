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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.validation.ValidatorFactory
import org.apache.camel.CamelContext
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.component.bean.validator.BeanValidationException
import org.apache.camel.component.bean.validator.BeanValidatorComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@MicronautTest
class TicketRoutesTest {

    @Inject
    lateinit var producer: FluentProducerTemplate

    @Inject
    lateinit var camelContext: CamelContext

    @Inject
    lateinit var validatorFactory: ValidatorFactory

    @Test
    fun theComponentUsesMicronautValidation() {
        val component = camelContext.getComponent("bean-validator", BeanValidatorComponent::class.java)

        assertSame(validatorFactory, component.validatorFactory)
        assertFalse(validatorFactory.javaClass.name.contains("hibernate"))
    }

    @Test
    fun validTicketsAreOpened() {
        assertEquals("opened Broken build",
            producer.to("direct:open-ticket").withBody(Ticket("Broken build", "fred@example.com")).request(String::class.java))
    }

    @Test
    fun invalidTicketsAreRejected() {
        val exchange = producer.to("direct:open-ticket").withBody(Ticket(" ", "not an email")).send()

        val e = assertInstanceOf(BeanValidationException::class.java, exchange.exception)
        val invalid = e.constraintViolations.map { it.propertyPath.toString() }.toSet()
        assertEquals(setOf("title", "reporter"), invalid)
    }
}
