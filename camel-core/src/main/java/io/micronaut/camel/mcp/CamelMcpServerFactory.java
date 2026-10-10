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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.McpServerFactory;

/**
 * Exposes Camel {@code ai-tool} routes as tools of the Micronaut MCP server, when micronaut-mcp's server and
 * camel-mcp-server-api are on the classpath. As with Camel Main, {@code camel.server.mcp-enabled=true} enables
 * it and {@code camel.server.mcp-tags} selects the tags of the tools to expose:
 *
 * <ul>
 *     <li>camel-main creates the Camel MCP server bridge with this {@link McpServerFactory}, without the
 *     Vert.x HTTP server of camel-mcp-server;</li>
 *     <li>the bridge publishes the tools through the {@link MicronautMcpServerEngine};</li>
 *     <li>the MCP server advertises the tools capability even when the application declares no tool of its own,
 *     since Camel adds its tools once the routes start.</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@Internal
@Requires(classes = {McpServerBridge.class, McpServerFactory.class, McpSchema.class})
final class CamelMcpServerFactory {

    /**
     * @param beanContext The bean context
     * @param jsonMapper  The JSON mapper of the MCP server
     * @return The engine, whose lifecycle the Camel MCP server bridge manages
     */
    @Singleton
    @Requires(missingBeans = McpServerEngine.class)
    @Requires(beans = McpJsonMapper.class)
    McpServerEngine micronautMcpServerEngine(BeanContext beanContext, McpJsonMapper jsonMapper) {
        return new MicronautMcpServerEngine(beanContext, jsonMapper);
    }

    /**
     * @return The factory of the Camel MCP server bridge, which camel-main uses with {@code camel.server.mcp-enabled}
     */
    @Singleton
    @Requires(missingBeans = McpServerFactory.class)
    McpServerFactory micronautMcpServerFactory() {
        return CamelMcpServerFactory::newMcpServer;
    }

    private static Service newMcpServer(CamelContext camelContext, HttpServerConfigurationProperties properties) {
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags(properties.getMcpTags());
        configuration.setToolTimeout(properties.getMcpToolTimeout());
        return new McpServerBridge(configuration);
    }
}
