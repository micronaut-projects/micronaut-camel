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

import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.NotAcceptableException;
import io.micronaut.http.server.exceptions.NotAllowedException;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Camel {@link PlatformHttpEngine} of a Micronaut application: the {@code platform-http} component,
 * and therefore the REST DSL, serves its endpoints with the Micronaut HTTP server.
 *
 * <p>The engine is the bean named {@code platform-http-engine}, which the
 * {@code platform-http} component looks up in the Camel registry. Each consumer registers its path when
 * its route starts and removes it when the route stops. Requests are dispatched to the consumers by an
 * HTTP server filter, when no Micronaut route matches the request.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Named(PlatformHttpConstants.PLATFORM_HTTP_ENGINE_NAME)
public final class MicronautPlatformHttpEngine implements PlatformHttpEngine {

    private static final Comparator<MicronautPlatformHttpConsumer> BY_SPECIFICITY =
        Comparator.comparing(MicronautPlatformHttpConsumer::getHttpPath, PlatformHttpPath.SPECIFICITY);

    private final List<MicronautPlatformHttpConsumer> consumers = new CopyOnWriteArrayList<>();
    private volatile int port;

    /**
     * @param environment The environment, for the configured server port
     */
    MicronautPlatformHttpEngine(Environment environment) {
        this.port = Math.max(0, environment.getProperty("micronaut.server.port", Integer.class, 0));
    }

    @Override
    public PlatformHttpConsumer createConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
        return new MicronautPlatformHttpConsumer(endpoint, processor, this);
    }

    /**
     * @return The port of the HTTP server once it has started, otherwise the configured port, or 0 if the
     * port is chosen at random
     */
    @Override
    public int getServerPort() {
        return port;
    }

    /**
     * @return The consumers currently serving requests, most specific path first
     */
    public List<PlatformHttpConsumer> getConsumers() {
        return List.copyOf(consumers);
    }

    /**
     * Records the port the HTTP server listens on.
     *
     * @param event The server startup event
     */
    @EventListener
    void onServerStartup(ServerStartupEvent event) {
        port = event.getSource().getPort();
    }

    /**
     * @param consumer A started consumer
     */
    void register(MicronautPlatformHttpConsumer consumer) {
        synchronized (consumers) {
            consumers.add(consumer);
            consumers.sort(BY_SPECIFICITY);
        }
    }

    /**
     * @param consumer A stopped consumer
     */
    void unregister(MicronautPlatformHttpConsumer consumer) {
        consumers.remove(consumer);
    }

    /**
     * @return Whether any consumer is registered
     */
    boolean hasConsumers() {
        return !consumers.isEmpty();
    }

    /**
     * Finds the consumer for a request.
     *
     * @param request The request
     * @param path    The raw request path without the server context path
     * @return The consumer and its path parameters, or {@code null} if no consumer serves the path
     * @throws NotAllowedException       if consumers serve the path, but not with the request method
     * @throws UnsupportedMediaException if no consumer serving the path and method consumes the content type
     * @throws NotAcceptableException    if no consumer serving the path and method produces an acceptable type
     */
    @Nullable Dispatch resolve(HttpRequest<?> request, String path) {
        String method = request.getMethodName();
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> consumable = new LinkedHashSet<>();
        Set<String> producible = new LinkedHashSet<>();
        boolean pathMatched = false;
        MediaType contentType = request.getContentType().orElse(null);
        Collection<MediaType> accept = request.accept();
        for (MicronautPlatformHttpConsumer consumer : consumers) {
            Map<String, String> pathParameters = consumer.getHttpPath().match(path);
            if (pathParameters == null) {
                continue;
            }
            pathMatched = true;
            if (!consumer.allowsMethod(method)) {
                allowed.addAll(consumer.getAllowedMethods());
                continue;
            }
            if (consumer.isServerRequestValidation()) {
                if (contentType != null && !accepts(consumer.getConsumes(), List.of(contentType))) {
                    consumer.getConsumes().forEach(m -> consumable.add(m.toString()));
                    continue;
                }
                if (!accept.isEmpty() && !accepts(consumer.getProduces(), accept)) {
                    consumer.getProduces().forEach(m -> producible.add(m.toString()));
                    continue;
                }
            }
            return new Dispatch(consumer, pathParameters);
        }
        if (!pathMatched) {
            return null;
        }
        if (!consumable.isEmpty()) {
            throw new UnsupportedMediaException(String.valueOf(contentType), new ArrayList<>(consumable));
        }
        if (!producible.isEmpty()) {
            throw new NotAcceptableException(accept.stream().map(MediaType::toString).toList(), new ArrayList<>(producible));
        }
        throw new NotAllowedException(method, request.getUri(), allowed);
    }

    private static boolean accepts(List<MediaType> supported, Collection<MediaType> requested) {
        if (supported.isEmpty()) {
            return true;
        }
        for (MediaType s : supported) {
            for (MediaType r : requested) {
                if (s.matches(r) || r.matches(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A consumer selected for a request.
     *
     * @param consumer       The consumer
     * @param pathParameters The values of the placeholders of the consumer path
     */
    record Dispatch(MicronautPlatformHttpConsumer consumer, Map<String, String> pathParameters) {
    }
}
