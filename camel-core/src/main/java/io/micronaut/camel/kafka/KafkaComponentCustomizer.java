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

import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.camel.requires.RequiresCamelKafka;
import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.kafka.clients.CommonClientConfigs;

/**
 * Sets the {@code brokers} of the Camel Kafka component to the {@code kafka.bootstrap.servers} of
 * Micronaut Kafka, unless {@code camel.component.kafka.brokers} is set.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelKafka
@Requires(classes = {KafkaComponent.class, KafkaDefaultConfiguration.class})
@Requires(beans = KafkaDefaultConfiguration.class)
@Requires(property = MicronautKafkaClientFactory.ENABLED, notEquals = StringUtils.FALSE)
final class KafkaComponentCustomizer implements ComponentCustomizer {

    private final KafkaDefaultConfiguration kafkaConfiguration;
    private final Environment environment;

    /**
     * @param kafkaConfiguration The default configuration of Micronaut Kafka
     * @param environment        The environment
     */
    KafkaComponentCustomizer(KafkaDefaultConfiguration kafkaConfiguration, Environment environment) {
        this.kafkaConfiguration = kafkaConfiguration;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target instanceof KafkaComponent;
    }

    @Override
    public void configure(String name, Component target) {
        KafkaConfiguration configuration = ((KafkaComponent) target).getConfiguration();
        if (configuration.getBrokers() == null && !CamelComponentOptions.isSet(environment, name, "brokers")) {
            String servers = kafkaConfiguration.getConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
            if (StringUtils.isNotEmpty(servers)) {
                configuration.setBrokers(servers);
            }
        }
    }
}
