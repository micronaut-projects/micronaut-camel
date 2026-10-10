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
package io.micronaut.camel.mqtt;

import io.micronaut.camel.properties.CamelComponentOptions;
import io.micronaut.camel.requires.RequiresCamelPahoMqtt5;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.camel.Component;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Component;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Configuration;
import org.apache.camel.spi.ComponentCustomizer;

/**
 * Configures the Camel Paho MQTT 5 (camel-paho-mqtt5) component from the client settings of Micronaut MQTT:
 * {@code mqtt.client.server-uri} becomes {@code brokerUrl}, and {@code mqtt.client.user-name} and
 * {@code mqtt.client.password} become {@code userName} and {@code password}. An option set with
 * {@code camel.component.paho-mqtt5.*} is left unchanged. The client id is not shared, as an MQTT
 * server accepts one connection per client id.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelPahoMqtt5
@Requires(classes = PahoMqtt5Component.class)
@Requires(property = MqttClientSettings.SERVER_URI)
@Requires(property = MqttClientSettings.ENABLED, notEquals = StringUtils.FALSE)
final class PahoMqtt5ComponentCustomizer implements ComponentCustomizer {

    private final Environment environment;

    /**
     * @param environment The environment
     */
    PahoMqtt5ComponentCustomizer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String name, Component target) {
        return target instanceof PahoMqtt5Component;
    }

    @Override
    public void configure(String name, Component target) {
        PahoMqtt5Configuration configuration = ((PahoMqtt5Component) target).getConfiguration();
        if (!CamelComponentOptions.isSet(environment, name, "brokerUrl")) {
            MqttClientSettings.apply(environment, MqttClientSettings.SERVER_URI, configuration::setBrokerUrl);
        }
        if (!CamelComponentOptions.isSet(environment, name, "userName")) {
            MqttClientSettings.apply(environment, "mqtt.client.user-name", configuration::setUserName);
        }
        if (!CamelComponentOptions.isSet(environment, name, "password")) {
            MqttClientSettings.apply(environment, "mqtt.client.password", configuration::setPassword);
        }
    }
}
