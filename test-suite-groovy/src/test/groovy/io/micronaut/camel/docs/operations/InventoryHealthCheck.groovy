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
package io.micronaut.camel.docs.operations

// tag::check[]
import jakarta.inject.Singleton
import org.apache.camel.health.HealthCheckResultBuilder
import org.apache.camel.impl.health.AbstractHealthCheck

@Singleton // <1>
class InventoryHealthCheck extends AbstractHealthCheck {

    InventoryHealthCheck() {
        super('shop', 'inventory') // <2>
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.detail('warehouse', 'main') // <3>
        builder.up()
    }
}
// end::check[]
