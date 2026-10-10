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
package io.micronaut.camel.docs.integrations

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.mock.MockEndpoint
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

@MicronautTest
class IntegrationRoutesTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    ProducerTemplate producerTemplate

    void 'files are imported once'() {
        given:
        MockEndpoint imported = camelContext.getEndpoint('mock:imported', MockEndpoint)
        imported.expectedBodiesReceived('order-1')

        when:
        Path inbox = Files.createDirectories(Path.of('build/tmp/docs-inbox'))
        Files.writeString(inbox.resolve('a.txt'), 'order-1')

        then:
        imported.assertIsSatisfied()

        when:
        producerTemplate.sendBody('direct:import', 'order-1')

        then:
        imported.assertIsSatisfied()
    }

    void 'orders are written as JSON'() {
        expect:
        producerTemplate.requestBody('direct:order-json', [item: 'book'], String) == '{"item":"book"}'
    }
}
