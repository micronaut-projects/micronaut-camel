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

import io.micronaut.camel.registry.CamelInternal;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;

/**
 * Keeps an application that runs Camel without an HTTP server alive until it is shut down, as an
 * HTTP server would. Camel's own threads are daemon threads, so without it the JVM would exit as
 * soon as startup completes.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@CamelInternal
@Internal
@Requires(missingBeans = EmbeddedServer.class)
final class CamelApplication implements EmbeddedApplication<CamelApplication> {

    private final ApplicationContext applicationContext;
    private final ApplicationConfiguration applicationConfiguration;
    private volatile boolean running;

    /**
     * @param applicationContext       The application context
     * @param applicationConfiguration The application configuration
     */
    CamelApplication(ApplicationContext applicationContext, ApplicationConfiguration applicationConfiguration) {
        this.applicationContext = applicationContext;
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration() {
        return applicationConfiguration;
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public boolean isRunning() {
        return running && applicationContext.isRunning();
    }

    @Override
    public CamelApplication start() {
        if (!applicationContext.isRunning()) {
            applicationContext.start();
        }
        running = true;
        return this;
    }

    @Override
    public CamelApplication stop() {
        running = false;
        return EmbeddedApplication.super.stop();
    }
}
