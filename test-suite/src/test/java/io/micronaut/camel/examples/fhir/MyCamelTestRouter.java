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
package io.micronaut.camel.examples.fhir;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The route of the test of the example, which searches the patients.
 */
@Singleton
@Requires(env = "example-fhir")
public class MyCamelTestRouter extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:start")
                .to("fhir://search/searchByUrl?url=Patient&serverUrl={{serverUrl}}&fhirVersion={{fhirVersion}}")
                .process(exchange -> {
                    Bundle bundle = exchange.getIn().getBody(Bundle.class);
                    exchange.getIn().setBody(bundle.getEntry().get(0).getResource());
                })
                .to("mock:result");
    }
}
