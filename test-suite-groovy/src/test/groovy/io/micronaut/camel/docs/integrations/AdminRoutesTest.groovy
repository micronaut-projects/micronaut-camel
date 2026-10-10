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

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
import spock.lang.Specification

@MicronautTest(environments = 'docs-security')
class AdminRoutesTest extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    void 'anonymous requests are unauthorized'() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET('/admin/stats'))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED
    }

    void 'admins are authorized'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/admin/stats').basicAuth('alice', 'password')) == '{"orders":42}'
    }

    void 'users without the role are forbidden'() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET('/admin/stats').basicAuth('bob', 'password'))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.FORBIDDEN
    }

    @Singleton
    @Requires(env = 'docs-security')
    static class Users<B> implements HttpRequestAuthenticationProvider<B> {

        @Override
        AuthenticationResponse authenticate(@Nullable HttpRequest<B> request, AuthenticationRequest<String, String> authRequest) {
            if (authRequest.secret != 'password') {
                return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
            }
            switch (authRequest.identity) {
                case 'alice':
                    return AuthenticationResponse.success('alice', ['ROLE_ADMIN'])
                case 'bob':
                    return AuthenticationResponse.success('bob', ['ROLE_USER'])
                default:
                    return AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND)
            }
        }
    }
}
