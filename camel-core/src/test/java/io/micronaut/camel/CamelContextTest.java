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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "CamelContextTest")
@Property(name = "camel.main.name", value = "orders")
@Property(name = "greeting.prefix", value = "Hello")
@Property(name = "camel.component.seda.queue-size", value = "42")
class CamelContextTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    FluentProducerTemplate fluentProducerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    @Test
    void contextIsStartedAndConfiguredFromMicronautProperties() {
        assertEquals(ServiceStatus.Started, camelContext.getStatus());
        assertEquals("orders", camelContext.getName());
        assertTrue(camelContext.getExecutorServiceManager().getThreadNamePattern().contains("orders"));
    }

    @Test
    void routeBuilderBeansAreStarted() throws Exception {
        MockEndpoint greetings = camelContext.getEndpoint("mock:greetings", MockEndpoint.class);
        greetings.expectedBodiesReceived("Hello Fred!");

        producerTemplate.sendBody("direct:greet", "Fred");

        greetings.assertIsSatisfied();
    }

    @Test
    void beansAreFoundByNameAndByClassName() {
        assertEquals("Hello Fred!", fluentProducerTemplate.to("direct:named").withBody("Fred").request(String.class));
        assertEquals("HELLO FRED", fluentProducerTemplate.to("direct:by-class-name").withBody("Hello Fred").request(String.class));
    }

    @Test
    void componentsAreConfiguredFromMicronautProperties() {
        assertEquals(42, camelContext.getComponent("seda", SedaComponent.class).getQueueSize());
    }

    @Test
    void routeBuildersCanInjectTheCamelBeans(GreetingRoutes routes) {
        assertSame(camelContext, routes.camelContext);
        assertNotNull(routes.producerTemplate);
    }

    @Test
    void consumerTemplateReceives() {
        producerTemplate.sendBody("seda:inbox", "ping");

        assertEquals("ping", consumerTemplate.receiveBody("seda:inbox", 5000, String.class));
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelContextTest")
    static class GreetingRoutes extends RouteBuilder {

        final CamelContext camelContext;
        final ProducerTemplate producerTemplate;

        GreetingRoutes(CamelContext camelContext, ProducerTemplate producerTemplate) {
            this.camelContext = camelContext;
            this.producerTemplate = producerTemplate;
        }

        @Override
        public void configure() {
            from("direct:greet")
                .setBody(simple("{{greeting.prefix}} ${body}!"))
                .to("mock:greetings");

            from("direct:named").bean("greeter", "greet");

            from("direct:by-class-name").bean("shouter", "shout");
        }
    }

    @Singleton
    @Named("greeter")
    @Requires(property = "spec.name", value = "CamelContextTest")
    public static class GreetingService {
        public String greet(String name) {
            return "Hello " + name + "!";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelContextTest")
    public static class Shouter {
        public String shout(String text) {
            return text.toUpperCase();
        }
    }
}
