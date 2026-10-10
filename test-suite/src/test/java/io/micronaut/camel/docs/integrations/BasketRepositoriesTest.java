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
package io.micronaut.camel.docs.integrations;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class BasketRepositoriesTest {

    @Inject
    CamelContext camelContext;

    @Inject
    @Named("baskets")
    AggregationRepository baskets;

    @Test
    void storesExchangesInTheCache() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Item("pen", 3));

        baskets.add(camelContext, "basket-1", exchange);

        assertEquals(Set.of("basket-1"), baskets.getKeys());
        assertEquals(new Item("pen", 3), baskets.get(camelContext, "basket-1").getMessage().getBody());
    }

    @Introspected
    public record Item(String name, int quantity) {
    }
}
