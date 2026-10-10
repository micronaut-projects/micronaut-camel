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

import groovy.transform.Canonical
import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.CamelContext
import org.apache.camel.spi.AggregationRepository
import org.apache.camel.support.DefaultExchange
import spock.lang.Specification

@MicronautTest
class BasketRepositoriesTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    @Named('baskets')
    AggregationRepository baskets

    void 'stores exchanges in the cache'() {
        given:
        def exchange = new DefaultExchange(camelContext)
        exchange.message.body = new Item('pen', 3)

        when:
        baskets.add(camelContext, 'basket-1', exchange)

        then:
        baskets.keys == ['basket-1'] as Set
        baskets.get(camelContext, 'basket-1').message.body == new Item('pen', 3)
    }

    @Introspected
    @Canonical
    static class Item {
        String name
        int quantity
    }
}
