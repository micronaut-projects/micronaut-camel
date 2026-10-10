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
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.function.Function;

/**
 * The Camel {@link McpServerEngine} that publishes the {@code ai-tool} routes selected by
 * {@code camel.server.mcp-tags} as tools of the Micronaut MCP server, the role of camel-spring-boot's
 * {@code SpringAiMcpServerEngine}. It adds the tools to the MCP server bean of micronaut-mcp: the stateless
 * server of the HTTP transport, or the server of the STDIO transport, synchronous or reactive.
 *
 * <p>The Micronaut MCP server owns the serving configuration ({@code micronaut.mcp.server.*}): the
 * {@code camel.server.mcp-path} and {@code camel.server.mcp-server-name} options do not apply.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautMcpServerEngine extends ServiceSupport implements McpServerEngine {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautMcpServerEngine.class);

    private static final String EMPTY_OBJECT_SCHEMA = """
        {"type": "object", "properties": {}, "additionalProperties": false}
        """;

    private final BeanContext beanContext;
    private final McpJsonMapper jsonMapper;
    private @Nullable CamelContext camelContext;
    private volatile @Nullable ToolServer server;

    /**
     * @param beanContext The bean context, to find the MCP server
     * @param jsonMapper  The JSON mapper of the MCP server, to read the input schemas of the tools
     */
    public MicronautMcpServerEngine(BeanContext beanContext, McpJsonMapper jsonMapper) {
        this.beanContext = beanContext;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void initialize(McpServerInfo info) {
        LOG.debug("Micronaut MCP server engine initialized; micronaut.mcp.server.* configures the server, not {}", info);
    }

    @Override
    protected void doStart() throws Exception {
        server();
    }

    @Override
    public void toolAdded(McpServerTool tool) {
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(jsonMapper, tool.inputSchemaJson() != null ? tool.inputSchemaJson() : EMPTY_OBJECT_SCHEMA);
        applyAnnotations(builder, tool.annotations());
        server().add(builder.build(), arguments -> call(tool, arguments));
        LOG.debug("MCP tool added: {}", tool.name());
    }

    @Override
    public void toolRemoved(String toolName) {
        try {
            server().remove(toolName);
            LOG.debug("MCP tool removed: {}", toolName);
        } catch (Exception e) {
            LOG.debug("Failed to remove MCP tool {}: {}", toolName, e.getMessage());
        }
    }

    private static McpSchema.CallToolResult call(McpServerTool tool, @Nullable Map<String, Object> arguments) {
        McpToolCallResult result = tool.handler().call(arguments != null ? arguments : Map.of());
        return McpSchema.CallToolResult.builder()
            .addTextContent(result.text())
            .isError(result.isError())
            .build();
    }

    private ToolServer server() {
        ToolServer result = server;
        if (result == null) {
            result = resolveServer();
            server = result;
        }
        return result;
    }

    private ToolServer resolveServer() {
        var statelessSync = beanContext.findBean(McpStatelessSyncServer.class);
        if (statelessSync.isPresent()) {
            McpStatelessSyncServer s = statelessSync.get();
            return new ToolServer(
                (tool, handler) -> s.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(tool).callHandler((context, request) -> handler.apply(request.arguments())).build()),
                s::removeTool);
        }
        var sync = beanContext.findBean(McpSyncServer.class);
        if (sync.isPresent()) {
            McpSyncServer s = sync.get();
            return new ToolServer(
                (tool, handler) -> s.addTool(McpServerFeatures.SyncToolSpecification.builder()
                    .tool(tool).callHandler((exchange, request) -> handler.apply(request.arguments())).build()),
                s::removeTool);
        }
        var statelessAsync = beanContext.findBean(McpStatelessAsyncServer.class);
        if (statelessAsync.isPresent()) {
            McpStatelessAsyncServer s = statelessAsync.get();
            return new ToolServer(
                (tool, handler) -> s.addTool(McpStatelessServerFeatures.AsyncToolSpecification.builder()
                    .tool(tool).callHandler((context, request) -> blocking(handler, request)).build()).block(),
                name -> s.removeTool(name).block());
        }
        var async = beanContext.findBean(McpAsyncServer.class);
        if (async.isPresent()) {
            McpAsyncServer s = async.get();
            return new ToolServer(
                (tool, handler) -> s.addTool(McpServerFeatures.AsyncToolSpecification.builder()
                    .tool(tool).callHandler((exchange, request) -> blocking(handler, request)).build()).block(),
                name -> s.removeTool(name).block());
        }
        throw new IllegalStateException("No Micronaut MCP server: add micronaut-mcp-server-java-sdk and enable micronaut.mcp.server");
    }

    private static Mono<McpSchema.CallToolResult> blocking(Function<@Nullable Map<String, Object>, McpSchema.CallToolResult> handler,
                                                           McpSchema.CallToolRequest request) {
        // Camel runs the route of the tool on the calling thread, until the tool timeout
        return Mono.fromCallable(() -> handler.apply(request.arguments())).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Maps the ai-tool hints: the title to the title of the tool, and the boolean hints to the tool annotations.
     */
    private static void applyAnnotations(McpSchema.Tool.Builder builder, @Nullable AiToolAnnotations annotations) {
        if (annotations == null) {
            return;
        }
        if (annotations.title() != null) {
            builder.title(annotations.title());
        }
        if (annotations.readOnlyHint() == null && annotations.destructiveHint() == null
            && annotations.idempotentHint() == null && annotations.openWorldHint() == null) {
            return;
        }
        builder.annotations(McpSchema.ToolAnnotations.builder()
            .readOnlyHint(annotations.readOnlyHint())
            .destructiveHint(annotations.destructiveHint())
            .idempotentHint(annotations.idempotentHint())
            .openWorldHint(annotations.openWorldHint())
            .build());
    }

    /**
     * Adds and removes the tools of one kind of MCP server.
     *
     * @param adder   Adds a tool with its handler
     * @param remover Removes a tool by name
     */
    private record ToolServer(ToolAdder adder, java.util.function.Consumer<String> remover) {

        void add(McpSchema.Tool tool, Function<@Nullable Map<String, Object>, McpSchema.CallToolResult> handler) {
            adder.add(tool, handler);
        }

        void remove(String name) {
            remover.accept(name);
        }
    }

    /**
     * Adds a tool to an MCP server.
     */
    @FunctionalInterface
    private interface ToolAdder {
        void add(McpSchema.Tool tool, Function<@Nullable Map<String, Object>, McpSchema.CallToolResult> handler);
    }
}
