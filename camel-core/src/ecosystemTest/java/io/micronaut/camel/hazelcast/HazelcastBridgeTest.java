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
package io.micronaut.camel.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.HazelcastInstance;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.map.HazelcastMapComponent;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
@Property(name = "spec.name", value = "HazelcastBridgeTest")
class HazelcastBridgeTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    HazelcastInstance hazelcastInstance;

    @Test
    void theComponentsUseTheMicronautHazelcastInstance() {
        assertSame(hazelcastInstance, camelContext.getComponent("hazelcast-map", HazelcastMapComponent.class).getHazelcastInstance());
        assertSame(hazelcastInstance, camelContext.getComponent("hazelcast-queue", HazelcastQueueComponent.class).getHazelcastInstance());

        producerTemplate.sendBodyAndHeader("direct:put", "pending", HazelcastConstants.OBJECT_ID, "order-1");

        assertEquals("pending", hazelcastInstance.getMap("orders").get("order-1"));
    }

    @Factory
    @Requires(property = "spec.name", value = "HazelcastBridgeTest")
    static class HazelcastConfigFactory {

        /**
         * A member that does not look for others.
         */
        @Singleton
        Config hazelcastConfig() {
            Config config = new Config();
            config.setClusterName("camel-bridge-test");
            config.setProperty("hazelcast.phone.home.enabled", "false");
            JoinConfig join = config.getNetworkConfig().getJoin();
            join.getMulticastConfig().setEnabled(false);
            join.getAutoDetectionConfig().setEnabled(false);
            join.getTcpIpConfig().setEnabled(false);
            return config;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "HazelcastBridgeTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:put").to("hazelcast-map:orders?operation=put");
        }
    }
}
