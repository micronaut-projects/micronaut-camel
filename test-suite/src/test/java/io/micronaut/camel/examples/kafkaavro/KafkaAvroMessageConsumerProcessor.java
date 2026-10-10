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
import org.apache.avro.generic.GenericRecord;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@Requires(env = "example-kafka-avro")
public class KafkaAvroMessageConsumerProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAvroMessageConsumerProcessor.class);

    private final List<GenericRecord> employees = new CopyOnWriteArrayList<>();

    @Override
    public void process(Exchange exchange) throws Exception {
        GenericRecord employee = exchange.getIn().getBody(GenericRecord.class);
        LOG.info("Consumed employee: {}", employee);
        employees.add(employee);
    }

    /**
     * @return The employees consumed so far
     */
    public List<GenericRecord> getEmployees() {
        return employees;
    }
}
