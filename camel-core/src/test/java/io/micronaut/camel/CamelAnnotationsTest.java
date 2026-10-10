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
package io.micronaut.camel;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
@Property(name = "spec.name", value = "CamelAnnotationsTest")
@Property(name = "shop.currency", value = "EUR")
class CamelAnnotationsTest {

    @EndpointInject("mock:orders")
    MockEndpoint orders;

    @Produce("direct:orders")
    ProducerTemplate ordersTemplate;

    @Inject
    CamelContext camelContext;

    @Inject
    OrderService orderService;

    @Inject
    OrderClient orderClient;

    @Test
    void endpointsAndTemplatesAreInjected() throws Exception {
        orders.expectedBodiesReceived("book");

        ordersTemplate.sendBody("book");

        orders.assertIsSatisfied();
        assertEquals("direct://orders", orderService.ordersEndpoint.getEndpointUri());
        assertNotNull(orderService.fluentTemplate);
    }

    @Test
    void producedInterfacesSendToTheirEndpoint() {
        assertEquals("priced book", orderClient.prices.price("book"));
    }

    @Test
    void beansAndPropertiesAreInjected() {
        assertSame(camelContext.getRegistry().lookupByName("pricing"), orderService.pricing);
        assertEquals("EUR", orderService.currency);
        assertEquals("standard", orderService.shipping);
    }

    @Test
    void consumeMethodsReceiveMessages() {
        assertEquals("priced book", camelContext.createProducerTemplate().requestBody("direct:price", "book", String.class));
    }

    @Test
    void converterClassesAreLoaded() {
        assertEquals(new Money(42, "EUR"), camelContext.getTypeConverter().convertTo(Money.class, "42 EUR"));
        assertEquals("42 EUR", camelContext.getTypeConverter().convertTo(String.class, new Money(42, "EUR")));
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelAnnotationsTest")
    public static class OrderRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:orders").to("mock:orders");
        }
    }

    public interface Prices {
        String price(String item);
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelAnnotationsTest")
    public static class OrderService {

        @EndpointInject("direct:orders")
        Endpoint ordersEndpoint;

        FluentProducerTemplate fluentTemplate;

        @BeanInject("pricing")
        Object pricing;

        @PropertyInject("shop.currency")
        String currency;

        @PropertyInject(value = "shop.shipping", defaultValue = "standard")
        String shipping;

        @EndpointInject("direct:orders")
        public void setFluentTemplate(FluentProducerTemplate fluentTemplate) {
            this.fluentTemplate = fluentTemplate;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelAnnotationsTest")
    public static class OrderClient {

        @Produce("direct:price")
        Prices prices;
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelAnnotationsTest")
    public static class PriceConsumer {

        @Consume("direct:price")
        public String price(String item) {
            return "priced " + item;
        }
    }

    @BindToRegistry("pricing")
    @Requires(property = "spec.name", value = "CamelAnnotationsTest")
    public static class Pricing {
    }

    public record Money(int amount, String currency) {
    }

    @Converter
    public static class MoneyConverters {

        @Converter
        public static Money toMoney(String value) {
            String[] parts = value.split(" ");
            return new Money(Integer.parseInt(parts[0]), parts[1]);
        }

        @Converter
        public String fromMoney(Money money) {
            return money.amount() + " " + money.currency();
        }
    }
}
