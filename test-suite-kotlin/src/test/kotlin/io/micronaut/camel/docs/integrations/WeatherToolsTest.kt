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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(environments = ["docs-mcp"])
class WeatherToolsTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var jsonMapper: JsonMapper

    @Test
    fun mcpClientsCallTheTool() {
        val tools = rpc("tools/list", mapOf())
        assertEquals("forecast", ((tools["tools"] as List<*>)[0] as Map<*, *>)["name"])

        val result = rpc("tools/call", mapOf("name" to "forecast", "arguments" to mapOf("city" to "Lisbon")))
        assertEquals("Sunny in Lisbon", ((result["content"] as List<*>)[0] as Map<*, *>)["text"])
    }

    private fun rpc(method: String, params: Map<String, Any>): Map<*, *> {
        val request = jsonMapper.writeValueAsString(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to method, "params" to params))
        val response = client.toBlocking().retrieve(HttpRequest.POST("/mcp", request)
            .contentType("application/json")
            .accept("application/json", "text/event-stream"))
        return jsonMapper.readValue(response, Map::class.java)!!["result"] as Map<*, *>
    }
}
