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
package io.micronaut.camel.docs.annotations

// tag::checkout[]
import jakarta.inject.Singleton
import org.apache.camel.Produce

@Singleton
class Checkout {

    @Produce('direct:orders') // <1>
    OrderSender orders

    void checkout(String item) {
        orders.send(item) // <2>
    }
}
// end::checkout[]
