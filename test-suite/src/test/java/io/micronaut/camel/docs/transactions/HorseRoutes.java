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
package io.micronaut.camel.docs.transactions;

// tag::routes[]
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

@Singleton
public class HorseRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:add-horse")
            .transacted() // <1>
            .to("sql:insert into horses(name, age) values (:#name, :#age)") // <2>
            .to("direct:audit")
            .filter(header("fail"))
                .rollback("Horse rejected"); // <3>

        from("direct:add-horses")
            .transacted()
            .split(body()) // <4>
                .to("sql:insert into horses(name) values (:#${body})");

        from("direct:audit")
            .transacted("PROPAGATION_REQUIRES_NEW") // <5>
            .setBody(simple("Added ${header.name}"))
            .to("sql:insert into audit(message) values (:#${body})");

        from("direct:count-horses")
            .transacted("readOnly") // <6>
            .to("sql:select count(*) from horses?outputType=SelectOne");
    }
}
// end::routes[]
