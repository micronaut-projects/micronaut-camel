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
package io.micronaut.camel.examples.springjdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * Routes that insert horses in a local JDBC transaction and read them.
 */
@Singleton
@Requires(env = "example-spring-jdbc")
public class SpringJdbcRoute extends RouteBuilder {

    protected static final String INSERT_QUERY = "insert into horses(name,age) values(:#name,:#age)";
    protected static final String SELECT_QUERY = "select * from horses";

    @Override
    public void configure() throws Exception {

        from("direct:persist")
                .choice().when(simple("${header.fail} == 'true'"))
                        .to("direct:rollback")
                    .otherwise()
                        .to("direct:commit");

        from("direct:commit")
                .transacted()
                    .to("sql:" + INSERT_QUERY)
                .setBody(constant("executed"));

        from("direct:rollback")
                .transacted()
                    .to("sql:" + INSERT_QUERY)
                .rollback("forced to rollback");

        from("direct:read")
                .to("sql:" + SELECT_QUERY);

    }

}
