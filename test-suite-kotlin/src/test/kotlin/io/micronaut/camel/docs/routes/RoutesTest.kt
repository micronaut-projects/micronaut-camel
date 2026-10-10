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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.ProducerTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class RoutesTest {

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @Test
    fun routeBuilderBeansCanInjectBeans() {
        assertEquals(40, producerTemplate.requestBody("direct:price", "book", Int::class.javaObjectType))
    }

    @Test
    fun lambdaRouteBuildersAreCollected() {
        assertEquals("shipped book", producerTemplate.requestBody("direct:ship", "book", String::class.java))
        assertEquals("tracking book", producerTemplate.requestBody("direct:track", "book", String::class.java))
    }

    @Test
    fun yamlRoutesAreLoaded() {
        assertEquals("notified Fred", producerTemplate.requestBody("direct:notify", "Fred", String::class.java))
    }
}
