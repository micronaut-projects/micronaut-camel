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
package io.micronaut.camel.docs.http

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class HttpTest extends Specification {

    @Inject
    @Client('/')
    HttpClient httpClient

    void 'platform-http routes are served'() {
        expect:
        httpClient.toBlocking().retrieve('/hello/Fred') == 'Hello Fred'
    }

    void 'REST services bind JSON'() {
        when:
        def created = httpClient.toBlocking().exchange(HttpRequest.POST('/books',
                '{"isbn":"1617292931","title":"Camel in Action"}')
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header('X-API-Key', 's3cr3t'), String)

        then:
        created.status() == HttpStatus.CREATED

        when:
        Book book = httpClient.toBlocking().retrieve(HttpRequest.GET('/books/1617292931'), Book)

        then:
        book == new Book(isbn: '1617292931', title: 'Camel in Action')
    }

    void 'server filters apply to Camel routes'() {
        when:
        httpClient.toBlocking().retrieve(HttpRequest.POST('/books', '{"isbn":"1","title":"x"}')
            .contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED
    }
}
