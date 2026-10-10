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
package io.micronaut.camel.examples.master;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.cluster.FileLockClusterService;

/**
 * The cluster service and the routes of the example.
 */
@Factory
@Requires(env = "example-master")
class MasterNodeConfiguration {

    /**
     * The file based cluster service that camel-spring-boot creates from {@code camel.cluster.file.*}: camel-main
     * finds the {@code CamelClusterService} beans of the registry and adds them to Camel.
     *
     * @param id   The id of this node
     * @param root The directory of the lock files, shared by the nodes
     * @return The cluster service
     */
    @Singleton
    FileLockClusterService clusterService(@Value("${node.id}") String id, @Value("${cluster.file.root}") String root) {
        FileLockClusterService service = new FileLockClusterService();
        service.setId(id);
        service.setRoot(root);
        return service;
    }

    @Singleton
    RouteBuilder routeBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // This route is local so it will be started regardless of the
                // leadership status of this node.
                from("timer:heartbeat?period=10000")
                    .routeId("heartbeat")
                    .log("HeartBeat route (timer) {{node.id}} ...");

                // This route is configured to be clustered so it will be started
                // by the controller only when this node is leader
                from("master:{{node.namespace}}:timer:clustered?period=5000")
                    .routeId("clustered")
                    .log("Clustered route (timer) {{node.id}} ...");
            }
        };
    }
}
