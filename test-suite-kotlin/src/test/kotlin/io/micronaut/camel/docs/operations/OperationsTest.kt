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
package io.micronaut.camel.docs.operations

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.ProducerTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "endpoints.health.details-visible", value = "ANONYMOUS")
@Property(name = "camel.health.exposure-level", value = "full")
@Property(name = "camel.metrics.enabled", value = "true")
// the file consumer of the docs.integrations routes is DOWN until its first poll
@Property(name = "camel.health.initial-state", value = "up")
class OperationsTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    @Suppress("UNCHECKED_CAST")
    fun customHealthChecksAreReported() {
        val health = client.toBlocking().retrieve("/health", Map::class.java) as Map<String, Any>
        val camel = (health["details"] as Map<String, Any>)["camel"] as Map<String, Any>
        val inventory = (camel["details"] as Map<String, Any>)["inventory"] as Map<String, Any>

        assertEquals("UP", inventory["status"])
        assertEquals("main", inventory["warehouse"])
    }

    @Test
    fun metricsAreRecordedInTheMicronautRegistry() {
        assertEquals("Order 42 placed", producerTemplate.requestBody("direct:submit-order", "42", String::class.java))

        assertTrue(meterRegistry.get("orders.placed").counter().count() >= 1)
        assertNotNull(meterRegistry.find("camel.route.policy").tag("routeId", "submit-order").timer())
    }
}
