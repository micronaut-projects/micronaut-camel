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
package io.micronaut.camel.examples.pahomqtt5;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.INFO;

@Singleton
@Requires(env = "example-paho-mqtt5-shared-subscriptions")
public class PahoMqtt5RouteBuilder extends RouteBuilder {

    AtomicInteger counter = new AtomicInteger(0);

    @Override
    public void configure() throws Exception {
        // the broker is the mqtt.client.server-uri of Micronaut MQTT

        // The following consumers share the same conversation group (group1) and will be loadbalanced
        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerA")
                .id("consumerA")
                .log(INFO, "CONSUMER A - MESSAGE: ${body}");

        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerB")
                .id("consumerB")
                .log(INFO, "CONSUMER B - MESSAGE: ${body}");

        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerC")
                .id("consumerC")
                .log(INFO, "CONSUMER C - MESSAGE: ${body}");

        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerD")
                .id("consumerD")
                .log(INFO, "CONSUMER D - MESSAGE: ${body}");

        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerE")
                .id("consumerE")
                .log(INFO, "CONSUMER E - MESSAGE: ${body}");

        from("paho-mqtt5:{{consumer.topic}}?clientId=consumerF")
                .id("consumerF")
                .log(INFO, "CONSUMER F - MESSAGE: ${body}");

        // the producer
        from("timer://foo?fixedRate=true&period={{producer.period}}")
                .id("producer")
                .process(exchange -> {
                    exchange.getIn().setBody(counter.getAndIncrement() + " - hello world");
                })
                .log(DEBUG, "PRODUCER   - MESSAGE: ${body}")
                .to("paho-mqtt5:{{producer.topic}}?clientId=producer");
    }
}
