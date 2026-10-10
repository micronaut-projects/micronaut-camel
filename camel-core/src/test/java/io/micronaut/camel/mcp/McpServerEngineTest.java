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
package io.micronaut.camel.mcp;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "McpServerEngineTest")
@Property(name = "micronaut.mcp.server.transport", value = "HTTP")
@Property(name = "camel.server.mcp-enabled", value = "true")
@Property(name = "camel.server.mcp-tags", value = "weather")
class McpServerEngineTest {

    @Inject
    McpStatelessSyncServer server;

    @Inject
    CamelContext camelContext;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    JsonMapper jsonMapper;

    @Test
    void aiToolRoutesAreMcpTools() throws Exception {
        List<String> names = server.listTools().stream().map(McpSchema.Tool::name).toList();
        assertEquals(List.of("forecast"), names, "only the tools of the selected tags");

        Map<?, ?> list = rpc("tools/list", Map.of());
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        Map<?, ?> forecast = (Map<?, ?>) tools.get(0);
        assertEquals("forecast", forecast.get("name"));
        assertEquals("The weather forecast of a city", forecast.get("description"));
        Map<?, ?> schema = (Map<?, ?>) forecast.get("inputSchema");
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("city"));

        Map<?, ?> call = rpc("tools/call", Map.of("name", "forecast", "arguments", Map.of("city", "Paris")));
        Map<?, ?> result = (Map<?, ?>) call.get("result");
        assertEquals(false, result.get("isError"));
        assertEquals("Sunny in Paris", ((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text"));
    }

    @Test
    void stoppedRoutesAreRemoved() throws Exception {
        camelContext.getRouteController().stopRoute("forecast");
        try {
            assertFalse(server.listTools().stream().anyMatch(tool -> tool.name().equals("forecast")));
        } finally {
            camelContext.getRouteController().startRoute("forecast");
        }
        assertTrue(server.listTools().stream().anyMatch(tool -> tool.name().equals("forecast")));
    }

    private Map<?, ?> rpc(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
        String response = client.toBlocking().retrieve(HttpRequest.POST("/mcp", jsonMapper.writeValueAsString(request))
            .contentType("application/json")
            .accept("application/json", "text/event-stream"));
        return jsonMapper.readValue(response, Map.class);
    }

    @Singleton
    @Requires(property = "spec.name", value = "McpServerEngineTest")
    static class Tools extends RouteBuilder {

        @Override
        public void configure() {
            from("ai-tool:forecast?tags=weather&description=The weather forecast of a city"
                + "&parameter.city=string&parameter.city.description=The city&parameter.city.required=true")
                .routeId("forecast")
                .setBody(simple("Sunny in ${header.city}"));

            from("ai-tool:secret?tags=internal&description=Not exposed")
                .setBody(constant("hidden"));
        }
    }
}
