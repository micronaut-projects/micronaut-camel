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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.spi.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelBootstrapTest {

    private static ApplicationContext run(Map<String, Object> properties) {
        Map<String, Object> all = new java.util.HashMap<>(properties);
        all.put("spec.name", "CamelBootstrapTest");
        return ApplicationContext.run(all);
    }

    @Test
    void yamlRoutesAreLoadedFromTheIncludePattern() {
        try (ApplicationContext context = run(Map.of("camel.main.routes-include-pattern", "classpath:yaml-routes/*.yaml"))) {
            ProducerTemplate template = context.getBean(ProducerTemplate.class);

            assertEquals("Hello from YAML, Fred", template.requestBody("direct:yaml", "Fred", String.class));
        }
    }

    @Test
    void lambdaRouteBuildersAreCollected() {
        try (ApplicationContext context = run(Map.of())) {
            ProducerTemplate template = context.getBean(ProducerTemplate.class);

            assertEquals("lambda:Fred", template.requestBody("direct:lambda", "Fred", String.class));
        }
    }

    @Test
    void lambdaEndpointRouteBuildersAreCollected() {
        try (ApplicationContext context = run(Map.of())) {
            ProducerTemplate template = context.getBean(ProducerTemplate.class);

            assertEquals("endpoint-dsl:Fred", template.requestBody("direct:endpoint-dsl", "Fred", String.class));
        }
    }

    @Test
    void singleBeanLookupsPreferThePrimaryBean() {
        try (ApplicationContext context = run(Map.of())) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            UuidGenerator primary = camelContext.getRegistry().findSingleByType(UuidGenerator.class);

            assertSame(context.getBean(UuidGenerator.class), primary);
            assertEquals("primary", primary.generateUuid());
        }
    }

    @Test
    void mainListenerBeansAreNotified() {
        try (ApplicationContext context = run(Map.of())) {
            RecordingMainListener listener = context.getBean(RecordingMainListener.class);

            assertTrue(listener.events.containsAll(List.of("beforeConfigure", "afterConfigure", "beforeStart", "afterStart")),
                listener.events::toString);
        }
    }

    @Test
    void micronautConvertersAreAFallbackForCamel() {
        try (ApplicationContext context = run(Map.of())) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertEquals(new Money(42), camelContext.getTypeConverter().convertTo(Money.class, "42 EUR"));
        }
    }

    @Test
    void camelConvertsJdkTypesItself() {
        try (ApplicationContext context = run(Map.of("camel.variable.greeting", "Hello"))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertNull(camelContext.getTypeConverter().tryConvertTo(Boolean.class, "Hello"));
            assertEquals("Hello", camelContext.getVariable("greeting"));
        }
    }

    @Test
    void camelStopsWithTheApplication() {
        ApplicationContext context = run(Map.of());
        CamelContext camelContext = context.getBean(CamelContext.class);
        assertEquals(ServiceStatus.Started, camelContext.getStatus());

        context.close();

        assertEquals(ServiceStatus.Stopped, camelContext.getStatus());
    }

    @Test
    void applicationsWithoutAServerAreKeptAlive() {
        try (ApplicationContext context = run(Map.of())) {
            EmbeddedApplication<?> application = context.getBean(EmbeddedApplication.class);

            assertInstanceOf(CamelApplication.class, application);
            assertTrue(application.isServer());
        }
    }

    @Test
    void theBootstrapBeansAreNotInTheCamelRegistry() {
        try (ApplicationContext context = run(Map.of())) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            assertTrue(camelContext.getRegistry().findByType(BaseMainSupport.class).isEmpty());
            assertTrue(camelContext.getRegistry().findByType(CamelContext.class).isEmpty());
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "CamelBootstrapTest")
    static class TestBeans {

        @Singleton
        LambdaRouteBuilder lambdaRoutes() {
            return rb -> rb.from("direct:lambda").setBody(rb.simple("lambda:${body}"));
        }

        @Singleton
        LambdaEndpointRouteBuilder lambdaEndpointRoutes() {
            return rb -> rb.from(rb.direct("endpoint-dsl")).setBody(rb.simple("endpoint-dsl:${body}"));
        }

        @Singleton
        @Primary
        UuidGenerator fixedUuidGenerator() {
            return new FixedUuidGenerator("primary");
        }

        @Singleton
        @Named("other")
        UuidGenerator otherUuidGenerator() {
            return new FixedUuidGenerator("other");
        }

        @Singleton
        @Bean(typed = {TypeConverter.class})
        TypeConverter<String, Money> moneyConverter() {
            return TypeConverter.of(String.class, Money.class,
                value -> new Money(Integer.parseInt(value.split(" ")[0])));
        }
    }

    record FixedUuidGenerator(String id) implements UuidGenerator {
        @Override
        public String generateUuid() {
            return id;
        }
    }

    record Money(int amount) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelBootstrapTest")
    static class RecordingMainListener extends MainListenerSupport {

        final List<String> events = new ArrayList<>();

        @Override
        public void beforeConfigure(BaseMainSupport main) {
            events.add("beforeConfigure");
        }

        @Override
        public void afterConfigure(BaseMainSupport main) {
            events.add("afterConfigure");
        }

        @Override
        public void beforeStart(BaseMainSupport main) {
            events.add("beforeStart");
        }

        @Override
        public void afterStart(BaseMainSupport main) {
            events.add("afterStart");
        }
    }
}
