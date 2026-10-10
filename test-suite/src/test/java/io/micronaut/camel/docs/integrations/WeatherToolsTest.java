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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(environments = "docs-mcp")
class WeatherToolsTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    JsonMapper jsonMapper;

    @Test
    void mcpClientsCallTheTool() throws IOException {
        Map<?, ?> tools = rpc("tools/list", Map.of());
        assertEquals("forecast", ((Map<?, ?>) ((List<?>) tools.get("tools")).get(0)).get("name"));

        Map<?, ?> result = rpc("tools/call", Map.of("name", "forecast", "arguments", Map.of("city", "Lisbon")));
        assertEquals("Sunny in Lisbon", ((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text"));
    }

    private Map<?, ?> rpc(String method, Map<String, Object> params) throws IOException {
        String request = jsonMapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
        String response = client.toBlocking().retrieve(HttpRequest.POST("/mcp", request)
            .contentType("application/json")
            .accept("application/json", "text/event-stream"));
        return (Map<?, ?>) jsonMapper.readValue(response, Map.class).get("result");
    }
}
