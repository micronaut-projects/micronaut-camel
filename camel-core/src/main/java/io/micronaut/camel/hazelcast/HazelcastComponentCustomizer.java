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
package io.micronaut.camel.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.hazelcast.HazelcastDefaultComponent;
import org.apache.camel.spi.ComponentCustomizer;

/**
 * Sets the {@code hazelcastInstance} of the Camel Hazelcast components, such as {@code hazelcast-map}
 * and {@code hazelcast-queue}, to the {@link HazelcastInstance} of Micronaut Cache for Hazelcast,
 * unless {@code camel.component.<name>.hazelcast-instance} is set. Without it, each component would
 * start a Hazelcast member of its own.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(classes = {HazelcastDefaultComponent.class, HazelcastInstance.class})
@Requires(beans = HazelcastInstance.class)
@Requires(property = HazelcastComponentCustomizer.ENABLED, notEquals = StringUtils.FALSE)
final class HazelcastComponentCustomizer implements ComponentCustomizer {

    /**
     * The property that disables the integration with Micronaut Cache for Hazelcast when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.hazelcast.enabled";

    private final BeanProvider<HazelcastInstance> instances;
    private final Environment environment;

    /**
     * @param instances   The Hazelcast instances
     * @param environment The environment
     */
    HazelcastComponentCustomizer(BeanProvider<HazelcastInstance> instances, Environment environment) {
        this.instances = instances;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target instanceof HazelcastDefaultComponent;
    }

    @Override
    public void configure(String name, Component target) {
        HazelcastDefaultComponent component = (HazelcastDefaultComponent) target;
        if (component.getHazelcastInstance() == null
            && !CamelComponentOptions.isSet(environment, name, "hazelcastInstance")) {
            try {
                instances.find(null).ifPresent(component::setHazelcastInstance);
            } catch (NonUniqueBeanException e) {
                // several instances and none is primary: the routes must choose
            }
        }
    }
}
