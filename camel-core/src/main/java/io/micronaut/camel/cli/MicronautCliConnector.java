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
package io.micronaut.camel.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import org.apache.camel.cli.connector.LocalCliConnector;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel CLI connector of a Micronaut application. When the CLI stops the application, with
 * {@code camel stop}, the connector closes the application context, which stops Camel, rather than
 * only stopping the {@link org.apache.camel.CamelContext} and leaving the application running.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautCliConnector extends LocalCliConnector {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautCliConnector.class);

    private final ApplicationContext applicationContext;

    /**
     * @param factory            The factory
     * @param applicationContext The application context to close when the CLI stops the application
     */
    MicronautCliConnector(CliConnectorFactory factory, ApplicationContext applicationContext) {
        super(factory);
        this.applicationContext = applicationContext;
    }

    @Override
    public void sigterm() {
        // the connector's own thread calls this method, and closing the context stops the connector:
        // close it from another thread
        Thread terminate = new Thread(this::closeApplicationContext, "camel-cli-terminate");
        terminate.setDaemon(false);
        terminate.start();
    }

    private void closeApplicationContext() {
        LOG.info("Camel CLI connector closing the application context");
        // as LocalCliConnector does: detach a debugger before Camel stops
        BacklogDebugger debugger = getCamelContext().hasService(BacklogDebugger.class);
        if (debugger instanceof ShutdownPrepared prepared) {
            prepared.prepareShutdown(false, true);
        }
        ServiceHelper.stopAndShutdownServices(debugger);
        if (applicationContext.isRunning()) {
            applicationContext.close();
        }
    }
}
