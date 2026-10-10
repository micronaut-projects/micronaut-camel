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
package io.micronaut.camel.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteAttributes;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches the requests that no Micronaut route matches to the platform-http consumers.
 *
 * <p>The filter runs after every other server filter, so filters such as security apply to Camel routes
 * as they do to controllers, and it lets a matching Micronaut route handle the request. The request body
 * is read without blocking, then Camel processes the exchange on the blocking executor.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
final class PlatformHttpServerFilter implements Ordered {

    private final MicronautPlatformHttpEngine engine;
    private final Executor executor;
    private final String contextPath;

    /**
     * @param engine        The engine
     * @param executor      The executor that runs Camel
     * @param configuration The server configuration, for its context path
     */
    PlatformHttpServerFilter(MicronautPlatformHttpEngine engine,
                             @Named(TaskExecutors.BLOCKING) Executor executor,
                             HttpServerConfiguration configuration) {
        this.engine = engine;
        this.executor = executor;
        String path = configuration.getContextPath();
        this.contextPath = path == null || "/".equals(path) ? "" : stripTrailingSlash(path);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.LAST.order();
    }

    /**
     * @param request The request
     * @return The response of a Camel route, or {@code null} to continue with Micronaut
     */
    @RequestFilter
    @Nullable CompletableFuture<@Nullable MutableHttpResponse<?>> dispatch(HttpRequest<?> request) {
        if (!engine.hasConsumers() || RouteAttributes.getRouteMatch(request).isPresent()) {
            return null;
        }
        String path = applicationPath(request);
        if (path == null) {
            return null;
        }
        MicronautPlatformHttpEngine.Dispatch dispatch;
        try {
            dispatch = engine.resolve(request, path);
        } catch (HttpStatusException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (dispatch == null) {
            return null;
        }
        MicronautPlatformHttpConsumer consumer = dispatch.consumer();
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        CompletableFuture<@Nullable MutableHttpResponse<?>> response = readBody(request, consumer.isUseStreaming())
            .thenComposeAsync(body -> {
                try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
                    return consumer.handle(request, path, dispatch.pathParameters(), body);
                }
            }, executor);
        long timeout = consumer.getRequestTimeout();
        if (timeout > 0) {
            response = response.completeOnTimeout(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE), timeout, TimeUnit.MILLISECONDS);
        }
        return response;
    }

    private @Nullable String applicationPath(HttpRequest<?> request) {
        String path = request.getUri().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (contextPath.isEmpty()) {
            return path;
        }
        if (path.equals(contextPath)) {
            return "/";
        }
        if (path.startsWith(contextPath + "/")) {
            return path.substring(contextPath.length());
        }
        return null;
    }

    private static CompletableFuture<@Nullable Object> readBody(HttpRequest<?> request, boolean streaming) {
        if (!(request instanceof ServerHttpRequest<?> serverRequest)) {
            return CompletableFuture.completedFuture(request.getBody(byte[].class).orElse(null));
        }
        ByteBody body = serverRequest.byteBody();
        OptionalLong length = body.expectedLength();
        if (length.isPresent() && length.getAsLong() == 0) {
            return CompletableFuture.completedFuture(null);
        }
        if (streaming) {
            return CompletableFuture.completedFuture(body.split(ByteBody.SplitBackpressureMode.FASTEST).toInputStream());
        }
        return body.split(ByteBody.SplitBackpressureMode.FASTEST).buffer().thenApply(buffered -> {
            byte[] bytes = buffered.toByteArray();
            return bytes.length == 0 ? null : bytes;
        });
    }

    private static String stripTrailingSlash(String path) {
        String result = path.startsWith("/") ? path : "/" + path;
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
