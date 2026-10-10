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
package io.micronaut.camel.docs.configuration

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.seda.SedaComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "camel.main.name", value = "shop")
@Property(name = "camel.component.seda.queue-size", value = "500")
@Property(name = "shop.welcome", value = "Bonjour")
class ConfigurationTest {

    @Inject
    lateinit var camelContext: CamelContext

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @Test
    fun camelIsConfiguredFromMicronautConfiguration() {
        assertEquals("shop", camelContext.name)
        assertEquals(500, camelContext.getComponent("seda", SedaComponent::class.java).queueSize)
    }

    @Test
    fun placeholdersResolveFromMicronautConfiguration() {
        assertEquals("Bonjour Fred", producerTemplate.requestBody("direct:welcome", "Fred", String::class.java))
    }
}
