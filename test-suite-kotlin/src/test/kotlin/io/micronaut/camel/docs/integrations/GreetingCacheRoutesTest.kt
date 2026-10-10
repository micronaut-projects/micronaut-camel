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
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.apache.camel.FluentProducerTemplate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.core.RedisTemplate
import org.testcontainers.containers.GenericContainer

/**
 * Runs Redis with Testcontainers.
 */
@MicronautTest(environments = ["docs-redis"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GreetingCacheRoutesTest : TestPropertyProvider {

    private val redis = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)

    @Inject
    lateinit var producer: FluentProducerTemplate

    @Inject
    lateinit var redisTemplate: RedisTemplate<Any, Any>

    override fun getProperties(): Map<String, String> {
        redis.start()
        return mapOf("redis.uri" to "redis://${redis.host}:${redis.getMappedPort(6379)}")
    }

    @AfterAll
    fun stopRedis() {
        redis.stop()
    }

    @Test
    fun routesUseTheRedisOfTheApplication() {
        producer.to("direct:remember-greeting").withHeader("name", "fred").withBody("Hello Fred").send()

        assertEquals("Hello Fred", producer.to("direct:recall-greeting").withHeader("name", "fred").request(String::class.java))
        assertEquals("Hello Fred", redisTemplate.opsForValue().get("greeting:fred"))
    }
}
