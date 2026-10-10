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
package io.micronaut.camel.examples.openapicontractfirst;

import io.micronaut.camel.examples.openapicontractfirst.model.Pet;
import io.micronaut.camel.examples.openapicontractfirst.model.Pet.StatusEnum;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

/**
 * Camel routes for the PetStore example
 */
@Singleton
@Requires(env = "example-openapi-contract-first")
public class PetStoreRoute extends RouteBuilder {

    private final String petName;

    public PetStoreRoute(@Value("${petName}") String petName) {
        this.petName = petName;
    }

    @Override
    public void configure() throws Exception {
        // turn on json binding and scan for POJO classes in the model package
        restConfiguration().bindingMode(RestBindingMode.json)
                .bindingPackageScan("io.micronaut.camel.examples.openapicontractfirst.model")
                // turn on request validation
                .clientRequestValidation(true);

        rest().openApi().specification("examples/openapi-contract-first/petstore.json").missingOperation("ignore");

        from("direct:getPetById")
                .process(e -> {
                    // build response body as POJO
                    Pet pet = new Pet();
                    pet.setId(e.getMessage().getHeader("petId", long.class));
                    pet.setName(petName);
                    pet.setStatus(StatusEnum.AVAILABLE);
                    e.getMessage().setBody(pet);
                });

        from("direct:updatePet")
                .process(e -> {
                    Pet pet = e.getMessage().getBody(Pet.class);
                    pet.setStatus(StatusEnum.PENDING);
                });
    }
}
