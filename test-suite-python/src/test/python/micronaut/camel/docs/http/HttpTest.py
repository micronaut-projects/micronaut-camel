from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.camel.docs.http.Book import Book
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class HttpTest:

    http_client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def platform_http_routes_are_served(self) -> None:
        assert self.http_client.toBlocking().retrieve("/hello/Fred") == "Hello Fred"

    @Test
    def rest_services_bind_json(self) -> None:
        created = self.http_client.toBlocking().exchange(
            HttpRequest.POST("/books", '{"isbn":"1617292931","title":"Camel in Action"}')
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("X-API-Key", "s3cr3t"),
            String)
        assert created.status() == HttpStatus.CREATED

        book = self.http_client.toBlocking().retrieve(HttpRequest.GET("/books/1617292931"), Book)
        assert book == Book("1617292931", "Camel in Action")

    @Test
    def server_filters_apply_to_camel_routes(self) -> None:
        try:
            self.http_client.toBlocking().retrieve(
                HttpRequest.POST("/books", '{"isbn":"1","title":"x"}').contentType(MediaType.APPLICATION_JSON_TYPE))
            raise AssertionError("the request was not rejected")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.UNAUTHORIZED
