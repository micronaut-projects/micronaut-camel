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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.apache.camel.FluentProducerTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification

/**
 * Runs Redis with Testcontainers.
 */
@MicronautTest(environments = 'docs-redis')
class GreetingCacheRoutesTest extends Specification implements TestPropertyProvider {

    @Shared
    GenericContainer<?> redis = new GenericContainer<>('redis:7.4-alpine').withExposedPorts(6379)

    @Inject
    FluentProducerTemplate producer

    @Inject
    RedisTemplate<Object, Object> redisTemplate

    @Override
    Map<String, String> getProperties() {
        redis.start()
        ['redis.uri': "redis://${redis.host}:${redis.getMappedPort(6379)}".toString()]
    }

    void cleanupSpec() {
        redis.stop()
    }

    void 'routes use the Redis of the application'() {
        when:
        producer.to('direct:remember-greeting').withHeader('name', 'fred').withBody('Hello Fred').send()

        then:
        producer.to('direct:recall-greeting').withHeader('name', 'fred').request(String) == 'Hello Fred'
        redisTemplate.opsForValue().get('greeting:fred') == 'Hello Fred'
    }
}
