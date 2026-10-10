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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MicronautTest(environments = ["docs-security"])
class AdminRoutesTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun anonymousRequestsAreUnauthorized() {
        val e = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().retrieve(HttpRequest.GET<Any>("/admin/stats"))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, e.status)
    }

    @Test
    fun adminsAreAuthorized() {
        val stats = client.toBlocking().retrieve(HttpRequest.GET<Any>("/admin/stats").basicAuth("alice", "password"))

        assertEquals("""{"orders":42}""", stats)
    }

    @Test
    fun usersWithoutTheRoleAreForbidden() {
        val e = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().retrieve(HttpRequest.GET<Any>("/admin/stats").basicAuth("bob", "password"))
        }

        assertEquals(HttpStatus.FORBIDDEN, e.status)
    }

    @Singleton
    @Requires(env = ["docs-security"])
    class Users<B : Any> : HttpRequestAuthenticationProvider<B> {

        override fun authenticate(request: HttpRequest<B>?, authRequest: AuthenticationRequest<String, String>): AuthenticationResponse {
            if (authRequest.secret != "password") {
                return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
            }
            return when (authRequest.identity) {
                "alice" -> AuthenticationResponse.success("alice", listOf("ROLE_ADMIN"))
                "bob" -> AuthenticationResponse.success("bob", listOf("ROLE_USER"))
                else -> AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND)
            }
        }
    }
}
