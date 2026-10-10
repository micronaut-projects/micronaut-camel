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
package io.micronaut.camel.examples;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.lifecycle.Startable;

/**
 * Starts the containers of the examples that need a database or a broker.
 */
public final class Containers {

    private Containers() {
    }

    /**
     * Starts containers. The container runtime is pinged first: a runtime such as Podman closes idle connections,
     * and the ping, unlike the creation of a container, is retried on a new connection.
     *
     * @param containers The containers
     */
    public static void start(Startable... containers) {
        for (int i = 0; i < 5; i++) {
            DockerClientFactory.instance().client().pingCmd().exec();
        }
        for (Startable container : containers) {
            container.start();
        }
    }
}
