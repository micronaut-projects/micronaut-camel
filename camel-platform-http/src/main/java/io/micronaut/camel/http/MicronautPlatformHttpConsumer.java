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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.Method;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A {@code platform-http} consumer served by the Micronaut HTTP server.
 *
 * <p>The consumer serves requests once its route has started, and stops serving them when the route
 * stops. While the route is suspended, requests receive a {@code 503 Service Unavailable} response.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public final class MicronautPlatformHttpConsumer extends DefaultConsumer implements PlatformHttpConsumer, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautPlatformHttpConsumer.class);

    private final MicronautPlatformHttpEngine engine;
    private final PlatformHttpPath httpPath;
    private final Set<Method> methods;
    private final List<MediaType> consumes;
    private final List<MediaType> produces;
    private final MicronautPlatformHttpBinding binding;

    /**
     * @param endpoint  The endpoint
     * @param processor The processor of the route
     * @param engine    The engine
     */
    MicronautPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor, MicronautPlatformHttpEngine engine) {
        super(endpoint, processor);
        this.engine = engine;
        this.httpPath = PlatformHttpPath.of(endpoint.getPath(), endpoint.isMatchOnUriPrefix());
        this.methods = Method.parseList(endpoint.getHttpMethodRestrict());
        this.consumes = mediaTypes(endpoint.getConsumes());
        this.produces = mediaTypes(endpoint.getProduces());
        this.binding = new MicronautPlatformHttpBinding(endpoint, httpPath);
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        engine.register(this);
    }

    @Override
    protected void doStop() throws Exception {
        engine.unregister(this);
        super.doStop();
    }

    /**
     * Routes a request through Camel.
     *
     * @param request        The request
     * @param path           The request path without the server context path
     * @param pathParameters The values of the placeholders of the consumer path
     * @param body           The request body: a byte array, an input stream or {@code null}
     * @return The response, completed when Camel has processed the exchange
     */
    CompletableFuture<MutableHttpResponse<?>> handle(HttpRequest<?> request, String path,
                                                     Map<String, String> pathParameters, @Nullable Object body) {
        if (isSuspended() || !isRunAllowed()) {
            LOG.debug("Consumer suspended, cannot service request: {} {}", request.getMethodName(), path);
            return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
        }
        Exchange exchange = createExchange(false);
        exchange.setPattern(ExchangePattern.InOut);
        try {
            binding.readRequest(exchange, request, path, pathParameters, body);
            createUoW(exchange);
            if (body instanceof InputStream stream) {
                // a streamed request body the route did not read must still be released
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange done) {
                        IOHelper.close(stream);
                    }
                });
            }
        } catch (Exception e) {
            // the exchange did not reach the processor, so it must be released here
            releaseExchange(exchange, false);
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<MutableHttpResponse<?>> response = new CompletableFuture<>();
        try {
            getAsyncProcessor().process(exchange, doneSync -> response.complete(afterProcess(exchange)));
        } catch (Exception e) {
            exchange.setException(e);
            response.complete(afterProcess(exchange));
        }
        return response;
    }

    private MutableHttpResponse<?> afterProcess(Exchange exchange) {
        try {
            return binding.writeResponse(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(
                "Failed writing HTTP response url: " + getEndpoint().getPath() + " due to: " + e.getMessage(), e);
            if (getEndpoint().isHandleWriteResponseError()) {
                Exception existing = exchange.getException();
                if (existing != null) {
                    e.addSuppressed(existing);
                }
                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
                exchange.setException(e);
            }
            return HttpResponse.serverError();
        } finally {
            doneUoW(exchange);
            releaseExchange(exchange, false);
        }
    }

    PlatformHttpPath getHttpPath() {
        return httpPath;
    }

    boolean allowsMethod(String method) {
        for (Method m : methods) {
            if (m.name().equals(method)) {
                return true;
            }
        }
        return false;
    }

    Set<String> getAllowedMethods() {
        return methods.stream().map(Enum::name).collect(Collectors.toSet());
    }

    List<MediaType> getConsumes() {
        return consumes;
    }

    List<MediaType> getProduces() {
        return produces;
    }

    boolean isServerRequestValidation() {
        return getEndpoint().getComponent().isServerRequestValidation();
    }

    long getRequestTimeout() {
        return getEndpoint().getRequestTimeout();
    }

    boolean isUseStreaming() {
        return getEndpoint().isUseStreaming();
    }

    @Override
    public String toString() {
        return "MicronautPlatformHttpConsumer[" + httpPath + "]";
    }

    private static List<MediaType> mediaTypes(@Nullable String types) {
        if (types == null || types.isBlank()) {
            return List.of();
        }
        return Arrays.stream(types.split(","))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .map(MediaType::of)
            .filter(t -> !MediaType.ALL_TYPE.equals(t))
            .toList();
    }
}
