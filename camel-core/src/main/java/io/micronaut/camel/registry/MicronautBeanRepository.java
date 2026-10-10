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

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.BeanRepository;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Camel {@link BeanRepository} that looks beans up in the Micronaut {@link BeanContext}.
 *
 * <p>A bean is found by name when its {@code @Named} qualifier matches, or, for a bean without one,
 * when its simple class name matches ignoring case, so {@code myBean} finds a bean of type
 * {@code MyBean}. Lookups by type that expect a single bean honour {@code @Primary}.</p>
 *
 * <p>The {@link CamelContext} and the beans that bootstrap it are never returned, so that Camel can
 * look up beans while the context is being created. Beans may be adapted for Camel by a
 * {@link CamelBeanAdapter}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class MicronautBeanRepository implements BeanRepository {

    private final BeanContext beanContext;
    private final Map<String, Optional<BeanDefinition<?>>> definitionsByName = new ConcurrentHashMap<>();
    private volatile @Nullable List<CamelBeanAdapter> adapters;

    /**
     * @param beanContext The bean context
     */
    public MicronautBeanRepository(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public @Nullable Object lookupByName(String name) {
        return definitionByName(name).map(this::getBean).orElse(null);
    }

    @Override
    public <T> @Nullable T lookupByNameAndType(String name, Class<T> type) {
        Optional<BeanDefinition<?>> definition = definitionByName(name);
        if (definition.isPresent() && type.isAssignableFrom(definition.get().getBeanType())) {
            return getBean(definition.get(), type);
        }
        Collection<BeanDefinition<T>> candidates = candidates(type, name);
        if (candidates.size() == 1) {
            return getBean(candidates.iterator().next(), type);
        }
        return null;
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> beans = new LinkedHashMap<>();
        for (BeanDefinition<T> definition : definitions(type)) {
            beans.put(nameOf(definition), getBean(definition, type));
        }
        return beans;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Set<T> beans = new LinkedHashSet<>();
        for (BeanDefinition<T> definition : definitions(type)) {
            beans.add(getBean(definition, type));
        }
        return beans;
    }

    @Override
    public <T> @Nullable T findSingleByType(Class<T> type) {
        List<BeanDefinition<T>> definitions = definitions(type);
        if (definitions.isEmpty()) {
            return null;
        }
        if (definitions.size() == 1) {
            return getBean(definitions.get(0), type);
        }
        try {
            return beanContext.findBean(type).map(bean -> adapt(bean, type)).orElse(null);
        } catch (NonUniqueBeanException e) {
            return null;
        }
    }

    /**
     * The name Camel sees for a bean: its {@code @Named} value, or its simple class name with the first
     * letter in lower case.
     *
     * @param definition The bean definition
     * @return The name
     */
    static String nameOf(BeanDefinition<?> definition) {
        return definition.getAnnotationMetadata()
            .stringValue(AnnotationUtil.NAMED)
            .orElseGet(() -> NameUtils.decapitalize(definition.getBeanType().getSimpleName()));
    }

    private Optional<BeanDefinition<?>> definitionByName(String name) {
        return definitionsByName.computeIfAbsent(name, n -> {
            Collection<BeanDefinition<Object>> candidates = candidates(Object.class, n);
            if (candidates.size() == 1) {
                return Optional.of(candidates.iterator().next());
            }
            // prefer an explicit @Named qualifier over a match on the class name
            List<BeanDefinition<Object>> named = candidates.stream()
                .filter(d -> d.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).filter(n::equals).isPresent())
                .toList();
            return named.size() == 1 ? Optional.of(named.get(0)) : Optional.empty();
        });
    }

    private <T> Collection<BeanDefinition<T>> candidates(Class<T> type, String name) {
        return beanContext.getBeanDefinitions(type, Qualifiers.byName(name)).stream()
            .filter(MicronautBeanRepository::isVisible)
            .toList();
    }

    private <T> List<BeanDefinition<T>> definitions(Class<T> type) {
        return beanContext.getBeanDefinitions(type).stream()
            .filter(MicronautBeanRepository::isVisible)
            .toList();
    }

    private Object getBean(BeanDefinition<?> definition) {
        return getBean(definition, Object.class);
    }

    private <T> T getBean(BeanDefinition<?> definition, Class<T> type) {
        return adapt(beanContext.getBean(definition), type);
    }

    @SuppressWarnings("unchecked")
    private <T> T adapt(Object bean, Class<T> type) {
        List<CamelBeanAdapter> beanAdapters = adapters;
        if (beanAdapters == null) {
            beanAdapters = List.copyOf(beanContext.getBeansOfType(CamelBeanAdapter.class));
            adapters = beanAdapters;
        }
        Object result = bean;
        for (CamelBeanAdapter adapter : beanAdapters) {
            Object adapted = adapter.adapt(result);
            if (type.isInstance(adapted)) {
                result = adapted;
            }
        }
        return (T) result;
    }

    private static boolean isVisible(BeanDefinition<?> definition) {
        Class<?> beanType = definition.getBeanType();
        return !CamelContext.class.isAssignableFrom(beanType)
            && !definition.hasStereotype(CamelInternal.class);
    }
}
