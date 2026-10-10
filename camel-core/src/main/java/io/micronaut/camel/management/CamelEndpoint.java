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
package io.micronaut.camel.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Sensitive;
import io.micronaut.management.endpoint.annotation.Write;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedPerformanceCounterMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.URISupport;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes the {@link CamelContext} and its routes, and controls the routes, at {@code /camel}.
 *
 * <ul>
 *     <li>{@code GET /camel} returns the context and its routes.</li>
 *     <li>{@code GET /camel/{routeId}} returns a route.</li>
 *     <li>{@code POST /camel/{routeId}/{action}} starts, stops, suspends or resumes a route, or resets its
 *     statistics, with the action {@code start}, {@code stop}, {@code suspend}, {@code resume} or
 *     {@code reset}, and returns the route.</li>
 *     <li>{@code DELETE /camel/{routeId}} stops and removes a route.</li>
 * </ul>
 *
 * <p>Exchange statistics are included when Camel's JMX management ({@code camel-management}) is enabled.
 * The endpoint is sensitive, and the operations that change routes stay sensitive when the endpoint is
 * not, unless {@code endpoints.camel.write-sensitive} is {@code false}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Endpoint(id = CamelEndpoint.NAME, defaultSensitive = CamelEndpoint.DEFAULT_SENSITIVE)
@Requires(classes = Endpoint.class)
@Internal
final class CamelEndpoint {

    /**
     * The endpoint id.
     */
    static final String NAME = "camel";

    /**
     * The endpoint is sensitive by default.
     */
    static final boolean DEFAULT_SENSITIVE = true;

    private final CamelContext camelContext;

    /**
     * @param camelContext The Camel context
     */
    CamelEndpoint(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * @return The context and its routes
     */
    @Read
    Map<String, Object> context() {
        Map<String, Object> context = contextInfo(camelContext);
        statistics(managedContext()).ifPresent(statistics -> context.put("statistics", statistics));
        context.put("routes", camelContext.getRoutes().stream().map(this::routeInfo).toList());
        return context;
    }

    /**
     * @param routeId The route id
     * @return The route, or {@code null} when there is no such route
     */
    @Read
    @Nullable Map<String, Object> route(@Selector String routeId) {
        Route route = camelContext.getRoute(routeId);
        return route != null ? routeInfo(route) : null;
    }

    /**
     * Starts, stops, suspends or resumes a route, or resets its statistics.
     *
     * @param routeId The route id
     * @param action  The action
     * @return The route, or {@code null} when there is no such route
     */
    @Write(consumes = {})
    @Sensitive(property = "write-sensitive")
    @Nullable Map<String, Object> control(@Selector String routeId, @Selector RouteAction action) {
        Route route = camelContext.getRoute(routeId);
        if (route == null) {
            return null;
        }
        RouteController controller = camelContext.getRouteController();
        try {
            switch (action) {
                case START -> controller.startRoute(routeId);
                case STOP -> controller.stopRoute(routeId);
                case SUSPEND -> controller.suspendRoute(routeId);
                case RESUME -> controller.resumeRoute(routeId);
                case RESET -> {
                    ManagedRouteMBean managedRoute = managedRoute(routeId);
                    if (managedRoute == null) {
                        throw new IllegalStateException("Route statistics require camel-management with JMX enabled");
                    }
                    managedRoute.reset();
                }
                default -> throw new IllegalArgumentException("Unsupported action: " + action);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return routeInfo(route);
    }

    /**
     * Stops and removes a route.
     *
     * @param routeId The route id
     * @return The id of the route and whether it was removed, or {@code null} when there is no such route
     */
    @Delete
    @Sensitive(property = "write-sensitive")
    @Nullable Map<String, Object> remove(@Selector String routeId) {
        if (camelContext.getRoute(routeId) == null) {
            return null;
        }
        try {
            camelContext.getRouteController().stopRoute(routeId);
            return Map.of("id", routeId, "removed", camelContext.removeRoute(routeId));
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    /**
     * @param camelContext The Camel context
     * @return The name, version, status and uptime of the context
     */
    static Map<String, Object> contextInfo(CamelContext camelContext) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", camelContext.getName());
        info.put("version", camelContext.getVersion());
        info.put("status", camelContext.getStatus().name());
        if (camelContext.getUptime() != null) {
            info.put("uptime", CamelContextHelper.getUptime(camelContext));
            info.put("uptimeMillis", CamelContextHelper.getUptimeMillis(camelContext));
        }
        return info;
    }

    private Map<String, Object> routeInfo(Route route) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", route.getId());
        if (route.getGroup() != null) {
            info.put("group", route.getGroup());
        }
        if (route.getDescription() != null) {
            info.put("description", route.getDescription());
        }
        info.put("uri", URISupport.sanitizeUri(route.getEndpoint().getEndpointUri()));
        ServiceStatus status = camelContext.getRouteController().getRouteStatus(route.getId());
        info.put("status", status != null ? status.name() : ServiceStatus.Stopped.name());
        info.put("uptime", route.getUptime());
        info.put("uptimeMillis", route.getUptimeMillis());
        statistics(managedRoute(route.getId())).ifPresent(statistics -> info.put("statistics", statistics));
        return info;
    }

    private Optional<Map<String, Object>> statistics(@Nullable ManagedPerformanceCounterMBean counter) {
        if (counter == null) {
            return Optional.empty();
        }
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("exchangesTotal", counter.getExchangesTotal());
        statistics.put("exchangesCompleted", counter.getExchangesCompleted());
        statistics.put("exchangesFailed", counter.getExchangesFailed());
        statistics.put("exchangesInflight", counter.getExchangesInflight());
        statistics.put("failuresHandled", counter.getFailuresHandled());
        statistics.put("redeliveries", counter.getRedeliveries());
        statistics.put("minProcessingTime", counter.getMinProcessingTime());
        statistics.put("meanProcessingTime", counter.getMeanProcessingTime());
        statistics.put("maxProcessingTime", counter.getMaxProcessingTime());
        statistics.put("lastProcessingTime", counter.getLastProcessingTime());
        statistics.put("totalProcessingTime", counter.getTotalProcessingTime());
        return Optional.of(statistics);
    }

    private @Nullable ManagedCamelContextMBean managedContext() {
        ManagedCamelContext managed = managedCamelContext();
        return managed != null ? managed.getManagedCamelContext() : null;
    }

    private @Nullable ManagedRouteMBean managedRoute(String routeId) {
        ManagedCamelContext managed = managedCamelContext();
        return managed != null ? managed.getManagedRoute(routeId) : null;
    }

    private @Nullable ManagedCamelContext managedCamelContext() {
        if (camelContext.getManagementStrategy().getManagementAgent() == null) {
            // camel-management is not on the classpath, or JMX is disabled
            return null;
        }
        return camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
    }

    /**
     * The actions that control a route.
     */
    enum RouteAction {
        START, STOP, SUSPEND, RESUME, RESET
    }
}
