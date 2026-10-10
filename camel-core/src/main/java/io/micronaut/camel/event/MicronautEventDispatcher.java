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

import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Forwards the application events to the consumers of the {@code micronaut-event} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
final class MicronautEventDispatcher implements ApplicationEventListener<Object> {

    private final BeanProvider<MicronautEventComponent> component;

    MicronautEventDispatcher(BeanProvider<MicronautEventComponent> component) {
        this.component = component;
    }

    @Override
    public void onApplicationEvent(Object event) {
        component.get().dispatch(event);
    }
}
