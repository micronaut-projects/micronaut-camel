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
package io.micronaut.camel.registry;

import io.micronaut.core.annotation.Internal;

/**
 * Adapts a Micronaut bean before the Camel registry returns it, for beans that Camel must use
 * differently from Micronaut code. An adapter bean should be annotated with {@link CamelInternal}.
 *
 * <p>For example, Micronaut Data wraps each {@code DataSource} so that connections are only available
 * inside a Micronaut transaction or connection, while Camel components open connections themselves.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public interface CamelBeanAdapter {

    /**
     * Adapts a bean. The registry only uses the result when it is an instance of the type Camel asked
     * for, so an adapter can return a different implementation of the bean's interface.
     *
     * @param bean The bean from the Micronaut bean context
     * @return The bean that Camel uses, or the given bean when it is not adapted
     */
    Object adapt(Object bean);
}
