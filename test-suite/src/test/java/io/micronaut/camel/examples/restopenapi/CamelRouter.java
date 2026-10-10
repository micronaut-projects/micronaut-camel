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
package io.micronaut.camel.examples.restopenapi;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

import static org.apache.camel.model.rest.RestParamType.body;
import static org.apache.camel.model.rest.RestParamType.path;

@Singleton
@Requires(env = "example-rest-openapi")
public class CamelRouter extends RouteBuilder {

    private final String contextPath;

    public CamelRouter(@Value("${api.context-path}") String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public void configure() throws Exception {

        // @formatter:off

        // this can also be configured in application.yml
        restConfiguration()
            .component("platform-http")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .enableCORS(true)
            .contextPath(contextPath)
            // turn on openapi api-doc
            .apiContextPath("/api-doc")
            .apiProperty("api.title", "User API")
            .apiProperty("api.version", "1.0.0");

        rest("/users").description("User REST service")
            .consumes("application/json")
            .produces("application/json")

            .get().description("Find all users").outType(User[].class)
                .responseMessage().code(200).message("All users successfully returned").endResponseMessage()
                .to("bean:userService?method=findUsers")

            .get("/{id}").description("Find user by ID")
                .outType(User.class)
                .param().name("id").type(path).description("The ID of the user").dataType("integer").endParam()
                .responseMessage().code(200).message("User successfully returned").endResponseMessage()
                .to("bean:userService?method=findUser(${header.id})")

            .put("/{id}").description("Update a user").type(User.class)
                .param().name("id").type(path).description("The ID of the user to update").dataType("integer").endParam()
                .param().name("body").type(body).description("The user to update").endParam()
                .responseMessage().code(204).message("User successfully updated").endResponseMessage()
                .to("direct:update-user");

        from("direct:update-user")
            .to("bean:userService?method=updateUser")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
            .setBody(constant(""));

        // @formatter:on
    }

}
