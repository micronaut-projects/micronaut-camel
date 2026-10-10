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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import jakarta.inject.Singleton;
import org.apache.camel.cli.connector.DefaultCliConnectorFactory;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The {@link CliConnectorFactory} of Micronaut applications, which camel-main finds in the registry:
 * the Camel CLI reports the application as a Micronaut application, and its {@code camel stop}
 * command closes the application context instead of only stopping Camel.
 *
 * <p>Camel enables its connector whenever {@code camel-cli-connector} is on the classpath; with
 * {@code camel.cli.enabled=false}, as with Camel Spring Boot, this factory disables it.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = DefaultCliConnectorFactory.class)
final class MicronautCliConnectorFactory extends DefaultCliConnectorFactory {

    /**
     * The property that disables the CLI connector when {@code false}.
     */
    static final String ENABLED = "camel.cli.enabled";

    private final ApplicationContext applicationContext;

    /**
     * @param applicationContext The application context, which the CLI closes
     */
    MicronautCliConnectorFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        setEnabled(applicationContext.getProperty(ENABLED, Boolean.class, true));
        setRuntime("Micronaut");
        setRuntimeVersion(VersionUtils.getMicronautVersion());
        setRuntimeStartClass(mainClass());
    }

    @Override
    public @Nullable CliConnector createConnector() {
        return isEnabled() ? new MicronautCliConnector(this, applicationContext) : null;
    }

    /**
     * @return The class that started the application, from the command of the JVM, or the
     * {@code Main-Class} of the JAR that it runs
     */
    static @Nullable String mainClass() {
        String command = System.getProperty("sun.java.command");
        if (StringUtils.isEmpty(command)) {
            return null;
        }
        String main = command.trim().split("\\s+")[0];
        if (!main.endsWith(".jar")) {
            return main;
        }
        File jar = new File(main);
        if (!jar.isFile()) {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            return manifest != null ? manifest.getMainAttributes().getValue("Main-Class") : null;
        } catch (IOException e) {
            return null;
        }
    }
}
