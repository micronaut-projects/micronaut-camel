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
package io.micronaut.camel.docs.integrations;

import io.micronaut.context.annotation.Requires;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(environments = "docs-security")
class AdminRoutesTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void anonymousRequestsAreUnauthorized() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/admin/stats")));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    @Test
    void adminsAreAuthorized() {
        String stats = client.toBlocking().retrieve(HttpRequest.GET("/admin/stats").basicAuth("alice", "password"));

        assertEquals("{\"orders\":42}", stats);
    }

    @Test
    void usersWithoutTheRoleAreForbidden() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/admin/stats").basicAuth("bob", "password")));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Singleton
    @Requires(env = "docs-security")
    static class Users<B> implements HttpRequestAuthenticationProvider<B> {

        @Override
        public AuthenticationResponse authenticate(@Nullable HttpRequest<B> request, AuthenticationRequest<String, String> authRequest) {
            if (!"password".equals(authRequest.getSecret())) {
                return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
            }
            return switch (authRequest.getIdentity()) {
                case "alice" -> AuthenticationResponse.success("alice", List.of("ROLE_ADMIN"));
                case "bob" -> AuthenticationResponse.success("bob", List.of("ROLE_USER"));
                default -> AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND);
            };
        }
    }
}
