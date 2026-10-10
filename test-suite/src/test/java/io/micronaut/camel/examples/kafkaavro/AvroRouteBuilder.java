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
package io.micronaut.camel.examples.kafkaavro;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.avro.AvroDataFormat;

@Singleton
@Requires(env = "example-kafka-avro")
public class AvroRouteBuilder extends RouteBuilder {

    private final KafkaAvroMessageConsumerProcessor consumerProcessor;

    public AvroRouteBuilder(KafkaAvroMessageConsumerProcessor consumerProcessor) {
        this.consumerProcessor = consumerProcessor;
    }

    @Override
    public void configure() throws Exception {
        AvroDataFormat employeeAvroFormat = new AvroDataFormat(Employees.SCHEMA);

        // the brokers are the kafka.bootstrap.servers of Micronaut Kafka
        from("timer://foo?period={{period}}")
            .process(new KafkaAvroMessageProcessor())
            .marshal(employeeAvroFormat)
            .to("kafka:{{producer.topic}}"
                + "?keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                + "&valueSerializer=org.apache.kafka.common.serialization.ByteArraySerializer"
                + "&recordMetadata=true")
            .process(new KafkaAvroProcessor());

        from("kafka:{{consumer.topic}}"
                + "?groupId={{consumer.group}}"
                + "&autoOffsetReset=earliest"
                + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                + "&valueDeserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer")
            .unmarshal(employeeAvroFormat)
            .process(consumerProcessor);
    }
}
