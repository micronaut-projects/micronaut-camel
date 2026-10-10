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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import org.apache.camel.component.mcp.server.McpServerBridge;

/**
 * Advertises the tools capability of the Micronaut MCP server, with list change notifications, when
 * {@code camel.server.mcp-enabled=true}: micronaut-mcp only advertises it when the application declares tools
 * of its own, and Camel adds its tools once the routes start.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = {McpServerBridge.class, McpSchema.class})
@Requires(property = CamelMcpToolsCapability.MCP_ENABLED, value = StringUtils.TRUE)
final class CamelMcpToolsCapability implements BeanCreatedEventListener<McpSchema.ServerCapabilities.Builder> {

    /**
     * The camel-main option that enables the MCP server.
     */
    static final String MCP_ENABLED = "camel.server.mcp-enabled";

    @Override
    public McpSchema.ServerCapabilities.Builder onCreated(BeanCreatedEvent<McpSchema.ServerCapabilities.Builder> event) {
        return event.getBean().tools(true);
    }
}
