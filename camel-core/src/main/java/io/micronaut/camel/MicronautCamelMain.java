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
package io.micronaut.camel;

import io.micronaut.core.annotation.Internal;
import org.apache.camel.CamelContext;
import org.apache.camel.main.SimpleMain;

/**
 * Bootstraps Camel inside a Micronaut application with camel-main.
 *
 * <p>Initialization runs camel-main's configuration, which reads every {@code camel.*} option from
 * the Micronaut environment. Routes are collected later, in {@link #startRoutes()}, once the
 * {@link CamelContext} bean exists, so route builders can inject it.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautCamelMain extends SimpleMain {

    private volatile boolean routesEnabled;

    /**
     * @param camelContext The Camel context to bootstrap
     */
    MicronautCamelMain(CamelContext camelContext) {
        super(camelContext);
        // the Micronaut environment is the only source of properties
        setPropertyPlaceholderLocations("false");
        setRoutesCollector(new MicronautRoutesCollector());
    }

    /**
     * Collects the routes from the registry and the route resources, then starts Camel.
     *
     * @throws Exception if the routes cannot be added or Camel fails to start
     */
    void startRoutes() throws Exception {
        init();
        if (!routesEnabled) {
            routesEnabled = true;
            configureRoutes(camelContext);
        }
        start();
    }

    /**
     * Stops Camel and releases its resources.
     */
    void shutdownCamel() {
        try {
            stop();
        } finally {
            camelContext.shutdown();
        }
    }

    @Override
    protected void configureRoutes(CamelContext camelContext) throws Exception {
        if (routesEnabled) {
            super.configureRoutes(camelContext);
        }
    }
}
