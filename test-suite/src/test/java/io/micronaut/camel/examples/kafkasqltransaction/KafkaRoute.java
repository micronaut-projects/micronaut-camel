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
package io.micronaut.camel.examples.kafkasqltransaction;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * Routes that insert a word in a table and send it to a Kafka topic, with and without a Kafka transaction.
 */
@Singleton
@Requires(env = "example-kafka-sql-transaction")
public class KafkaRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        String insert = "insert into foo(name) values (:#word)";

        from("direct:send")
            .log("http word: ${header.word}")
            .to("sql:" + insert)
            .setBody(simple("{\"foo\": \"${header.word}\"}"))
            .to("kafka:foo");

        from("direct:send2")
            .log("http word: ${header.word}")
            .setBody(simple("{\"foo\": \"${header.word}\"}"))
            .to("kafka:foo")
            .to("sql:" + insert);

        from("direct:sendtx")
            .onException(Exception.class)
                .handled(true)
                .rollback("Expected error when trying to insert duplicate values in the unique column.")
            .end()
            .log("http word: ${header.word}")
            .to("sql:" + insert)
            .setBody(simple("{\"foo\": \"${header.word}\"}"))
            .to("kafka:foo?transacted=true");

        from("direct:sendtx2")
            .onException(Exception.class)
                .handled(true)
                .rollback("Expected error when trying to insert duplicate values in the unique column.")
            .end()
            .log("http word: ${header.word}")
            .setBody(simple("{\"foo\": \"${header.word}\"}"))
            .to("kafka:foo?transacted=true")
            .to("sql:" + insert);
    }
}
