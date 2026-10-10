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

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;

import java.util.function.Consumer;

/**
 * The connection settings of Micronaut MQTT ({@code mqtt.client.*}) that the Camel Paho MQTT 5 component
 * uses when its own options are not set.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MqttClientSettings {

    /**
     * The URI of the MQTT server, as configured for Micronaut MQTT.
     */
    static final String SERVER_URI = "mqtt.client.server-uri";

    /**
     * The property that disables the integration with Micronaut MQTT when {@code false}.
     */
    static final String ENABLED = "micronaut.camel.mqtt.enabled";

    private MqttClientSettings() {
    }

    /**
     * Applies a setting of Micronaut MQTT to a component.
     *
     * @param environment The environment
     * @param property    The name of the setting in Micronaut MQTT
     * @param setter      Sets the option of the component
     */
    static void apply(Environment environment, String property, Consumer<String> setter) {
        environment.getProperty(property, String.class).ifPresent(setter);
    }
}
