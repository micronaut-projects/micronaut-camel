from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.security.authentication import AuthenticationFailureReason, AuthenticationRequest, AuthenticationResponse
from micronaut.security.authentication.provider import HttpRequestAuthenticationProvider
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(environments=["docs-security"])
class AdminRoutesTest:

    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def anonymous_requests_are_unauthorized(self) -> None:
        try:
            self.client.toBlocking().retrieve(HttpRequest.GET("/admin/stats"))
            raise AssertionError("the request was not rejected")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.UNAUTHORIZED

    @Test
    def admins_are_authorized(self) -> None:
        stats = self.client.toBlocking().retrieve(HttpRequest.GET("/admin/stats").basicAuth("alice", "password"))

        assert stats == '{"orders":42}'

    @Test
    def users_without_the_role_are_forbidden(self) -> None:
        try:
            self.client.toBlocking().retrieve(HttpRequest.GET("/admin/stats").basicAuth("bob", "password"))
            raise AssertionError("the request was not rejected")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.FORBIDDEN


@Singleton
@Requires(env="docs-security")
class Users(HttpRequestAuthenticationProvider):

    def authenticate(self, request: HttpRequest | None, auth_request: AuthenticationRequest) -> AuthenticationResponse:
        if auth_request.getSecret() != "password":
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
        identity = auth_request.getIdentity()
        if identity == "alice":
            return AuthenticationResponse.success("alice", ["ROLE_ADMIN"])
        if identity == "bob":
            return AuthenticationResponse.success("bob", ["ROLE_USER"])
        return AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND)
