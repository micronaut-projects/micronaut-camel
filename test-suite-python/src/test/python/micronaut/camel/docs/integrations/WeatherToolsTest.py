import json
from typing import Annotated

from jakarta.inject import Inject
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(environments=["docs-mcp"])
class WeatherToolsTest:

    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def mcp_clients_call_the_tool(self) -> None:
        tools = self.rpc("tools/list", {})
        assert tools["tools"][0]["name"] == "forecast"

        result = self.rpc("tools/call", {"name": "forecast", "arguments": {"city": "Lisbon"}})
        assert result["content"][0]["text"] == "Sunny in Lisbon"

    def rpc(self, method: str, params: dict) -> dict:
        request = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params})
        response = self.client.toBlocking().retrieve(HttpRequest.POST("/mcp", request)
            .contentType("application/json")
            .accept("application/json", "text/event-stream"))
        return json.loads(response)["result"]
