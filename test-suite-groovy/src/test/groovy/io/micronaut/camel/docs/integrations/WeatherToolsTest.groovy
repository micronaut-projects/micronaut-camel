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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(environments = 'docs-mcp')
class WeatherToolsTest extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Inject
    JsonMapper jsonMapper

    void 'MCP clients call the tool'() {
        when:
        Map tools = rpc('tools/list', [:])

        then:
        tools.tools[0].name == 'forecast'

        when:
        Map result = rpc('tools/call', [name: 'forecast', arguments: [city: 'Lisbon']])

        then:
        result.content[0].text == 'Sunny in Lisbon'
    }

    private Map rpc(String method, Map<String, Object> params) {
        String request = jsonMapper.writeValueAsString([jsonrpc: '2.0', id: 1, method: method, params: params])
        String response = client.toBlocking().retrieve(HttpRequest.POST('/mcp', request)
            .contentType('application/json')
            .accept('application/json', 'text/event-stream'))
        jsonMapper.readValue(response, Map).result as Map
    }
}
