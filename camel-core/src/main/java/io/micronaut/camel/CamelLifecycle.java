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

import io.micronaut.camel.inject.CamelConsumeMethods;
import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;

/**
 * Starts Camel when the application starts and stops it when the application stops.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
final class CamelLifecycle {

    private final MicronautCamelMain main;
    private final BeanProvider<CamelContext> camelContext;
    private final CamelConsumeMethods consumeMethods;

    /**
     * @param main         The camel-main bootstrap
     * @param camelContext   The Camel context
     * @param consumeMethods The methods annotated with {@code @Consume}
     */
    CamelLifecycle(MicronautCamelMain main, BeanProvider<CamelContext> camelContext, CamelConsumeMethods consumeMethods) {
        this.main = main;
        this.camelContext = camelContext;
        this.consumeMethods = consumeMethods;
    }

    /**
     * Adds the routes and starts Camel.
     *
     * @param event The startup event
     */
    @EventListener
    void onStartup(StartupEvent event) {
        // create the context bean first, so beans collected as routes can inject it
        CamelContext context = camelContext.get();
        try {
            consumeMethods.subscribe(context);
            main.startRoutes();
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    /**
     * Stops Camel before the beans it uses are destroyed.
     *
     * @param event The shutdown event
     */
    @EventListener
    void onShutdown(ShutdownEvent event) {
        main.shutdownCamel();
    }
}
