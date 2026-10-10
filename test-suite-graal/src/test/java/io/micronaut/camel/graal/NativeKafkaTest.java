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
package io.micronaut.camel.graal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.testresources.client.TestResourcesClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Map;

/**
 * The kafka component, with a broker from Micronaut Test Resources, which provides
 * {@code kafka.bootstrap.servers}. Skipped when the test resources server cannot start Kafka, for
 * example without a container runtime.
 */
@MicronautTest(environments = "kafka")
@EnabledIf("kafkaAvailable")
class NativeKafkaTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void routesProduceAndConsumeRecords() throws Exception {
        MockEndpoint received = camelContext.getEndpoint("mock:kafka", MockEndpoint.class);
        received.expectedBodiesReceived("hello");
        received.setResultWaitTime(60_000);

        producerTemplate.sendBody("direct:to-kafka", "hello");

        received.assertIsSatisfied();
    }

    @ReflectiveAccess
    static boolean kafkaAvailable() {
        try {
            return TestResourcesClientFactory.fromSystemProperties()
                .flatMap(client -> client.resolve("kafka.bootstrap.servers", Map.of(), Map.of()))
                .isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Singleton
    @Requires(env = "kafka")
    public static class KafkaRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:to-kafka").to("kafka:native-orders");

            from("kafka:native-orders?groupId=native&autoOffsetReset=earliest").to("mock:kafka");
        }
    }
}
