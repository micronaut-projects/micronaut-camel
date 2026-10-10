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
package io.micronaut.camel.tracing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.OpenTelemetryTracer;
import org.apache.camel.spi.CamelTracingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "CamelTracingTest")
@Property(name = "camel.opentelemetry2.enabled", value = "true")
class CamelTracingTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    Tracer tracer;

    @Inject
    InMemorySpanExporter exporter;

    @Test
    void camelSpansJoinTheMicronautTrace() {
        assertInstanceOf(OpenTelemetryTracer.class, camelContext.hasService(CamelTracingService.class));

        Span parent = tracer.spanBuilder("checkout").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            producerTemplate.sendBody("direct:checkout", "order");
        } finally {
            parent.end();
        }

        String parentId = parent.getSpanContext().getSpanId();
        List<SpanData> camelSpans = exporter.getFinishedSpanItems().stream()
            .filter(span -> !span.getSpanId().equals(parentId))
            .toList();
        assertFalse(camelSpans.isEmpty(), "Camel spans are exported by the Micronaut OpenTelemetry instance");
        // every Camel span belongs to the Micronaut trace
        assertTrue(camelSpans.stream().allMatch(span -> span.getTraceId().equals(parent.getSpanContext().getTraceId())));
        assertTrue(camelSpans.stream().anyMatch(span -> span.getParentSpanId().equals(parentId)));
        assertTrue(camelSpans.stream().anyMatch(span -> span.getName().equals("checkout")), () -> camelSpans.toString());
    }

    @Factory
    @Requires(property = "spec.name", value = "CamelTracingTest")
    static class ExporterFactory {

        @Singleton
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }

        @Singleton
        SpanProcessor spanProcessor(InMemorySpanExporter exporter) {
            return SimpleSpanProcessor.create(exporter);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CamelTracingTest")
    static class CheckoutRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:checkout").routeId("checkout")
                .to("log:checkout");
        }
    }
}
