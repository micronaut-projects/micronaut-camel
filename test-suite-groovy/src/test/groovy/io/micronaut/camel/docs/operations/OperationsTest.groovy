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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.ProducerTemplate
import spock.lang.Specification

@MicronautTest
@Property(name = 'endpoints.health.details-visible', value = 'ANONYMOUS')
@Property(name = 'camel.health.exposure-level', value = 'full')
@Property(name = 'camel.metrics.enabled', value = 'true')
class OperationsTest extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Inject
    ProducerTemplate producerTemplate

    @Inject
    MeterRegistry meterRegistry

    void 'custom health checks are reported'() {
        when:
        Map health = client.toBlocking().retrieve('/health', Map)
        Map inventory = health.details.camel.details.inventory

        then:
        inventory.status == 'UP'
        inventory.warehouse == 'main'
    }

    void 'metrics are recorded in the Micronaut registry'() {
        expect:
        producerTemplate.requestBody('direct:submit-order', '42', String) == 'Order 42 placed'
        meterRegistry.get('orders.placed').counter().count() >= 1
        meterRegistry.find('camel.route.policy').tag('routeId', 'submit-order').timer() != null
    }
}
