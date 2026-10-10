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
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.support.DefaultRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The Camel registry of a Micronaut application.
 *
 * <p>Beans come from the Micronaut bean context first, then from beans Camel binds itself, such as
 * those declared in YAML routes. When Camel expects exactly one bean of a type, for example to
 * autowire a component option, and several Micronaut beans match, the {@code @Primary} bean is used.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class MicronautRegistry extends DefaultRegistry {

    private final MicronautBeanRepository micronautBeans;

    /**
     * @param micronautBeans The repository of Micronaut beans
     */
    public MicronautRegistry(MicronautBeanRepository micronautBeans) {
        super(micronautBeans);
        this.micronautBeans = micronautBeans;
    }

    @Override
    public <T> @Nullable T findSingleByType(Class<T> type) {
        Set<T> found = findByType(type);
        if (found.size() == 1) {
            return found.iterator().next();
        }
        if (found.isEmpty() || !onlyMicronautBeans(type)) {
            return null;
        }
        return micronautBeans.findSingleByType(type);
    }

    private <T> boolean onlyMicronautBeans(Class<T> type) {
        BeanRepository local = getLocalBeanRepository();
        return (local == null || local.findByType(type).isEmpty())
            && getSupplierRegistry().findByType(type).isEmpty()
            && getFallbackRegistry().findByType(type).isEmpty();
    }
}
