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
package io.micronaut.camel.requires;

import io.micronaut.camel.annotation.CamelConfig;
import io.micronaut.core.annotation.Internal;

/**
 * Generates a requirement annotation in this package for every component, data format and language
 * of the Camel catalog.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
@CamelConfig(requirements = {CamelConfig.Kind.COMPONENT, CamelConfig.Kind.DATAFORMAT, CamelConfig.Kind.LANGUAGE})
final class CamelRequirements {

    private CamelRequirements() {
    }
}
