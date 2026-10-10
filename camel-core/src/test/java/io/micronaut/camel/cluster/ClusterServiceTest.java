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
package io.micronaut.camel.cluster;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Camel cluster services, from Micronaut beans or from {@code camel.cluster.*}, and the routes that
 * only run on the leader.
 */
class ClusterServiceTest {

    @TempDir
    Path root;

    @Test
    void aClusterServiceBeanIsAddedToTheContext() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "ClusterServiceTest",
            "cluster.root", root.toString()
        ))) {
            CamelContext camelContext = context.getBean(CamelContext.class);
            CamelClusterService bean = context.getBean(CamelClusterService.class);

            CamelClusterService service = ClusterServiceHelper.mandatoryLookupService(camelContext);
            assertSame(bean, service);
            assertEquals("node-1", service.getId());

            // the master component elects the leader with the cluster service
            MockEndpoint leader = camelContext.getEndpoint("mock:leader", MockEndpoint.class);
            leader.expectedMinimumMessageCount(1);
            leader.setResultWaitTime(TimeUnit.SECONDS.toMillis(30));
            leader.assertIsSatisfied();
        }
    }

    @Test
    void aClusterServiceIsConfiguredFromTheCamelClusterOptions() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "camel.cluster.file.id", "node-2",
            "camel.cluster.file.root", root.toString()
        ))) {
            CamelContext camelContext = context.getBean(CamelContext.class);

            FileLockClusterService service = assertInstanceOf(FileLockClusterService.class,
                ClusterServiceHelper.mandatoryLookupService(camelContext));
            assertEquals("node-2", service.getId());
            assertEquals(root.toString(), service.getRoot());
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "ClusterServiceTest")
    static class ClusterServiceFactory {

        @Singleton
        CamelClusterService clusterService(@Value("${cluster.root}") String root) {
            FileLockClusterService service = new FileLockClusterService();
            service.setId("node-1");
            service.setRoot(root);
            service.setAcquireLockDelay(10, TimeUnit.MILLISECONDS);
            service.setAcquireLockInterval(100, TimeUnit.MILLISECONDS);
            return service;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ClusterServiceTest")
    static class LeaderRoutes extends RouteBuilder {

        @Override
        public void configure() {
            from("master:reports:timer:reports?period=100").to("mock:leader");
        }
    }
}
