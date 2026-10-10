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
package io.micronaut.camel.processor;

import io.micronaut.annotation.processing.test.JavaParser;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelAnnotationsVisitorTest {

    private static final String SERVICE = """
        package test;

        import jakarta.inject.Singleton;
        import org.apache.camel.BeanInject;
        import org.apache.camel.Consume;
        import org.apache.camel.Endpoint;
        import org.apache.camel.EndpointInject;
        import org.apache.camel.Produce;
        import org.apache.camel.ProducerTemplate;
        import org.apache.camel.PropertyInject;

        @Singleton
        class OrderService {

            @EndpointInject("direct:orders")
            Endpoint orders;

            @Produce("direct:audit")
            ProducerTemplate audit;

            @BeanInject("pricing")
            Object pricing;

            @PropertyInject(value = "shop.currency", defaultValue = "EUR")
            String currency;

            @Consume("direct:in")
            public void receive(String body) {
            }
        }
        """;

    @Test
    void generatesAFactoryForTheEndpointInjections() {
        Map<String, String> sources = generate("test.OrderService", SERVICE);

        String factory = sources.get("OrderServiceCamelInjections.java");
        assertTrue(factory.contains("@Factory"), factory);
        assertTrue(factory.contains("@CamelInject(\n      owner = \"test.OrderService\"\n  )"), factory);
        assertTrue(factory.contains("@Secondary"), factory);
        assertTrue(factory.contains("public Endpoint inject0(InjectionPoint<?> injectionPoint, CamelContext camelContext)"), factory);
        assertTrue(factory.contains("public ProducerTemplate inject1("), factory);
        assertTrue(factory.contains("CamelInjections.inject(org.apache.camel.Endpoint.class, injectionPoint, camelContext)"), factory);
    }

    @Test
    void generatesATypeConverterLoader() {
        Map<String, String> sources = generate("test.MoneyConverters", """
            package test;

            import org.apache.camel.Converter;
            import org.apache.camel.Exchange;

            @Converter
            public class MoneyConverters {

                @Converter
                public static Integer toCents(String value) {
                    return Integer.parseInt(value) * 100;
                }

                @Converter
                public String toText(byte[] value, Exchange exchange) {
                    return new String(value);
                }
            }
            """);

        String loader = sources.get("MoneyConvertersTypeConverterLoader.java");
        assertTrue(loader.contains("extends GeneratedTypeConverterLoader"), loader);
        assertTrue(loader.contains("@Singleton"), loader);
        assertTrue(loader.contains("this.addConverter(registry, java.lang.String.class, java.lang.Integer.class);"), loader);
        assertTrue(loader.contains("this.addConverter(registry, byte[].class, java.lang.String.class);"), loader);
        assertTrue(loader.contains("if (value.getClass() == byte[].class)"), loader);
        assertTrue(loader.contains("MoneyConverters.toCents((String) value)"), loader);
        assertTrue(loader.contains("this.converters.toText((byte[]) value, exchange)"), loader);
    }

    @Test
    void classesWithoutCamelInjectionsGetNoFactory() {
        Map<String, String> sources = generate("test.Plain", """
            package test;

            import jakarta.inject.Singleton;
            import org.apache.camel.Consume;

            @Singleton
            class Plain {
                @Consume("direct:in")
                public void receive(String body) {
                }
            }
            """);

        assertFalse(sources.keySet().stream().anyMatch(name -> name.contains("CamelInjections")), sources::toString);
    }

    private static Map<String, String> generate(String className, String source) {
        try (JavaParser parser = new JavaParser()) {
            Iterable<? extends JavaFileObject> generated = parser.generate(className, source);
            return StreamSupport.stream(generated.spliterator(), false)
                .filter(file -> file.getName().endsWith(".java"))
                .collect(Collectors.toMap(
                    file -> file.getName().substring(file.getName().lastIndexOf('/') + 1),
                    CamelAnnotationsVisitorTest::read));
        }
    }

    private static String read(JavaFileObject file) {
        try {
            return file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
