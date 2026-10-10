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
package io.micronaut.camel.docs.testing

// tag::test[]
import io.micronaut.camel.test.MockEndpoints
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.mock.MockEndpoint
import spock.lang.Specification

@MicronautTest
@MockEndpoints('log:*') // <1>
class MockGreetingsTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    ProducerTemplate producerTemplate

    void 'greetings are logged'() {
        given:
        MockEndpoint greetings = camelContext.getEndpoint('mock:log:greetings', MockEndpoint) // <2>
        greetings.expectedBodiesReceived('Hello Fred')

        when:
        producerTemplate.sendBody('direct:greet', 'Fred')

        then:
        greetings.assertIsSatisfied()
    }
}
// end::test[]
