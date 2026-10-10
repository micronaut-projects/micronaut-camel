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
class ParcelRoutesTest extends Specification {

    @Inject
    ProducerTemplate producerTemplate

    void 'routes parcels'() {
        expect:
        ship(weight: 30, country: 'CH') == 'freight'
        ship(weight: 2, country: 'NO') == 'customs for NO'
        ship(weight: 2, country: 'FR') == 'post'
    }

    private String ship(Map<String, Object> headers) {
        producerTemplate.requestBodyAndHeaders('direct:parcel', 'parcel', headers, String)
    }
}
