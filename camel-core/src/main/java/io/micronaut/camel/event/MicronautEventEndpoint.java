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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.DefaultEndpoint;

/**
 * An endpoint of the {@code micronaut-event} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@UriEndpoint(scheme = MicronautEventComponent.SCHEME, title = "Micronaut Event", syntax = "micronaut-event:type",
    category = {Category.CORE})
public class MicronautEventEndpoint extends DefaultEndpoint {

    private final Class<?> eventType;

    /**
     * @param uri       The URI
     * @param component The component
     * @param eventType The type of the events
     */
    MicronautEventEndpoint(String uri, MicronautEventComponent component, Class<?> eventType) {
        super(uri, component);
        this.eventType = eventType;
    }

    @Override
    public MicronautEventComponent getComponent() {
        return (MicronautEventComponent) super.getComponent();
    }

    /**
     * @return The type of the events, {@code Object} for all events
     */
    public Class<?> getEventType() {
        return eventType;
    }

    @Override
    public Producer createProducer() {
        return new MicronautEventProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        MicronautEventConsumer consumer = new MicronautEventConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }
}
