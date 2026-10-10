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
package io.micronaut.camel.kafka;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@MicronautTest
@Property(name = "kafka.bootstrap.servers", value = "micronaut:9092")
@Property(name = "kafka.security.protocol", value = "SASL_PLAINTEXT")
@Property(name = "kafka.sasl.mechanism", value = "PLAIN")
@Property(name = "kafka.health-timeout", value = "5s")
@Property(name = "camel.component.kafka.request-timeout-ms", value = "1234")
class KafkaClientFactoryTest {

    @Inject
    CamelContext camelContext;

    @Inject
    MicronautKafkaClientFactory clientFactory;

    @Test
    void theComponentUsesTheBrokersAndClientFactoryOfMicronautKafka() {
        KafkaComponent component = camelContext.getComponent("kafka", KafkaComponent.class);
        assertEquals("micronaut:9092", component.getConfiguration().getBrokers());
        assertInstanceOf(MicronautKafkaClientFactory.class, component.getKafkaClientFactory());
        assertEquals(1234, component.getConfiguration().getRequestTimeoutMs());
    }

    @Test
    void onlyKafkaClientPropertiesAreAdded() {
        Properties properties = clientFactory.getClientProperties();
        assertEquals("SASL_PLAINTEXT", properties.getProperty("security.protocol"));
        assertFalse(properties.containsKey("health-timeout"));
    }

    @Test
    void micronautPropertiesReplaceCamelDefaultsOnly() {
        KafkaConfiguration defaults = new KafkaConfiguration();
        Properties merged = clientFactory.merge(defaults.createProducerProperties(), defaults.createProducerProperties());
        assertEquals("SASL_PLAINTEXT", merged.getProperty("security.protocol"));
        assertEquals("PLAIN", merged.getProperty("sasl.mechanism"));

        KafkaConfiguration configured = new KafkaConfiguration();
        configured.setSecurityProtocol("SSL");
        configured.setBrokers("camel:9092");
        Properties camel = configured.createProducerProperties();
        camel.put("bootstrap.servers", "camel:9092");
        merged = clientFactory.merge(camel, defaults.createProducerProperties());
        assertEquals("SSL", merged.getProperty("security.protocol"));
        assertEquals("camel:9092", merged.getProperty("bootstrap.servers"));
    }
}
