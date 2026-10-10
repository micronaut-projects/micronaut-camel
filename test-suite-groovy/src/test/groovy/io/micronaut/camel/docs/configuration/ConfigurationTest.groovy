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
package io.micronaut.camel.docs.configuration

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.seda.SedaComponent
import spock.lang.Specification

@MicronautTest
@Property(name = 'camel.main.name', value = 'shop')
@Property(name = 'camel.component.seda.queue-size', value = '500')
@Property(name = 'shop.welcome', value = 'Bonjour')
class ConfigurationTest extends Specification {

    @Inject
    CamelContext camelContext

    @Inject
    ProducerTemplate producerTemplate

    void 'Camel is configured from Micronaut configuration'() {
        expect:
        camelContext.name == 'shop'
        camelContext.getComponent('seda', SedaComponent).queueSize == 500
    }

    void 'placeholders resolve from Micronaut configuration'() {
        expect:
        producerTemplate.requestBody('direct:welcome', 'Fred', String) == 'Bonjour Fred'
    }
}
