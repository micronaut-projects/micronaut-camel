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
package io.micronaut.camel.event;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The {@code micronaut-event} Camel component. Its producers publish the message body as a Micronaut
 * application event, and its consumers receive the application events of a type:
 * {@code micronaut-event:com.example.OrderPlaced}, or every event with {@code micronaut-event:all}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Named(MicronautEventComponent.SCHEME)
public class MicronautEventComponent extends DefaultComponent {

    /**
     * The scheme of the component.
     */
    public static final String SCHEME = "micronaut-event";

    private final ApplicationEventPublisher<Object> publisher;
    private final Set<MicronautEventConsumer> consumers = new CopyOnWriteArraySet<>();

    /**
     * @param publisher The application event publisher
     */
    public MicronautEventComponent(ApplicationEventPublisher<Object> publisher) {
        this.publisher = publisher;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Class<?> eventType = remaining.isEmpty() || "all".equals(remaining)
            ? Object.class
            : getCamelContext().getClassResolver().resolveMandatoryClass(remaining);
        MicronautEventEndpoint endpoint = new MicronautEventEndpoint(uri, this, eventType);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    final ApplicationEventPublisher<Object> publisher() {
        return publisher;
    }

    final void register(MicronautEventConsumer consumer) {
        consumers.add(consumer);
    }

    final void unregister(MicronautEventConsumer consumer) {
        consumers.remove(consumer);
    }

    /**
     * Delivers an application event to the consumers of its type.
     *
     * @param event The event
     */
    final void dispatch(Object event) {
        for (MicronautEventConsumer consumer : consumers) {
            consumer.onEvent(event);
        }
    }
}
