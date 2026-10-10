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
package io.micronaut.camel.jsonvalidation;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * The routes of {@link JsonValidatorWithMcpTest}.
 */
@Singleton
@Requires(env = "json-validator-mcp")
class ConsignmentRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:json-validator-consignments")
            .to("json-validator:jsonschema:Consignment")
            .to("mock:valid-consignments");
        from("direct:typed-consignments")
            .inputTypeWithValidate(Consignment.class)
            .to("mock:valid-consignments");
    }
}
