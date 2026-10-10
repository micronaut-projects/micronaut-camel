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
package io.micronaut.camel.docs.routes

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.ProducerTemplate
import spock.lang.Specification

@MicronautTest
class RoutesTest extends Specification {

    @Inject
    ProducerTemplate producerTemplate

    void 'route builder beans can inject beans'() {
        expect:
        producerTemplate.requestBody('direct:price', 'book', Integer) == 40
    }

    void 'lambda route builders are collected'() {
        expect:
        producerTemplate.requestBody('direct:ship', 'book', String) == 'shipped book'
        producerTemplate.requestBody('direct:track', 'book', String) == 'tracking book'
    }

    void 'YAML routes are loaded'() {
        expect:
        producerTemplate.requestBody('direct:notify', 'Fred', String) == 'notified Fred'
    }
}
