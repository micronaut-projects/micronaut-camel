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

// tag::test[]
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.EndpointInject
import org.apache.camel.ProducerTemplate
import spock.lang.Specification

@MicronautTest
class AnnotationsTest extends Specification {

    @EndpointInject('direct:orders') // <1>
    ProducerTemplate orders

    @Inject
    Checkout checkout

    @Inject
    OrderConsumer consumer

    void 'orders reach the consumer'() {
        when:
        checkout.checkout('book=40')
        orders.sendBody('pen=2')

        then:
        consumer.received == [new Price('book', 40), new Price('pen', 2)]
    }
}
// end::test[]
