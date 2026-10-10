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
package io.micronaut.camel.openapi;

import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

/**
 * REST services whose OpenAPI document Camel serves at {@code /api/api-doc}.
 */
@Singleton
class PetRoutes extends RouteBuilder {

    @Override
    public void configure() {
        restConfiguration()
            .bindingMode(RestBindingMode.off)
            .contextPath("/api")
            .apiContextPath("/api-doc")
            .apiProperty("api.title", "Pet API")
            .apiProperty("api.version", "1.0.0");

        rest("/pets").description("Pet REST service")
            .get("/{id}").description("Find a pet by ID").produces("application/json")
                .to("direct:pet");

        from("direct:pet")
            .setBody(simple("{\"id\": ${header.id}, \"name\": \"Rex\"}"));
    }
}
