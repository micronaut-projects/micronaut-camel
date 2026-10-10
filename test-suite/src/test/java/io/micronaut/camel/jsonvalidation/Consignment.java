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

import io.micronaut.camel.docs.jsonschema.Order;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * A type whose schema references the schema of another type, {@link Order}, by its classpath URI.
 *
 * @param reference The reference of the consignment
 * @param orders    The orders
 */
@JsonSchema(title = "Consignment")
@Serdeable
public record Consignment(@NotBlank String reference, List<Order> orders) {
}
