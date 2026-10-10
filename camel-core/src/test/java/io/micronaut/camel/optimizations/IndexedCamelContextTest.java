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
package io.micronaut.camel.optimizations;

import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedCamelContextTest {

    @Test
    void findsExtensionsAndTypeConverterLoadersInTheIndex() throws Exception {
        CamelServiceIndex index = new CamelServiceIndex(
            Map.of("component/indexed-seda", SedaComponent.class.getName()),
            Map.of("TypeConverterLoader", List.of("org.apache.camel.converter.CamelBaseBulkConverterLoader", RecordingLoader.class.getName()))
        );
        try (IndexedCamelContext camelContext = new IndexedCamelContext(index)) {
            camelContext.start();

            // only the index names this component
            assertInstanceOf(SedaComponent.class, camelContext.getComponent("indexed-seda"));
            // the other components are still found on the classpath
            assertInstanceOf(SedaComponent.class, camelContext.getComponent("seda"));
            assertTrue(RecordingLoader.loaded);
            assertEquals(42, camelContext.getTypeConverter().convertTo(Integer.class, "42"));
        }
    }

    public static class RecordingLoader implements TypeConverterLoader {
        static volatile boolean loaded;

        @Override
        public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
            loaded = true;
        }
    }
}
