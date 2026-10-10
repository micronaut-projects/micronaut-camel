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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.CamelExecutionException
import org.apache.camel.ProducerTemplate
import org.apache.camel.ValidationException
import org.apache.camel.component.mock.MockEndpoint
import spock.lang.Specification

@MicronautTest
class OrderRoutesTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    ProducerTemplate producerTemplate

    void 'validates orders against their schema with #uri'() {
        given:
        MockEndpoint orders = camelContext.getEndpoint('mock:valid-orders', MockEndpoint)
        orders.reset()
        orders.expectedMessageCount(1)

        when:
        producerTemplate.sendBody(uri, '{"id":"A-1","quantity":2}')

        then:
        orders.assertIsSatisfied()

        when:
        producerTemplate.sendBody(uri, '{"id":"","quantity":0}')

        then:
        CamelExecutionException e = thrown()
        e.cause instanceof ValidationException
        orders.assertIsSatisfied()

        where:
        uri << ['direct:checked-orders', 'direct:typed-orders', 'direct:contract-orders']
    }
}
