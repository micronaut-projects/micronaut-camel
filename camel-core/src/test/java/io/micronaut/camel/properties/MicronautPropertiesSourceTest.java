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

import io.micronaut.context.ApplicationContext;
import org.apache.camel.Ordered;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicronautPropertiesSourceTest {

    @Test
    void readsListsAsCommaSeparatedValues() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("camel.test.items", List.of("a", "b")))) {
            MicronautPropertiesSource source = new MicronautPropertiesSource(context.getEnvironment());

            assertEquals("a,b", source.getProperty("camel.test.items"));
            Properties properties = source.loadProperties();
            assertEquals("a,b", properties.getProperty("camel.test.items"));
            assertEquals(Ordered.HIGHEST, source.getOrder());
        }
    }
}
