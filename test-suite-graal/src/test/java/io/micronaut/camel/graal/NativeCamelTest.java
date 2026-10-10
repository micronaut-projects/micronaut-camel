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
package io.micronaut.camel.graal;

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.Converter;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs in a native image with {@code nativeTest}, and on the JVM with {@code test}.
 */
@MicronautTest
class NativeCamelTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @EndpointInject("mock:priced")
    MockEndpoint priced;

    @Inject
    Shop shop;

    @Test
    void routesAndConfigurationWork() {
        assertEquals("native", camelContext.getName());
        assertEquals(42, camelContext.getComponent("seda", SedaComponent.class).getQueueSize());
        assertEquals("Hello Fred EUR", producerTemplate.requestBody("direct:greet", "Fred", String.class));
    }

    @Test
    void yamlRoutesAreFoundOnTheClasspath() {
        assertEquals("Hello from YAML, Fred", producerTemplate.requestBody("direct:yaml-greeting", "Fred", String.class));
    }

    @Test
    void beansAreInvokedByReflection() {
        assertEquals(40, producerTemplate.requestBody("direct:price", "book", Integer.class));
    }

    @Test
    void camelAnnotationsWork() throws Exception {
        priced.expectedBodiesReceived(new Price("pen", 3));

        shop.order("pen=3");

        priced.assertIsSatisfied();
        assertEquals("EUR", shop.currency);
    }

    public interface Orders {
        void send(String order);
    }

    public record Price(String item, int amount) {
    }

    @Singleton
    public static class Shop {

        @Produce("direct:orders")
        Orders orders;

        @PropertyInject("shop.currency")
        String currency;

        void order(String order) {
            orders.send(order);
        }
    }

    @Singleton
    public static class OrderConsumer {

        @Produce("mock:priced")
        ProducerTemplate priced;

        @Consume("direct:orders")
        public void receive(Price price) {
            priced.sendBody(price);
        }
    }

    @Converter
    public static class PriceConverters {

        @Converter
        public static Price toPrice(String value) {
            String[] parts = value.split("=");
            return new Price(parts[0], Integer.parseInt(parts[1]));
        }
    }

    @Singleton
    @ReflectiveAccess
    public static class PriceService {
        public int priceOf(String item) {
            return item.length() * 10;
        }
    }

    @Singleton
    public static class Routes extends RouteBuilder {

        private final PriceService priceService;

        public Routes(PriceService priceService) {
            this.priceService = priceService;
        }

        @Override
        public void configure() {
            from("direct:greet").setBody(simple("Hello ${body} {{shop.currency}}"));

            from("direct:price").bean(priceService, "priceOf");
        }
    }
}
