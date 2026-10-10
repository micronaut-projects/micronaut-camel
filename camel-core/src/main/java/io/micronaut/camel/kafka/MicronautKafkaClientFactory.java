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

import io.micronaut.camel.requires.RequiresCamelKafka;
import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.camel.component.kafka.KafkaClientFactory;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * The Camel {@link KafkaClientFactory} of an application that uses Micronaut Kafka. It adds the
 * common client properties of Micronaut Kafka ({@code kafka.*}, such as {@code security.protocol},
 * {@code sasl.*} and {@code ssl.*}) to the configuration of the Kafka producers and consumers Camel
 * creates.
 *
 * <p>Camel options that are configured, on the component or on an endpoint, take precedence: a
 * Micronaut property only replaces a value that Camel left unset or at its default. The properties of
 * Micronaut's own consumers and producers ({@code kafka.consumers.*} and {@code kafka.producers.*})
 * are not used.</p>
 *
 * <p>Camel autowires the factory into the Kafka component, so it is not used when the component's
 * {@code kafkaClientFactory} option is set or when the application defines another
 * {@link KafkaClientFactory} bean. Set {@code micronaut.camel.kafka.enabled} to {@code false} to
 * disable it.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelKafka
@Requires(classes = {KafkaClientFactory.class, KafkaDefaultConfiguration.class})
@Requires(beans = KafkaDefaultConfiguration.class)
@Requires(missingBeans = KafkaClientFactory.class)
@Requires(property = MicronautKafkaClientFactory.ENABLED, notEquals = StringUtils.FALSE)
public class MicronautKafkaClientFactory extends DefaultKafkaClientFactory {

    /**
     * The property that disables the integration with Micronaut Kafka when {@code false}.
     */
    public static final String ENABLED = "micronaut.camel.kafka.enabled";

    private final Properties clientProperties;
    private final Properties producerDefaults;
    private final Properties consumerDefaults;

    /**
     * @param configuration The default configuration of Micronaut Kafka
     */
    public MicronautKafkaClientFactory(KafkaDefaultConfiguration configuration) {
        this.clientProperties = clientProperties(configuration);
        KafkaConfiguration defaults = new KafkaConfiguration();
        this.producerDefaults = defaults.createProducerProperties();
        this.consumerDefaults = defaults.createConsumerProperties();
    }

    @Override
    public Producer getProducer(Properties kafkaProps) {
        return super.getProducer(merge(kafkaProps, producerDefaults));
    }

    @Override
    public Consumer getConsumer(Properties kafkaProps) {
        return super.getConsumer(merge(kafkaProps, consumerDefaults));
    }

    /**
     * @return The client properties of Micronaut Kafka that are added to Camel's clients
     */
    public Properties getClientProperties() {
        Properties copy = new Properties();
        copy.putAll(clientProperties);
        return copy;
    }

    /**
     * Adds the client properties of Micronaut Kafka to the properties of a Camel client.
     *
     * @param camelProperties The properties of the client, from the Camel options
     * @param camelDefaults   The properties Camel uses when no option is set
     * @return The merged properties
     */
    protected Properties merge(Properties camelProperties, Properties camelDefaults) {
        Properties merged = new Properties();
        merged.putAll(camelProperties);
        for (Map.Entry<Object, Object> entry : clientProperties.entrySet()) {
            Object key = entry.getKey();
            Object camelValue = camelProperties.get(key);
            boolean camelDefault = camelValue != null
                && !CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG.equals(key)
                && Objects.equals(String.valueOf(camelValue), String.valueOf(camelDefaults.get(key)));
            if (camelValue == null || camelDefault) {
                merged.put(key, entry.getValue());
            }
        }
        return merged;
    }

    private static Properties clientProperties(KafkaDefaultConfiguration configuration) {
        Properties properties = new Properties();
        configuration.getConfig().forEach((key, value) -> {
            if (isClientProperty(key.toString())) {
                properties.put(key, value);
            }
        });
        return properties;
    }

    private static boolean isClientProperty(String key) {
        // Kafka client properties are lower case words separated by dots, whereas the settings of
        // Micronaut Kafka itself, such as health-timeout, are hyphenated or start with a known prefix
        return key.indexOf('-') < 0
            && !key.equals("enabled")
            && !key.startsWith("health.")
            && !key.startsWith("topics");
    }
}
