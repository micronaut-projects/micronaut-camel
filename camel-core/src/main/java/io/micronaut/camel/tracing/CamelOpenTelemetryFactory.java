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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import jakarta.inject.Singleton;
import org.apache.camel.opentelemetry2.OpenTelemetryTracer;

/**
 * Exposes the context propagators of the Micronaut {@link OpenTelemetry} instance to Camel.
 *
 * <p>camel-main enables Camel's OpenTelemetry tracing with {@code camel.opentelemetry2.enabled=true}.
 * Camel's tracer then looks up a single {@code io.opentelemetry.api.trace.Tracer} and a single
 * {@link ContextPropagators} in the registry, and falls back to {@code GlobalOpenTelemetry} when there is
 * none. Micronaut provides the tracer, and this factory provides the propagators, so Camel's spans are
 * created and propagated by the same OpenTelemetry instance as Micronaut's, and join the current
 * Micronaut trace.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@Requires(classes = {OpenTelemetry.class, OpenTelemetryTracer.class})
@Requires(beans = OpenTelemetry.class)
@Internal
final class CamelOpenTelemetryFactory {

    /**
     * @param openTelemetry The Micronaut OpenTelemetry instance
     * @return Its context propagators
     */
    @Singleton
    @Requires(missingBeans = ContextPropagators.class)
    ContextPropagators camelContextPropagators(OpenTelemetry openTelemetry) {
        return openTelemetry.getPropagators();
    }
}
