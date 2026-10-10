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
package io.micronaut.camel.docs.transactions

import groovy.transform.Canonical
import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.CamelContext
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository
import org.apache.camel.support.DefaultExchange
import spock.lang.Specification

@MicronautTest(transactional = false)
class OrderAggregationsTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    @Named('orderAggregations')
    JdbcAggregationRepository repository

    void 'stores exchanges as JSON'() {
        given:
        // started by Camel when a route uses it
        camelContext.addService(repository)
        def exchange = new DefaultExchange(camelContext)
        exchange.message.body = new Line('book', 2)
        exchange.message.setHeader('customer', 'Fred')

        when:
        repository.add(camelContext, 'basket-1', exchange)
        def stored = repository.get(camelContext, 'basket-1')

        then:
        stored.message.body == new Line('book', 2)
        stored.message.getHeader('customer') == 'Fred'

        cleanup:
        repository.remove(camelContext, 'basket-1', stored)
        repository.confirm(camelContext, stored.exchangeId)
    }

    @Introspected
    @Canonical
    static class Line {
        String item
        int quantity
    }
}
