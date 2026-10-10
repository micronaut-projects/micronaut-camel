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
package io.micronaut.camel.examples.reactivestreams;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.DefaultCamelReactiveStreamsServiceFactory;
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;

/**
 * Makes the {@link CamelReactiveStreamsService} a bean, as camel-reactive-streams-starter does in Spring Boot.
 * <p>
 * The {@code reactive-streams} component looks the service up in the registry, by type, before it creates one,
 * so the component and the beans that inject the service share this instance, and Camel starts and stops it.
 * The bean cannot be created with {@code CamelReactiveStreams.get(camelContext)}: the component would find the
 * bean that is being created.
 */
@Factory
@Requires(env = "example-reactive-streams")
class ReactiveStreamsServiceFactory {

    @Singleton
    CamelReactiveStreamsService camelReactiveStreamsService(CamelContext camelContext) {
        return new DefaultCamelReactiveStreamsServiceFactory().newInstance(camelContext, new ReactiveStreamsEngineConfiguration());
    }
}
