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
package io.micronaut.camel.properties;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.value.PropertyResolver;

/**
 * Tells whether an option of a Camel component is set in the Micronaut configuration, so that the
 * integrations with Micronaut modules never override a {@code camel.component.<name>.<option>} setting.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class CamelComponentOptions {

    private CamelComponentOptions() {
    }

    /**
     * @param resolver  The property resolver, usually the environment
     * @param component The name of the component, such as {@code kafka}
     * @param options   The names of the options, in camel case, such as {@code connectionFactory}
     * @return Whether any of the options is set with {@code camel.component.<component>.<option>}
     */
    public static boolean isSet(PropertyResolver resolver, String component, String... options) {
        String prefix = "camel.component." + component + '.';
        for (String option : options) {
            if (resolver.containsProperty(prefix + NameUtils.hyphenate(option)) || resolver.containsProperty(prefix + option)) {
                return true;
            }
        }
        return false;
    }
}
