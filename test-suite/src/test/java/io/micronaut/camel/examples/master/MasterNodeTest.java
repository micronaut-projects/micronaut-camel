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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code master} example of camel-spring-boot-examples (its {@code file} profile), which has no test.
 * <p>
 * camel-spring-boot creates the file based cluster service from {@code camel.cluster.file.*}. Here the
 * {@link FileLockClusterService} is a bean of {@link MasterNodeConfiguration}, configured from
 * {@code node.id} and {@code cluster.file.root}, and camel-main adds the {@code CamelClusterService} beans of the
 * registry to Camel. The {@code openshift} profile (the Kubernetes cluster service), the actuator settings and
 * {@code camel.main.run-controller} were dropped. The test uses a temporary directory for the lock files, instead of
 * {@code java.io.tmpdir}, so that no other process holds the leadership, and checks that the node becomes the leader
 * of the {@code camel-master} namespace and runs the {@code master:} route.
 */
@MicronautTest(environments = "example-master")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MasterNodeTest implements TestPropertyProvider {

    @Inject
    CamelContext camelContext;

    @Inject
    FileLockClusterService clusterService;

    @Override
    public Map<String, String> getProperties() {
        try {
            return Map.of("cluster.file.root", Files.createTempDirectory("camel-master").toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void clusteredRouteRunsOnTheLeader() throws Exception {
        // camel-main found the cluster service bean in the registry
        assertSame(clusterService, camelContext.hasService(CamelClusterService.class));

        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("clustered").whenDone(1)
            .and().fromRoute("heartbeat").whenDone(1)
            .create();

        assertTrue(notify.matches(20, TimeUnit.SECONDS));
        assertTrue(clusterService.getView("camel-master").getLocalMember().isLeader());
    }
}
