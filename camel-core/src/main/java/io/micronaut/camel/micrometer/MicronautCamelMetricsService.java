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
package io.micronaut.camel.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.camel.requires.RequiresCamelMicrometer;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifierNamingStrategyDefault;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifierNamingStrategyLegacy;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifierNamingStrategy;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryNamingStrategy;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyNamingStrategy;
import org.apache.camel.component.micrometer.spi.InstrumentedThreadPoolFactory;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelMetricsService;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The Camel metrics service that camel-main enables with {@code camel.metrics.enabled=true}, recording
 * Camel's metrics in the Micronaut {@link MeterRegistry} instead of a Prometheus registry of its own.
 *
 * <p>camel-main finds this bean in the registry and applies the {@code camel.metrics.*} options to it.
 * The options that only concern Camel's own Prometheus scraping endpoint, {@code path} and
 * {@code text-format-version}, are ignored, since Micronaut exposes the registry through its
 * {@code metrics} and {@code prometheus} endpoints. Micrometer binders are configured with Micronaut's
 * {@code micronaut.metrics.binders.*} options rather than {@code camel.metrics.binders}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@RequiresCamelMicrometer
@Requires(classes = {MeterRegistry.class, MicrometerRoutePolicyFactory.class})
@Requires(beans = MeterRegistry.class)
@Internal
public final class MicronautCamelMetricsService extends ServiceSupport implements CamelMetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautCamelMetricsService.class);
    private static final String LEGACY = "legacy";

    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private @Nullable CamelContext camelContext;
    private @Nullable MeterRegistry meterRegistry;
    private @Nullable String namingStrategy;
    private boolean enableRoutePolicy = true;
    private String routePolicyLevel = "all";
    private boolean enableMessageHistory;
    private boolean enableExchangeEventNotifier = true;
    private boolean baseEndpointURIExchangeEventNotifier = true;
    private boolean enableRouteEventNotifier = true;
    private boolean enableInstrumentedThreadPoolFactory;
    private boolean clearOnReload = true;
    private boolean skipCamelInfo;
    private boolean logMetricsOnShutdown;
    private @Nullable String logMetricsOnShutdownFilters;
    private @Nullable String binders;
    private @Nullable String path;
    private @Nullable String textFormatVersion;

    /**
     * Creates the service, which uses the primary Micronaut meter registry.
     *
     * @param meterRegistryProvider The Micronaut meter registry
     */
    MicronautCamelMetricsService(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Returns the meter registry, once the service is initialized.
     *
     * @return The meter registry, once the service is initialized
     */
    public @Nullable MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Returns the naming strategy of the metrics, {@code default} or {@code legacy}.
     *
     * @return The naming strategy of the metrics, {@code default} or {@code legacy}
     */
    public @Nullable String getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Sets {@code camel.metrics.naming-strategy}.
     *
     * @param namingStrategy The naming strategy of the metrics, {@code default} (Micrometer's naming
     *                       convention) or {@code legacy} (camel case)
     */
    public void setNamingStrategy(@Nullable String namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    /**
     * Returns whether route processing times are recorded.
     *
     * @return Whether route processing times are recorded
     */
    public boolean isEnableRoutePolicy() {
        return enableRoutePolicy;
    }

    /**
     * Sets {@code camel.metrics.enable-route-policy}.
     *
     * @param enableRoutePolicy Whether to record route processing times with a route policy
     */
    public void setEnableRoutePolicy(boolean enableRoutePolicy) {
        this.enableRoutePolicy = enableRoutePolicy;
    }

    /**
     * Returns the level of the route policy metrics.
     *
     * @return The level of the route policy metrics
     */
    public String getRoutePolicyLevel() {
        return routePolicyLevel;
    }

    /**
     * Sets {@code camel.metrics.route-policy-level}.
     *
     * @param routePolicyLevel The level of the route policy metrics: {@code all}, {@code route} or
     *                         {@code context}
     */
    public void setRoutePolicyLevel(String routePolicyLevel) {
        this.routePolicyLevel = routePolicyLevel;
    }

    /**
     * Returns whether the processing times of individual route nodes are recorded.
     *
     * @return Whether the processing times of individual route nodes are recorded
     */
    public boolean isEnableMessageHistory() {
        return enableMessageHistory;
    }

    /**
     * Sets {@code camel.metrics.enable-message-history}.
     *
     * @param enableMessageHistory Whether to record the processing times of individual route nodes
     */
    public void setEnableMessageHistory(boolean enableMessageHistory) {
        this.enableMessageHistory = enableMessageHistory;
    }

    /**
     * Returns whether exchange processing times are recorded.
     *
     * @return Whether exchange processing times are recorded
     */
    public boolean isEnableExchangeEventNotifier() {
        return enableExchangeEventNotifier;
    }

    /**
     * Sets {@code camel.metrics.enable-exchange-event-notifier}.
     *
     * @param enableExchangeEventNotifier Whether to record exchange processing times
     */
    public void setEnableExchangeEventNotifier(boolean enableExchangeEventNotifier) {
        this.enableExchangeEventNotifier = enableExchangeEventNotifier;
    }

    /**
     * Returns whether exchange metrics are tagged with the base endpoint URI.
     *
     * @return Whether exchange metrics are tagged with the base endpoint URI
     */
    public boolean isBaseEndpointURIExchangeEventNotifier() {
        return baseEndpointURIExchangeEventNotifier;
    }

    /**
     * Sets {@code camel.metrics.base-endpoint-uri-exchange-event-notifier}.
     *
     * @param baseEndpointURIExchangeEventNotifier Whether to tag exchange metrics with the base endpoint URI,
     *                                             rather than the full URI
     */
    public void setBaseEndpointURIExchangeEventNotifier(boolean baseEndpointURIExchangeEventNotifier) {
        this.baseEndpointURIExchangeEventNotifier = baseEndpointURIExchangeEventNotifier;
    }

    /**
     * Returns whether the number of routes and running routes is recorded.
     *
     * @return Whether the number of routes and running routes is recorded
     */
    public boolean isEnableRouteEventNotifier() {
        return enableRouteEventNotifier;
    }

    /**
     * Sets {@code camel.metrics.enable-route-event-notifier}.
     *
     * @param enableRouteEventNotifier Whether to record the number of routes and running routes
     */
    public void setEnableRouteEventNotifier(boolean enableRouteEventNotifier) {
        this.enableRouteEventNotifier = enableRouteEventNotifier;
    }

    /**
     * Returns whether Camel's thread pools are instrumented.
     *
     * @return Whether Camel's thread pools are instrumented
     */
    public boolean isEnableInstrumentedThreadPoolFactory() {
        return enableInstrumentedThreadPoolFactory;
    }

    /**
     * Sets {@code camel.metrics.enable-instrumented-thread-pool-factory}.
     *
     * @param enableInstrumentedThreadPoolFactory Whether to instrument Camel's thread pools
     */
    public void setEnableInstrumentedThreadPoolFactory(boolean enableInstrumentedThreadPoolFactory) {
        this.enableInstrumentedThreadPoolFactory = enableInstrumentedThreadPoolFactory;
    }

    /**
     * Returns whether route metrics are removed when routes are reloaded.
     *
     * @return Whether route metrics are removed when routes are reloaded
     */
    public boolean isClearOnReload() {
        return clearOnReload;
    }

    /**
     * Sets {@code camel.metrics.clear-on-reload}.
     *
     * @param clearOnReload Whether to remove route metrics when routes are reloaded
     */
    public void setClearOnReload(boolean clearOnReload) {
        this.clearOnReload = clearOnReload;
    }

    /**
     * Returns whether the {@code app.info} metric is skipped.
     *
     * @return Whether the {@code app.info} metric is skipped
     */
    public boolean isSkipCamelInfo() {
        return skipCamelInfo;
    }

    /**
     * Sets {@code camel.metrics.skip-camel-info}.
     *
     * @param skipCamelInfo Whether to skip the {@code app.info} metric
     */
    public void setSkipCamelInfo(boolean skipCamelInfo) {
        this.skipCamelInfo = skipCamelInfo;
    }

    /**
     * Returns whether metrics are logged when Camel stops.
     *
     * @return Whether metrics are logged when Camel stops
     */
    public boolean isLogMetricsOnShutdown() {
        return logMetricsOnShutdown;
    }

    /**
     * Sets {@code camel.metrics.log-metrics-on-shutdown}.
     *
     * @param logMetricsOnShutdown Whether to log metrics when Camel stops
     */
    public void setLogMetricsOnShutdown(boolean logMetricsOnShutdown) {
        this.logMetricsOnShutdown = logMetricsOnShutdown;
    }

    /**
     * Returns the metrics logged when Camel stops.
     *
     * @return The metrics logged when Camel stops
     */
    public @Nullable String getLogMetricsOnShutdownFilters() {
        return logMetricsOnShutdownFilters;
    }

    /**
     * Sets {@code camel.metrics.log-metrics-on-shutdown-filters}.
     *
     * @param logMetricsOnShutdownFilters The metrics to log when Camel stops, separated by commas, which
     *                                    may use {@code *} as a wildcard
     */
    public void setLogMetricsOnShutdownFilters(@Nullable String logMetricsOnShutdownFilters) {
        this.logMetricsOnShutdownFilters = logMetricsOnShutdownFilters;
    }

    /**
     * Returns the binders option, which is not supported.
     *
     * @return The binders option, which is not supported
     */
    public @Nullable String getBinders() {
        return binders;
    }

    /**
     * Not supported: configure binders with {@code micronaut.metrics.binders.*}.
     *
     * @param binders The Micrometer binders
     */
    public void setBinders(@Nullable String binders) {
        this.binders = binders;
    }

    /**
     * Returns the path option, which is ignored.
     *
     * @return The path option, which is ignored
     */
    public @Nullable String getPath() {
        return path;
    }

    /**
     * Ignored: the metrics are exposed by the Micronaut {@code metrics} and {@code prometheus} endpoints.
     *
     * @param path The path of Camel's Prometheus scraping endpoint
     */
    public void setPath(@Nullable String path) {
        this.path = path;
    }

    /**
     * Returns the text format version option, which is ignored.
     *
     * @return The text format version option, which is ignored
     */
    public @Nullable String getTextFormatVersion() {
        return textFormatVersion;
    }

    /**
     * Ignored: the metrics are exposed by the Micronaut {@code metrics} and {@code prometheus} endpoints.
     *
     * @param textFormatVersion The text format of Camel's Prometheus scraping endpoint
     */
    public void setTextFormatVersion(@Nullable String textFormatVersion) {
        this.textFormatVersion = textFormatVersion;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        CamelContext context = camelContext;
        if (context == null) {
            throw new IllegalStateException("CamelContext must be set");
        }
        MeterRegistry registry = meterRegistryProvider.get();
        meterRegistry = registry;
        if (binders != null && !binders.isBlank()) {
            LOG.warn("camel.metrics.binders is not supported, configure Micrometer binders with micronaut.metrics.binders.* instead");
        }
        boolean legacy = LEGACY.equalsIgnoreCase(namingStrategy);
        if (enableRoutePolicy) {
            MicrometerRoutePolicyFactory factory = routePolicyFactory(registry, legacy);
            // ensures the factory is started and stopped with Camel
            context.addService(factory);
            context.addRoutePolicyFactory(factory);
        }
        ManagementStrategy managementStrategy = context.getManagementStrategy();
        if (enableExchangeEventNotifier) {
            managementStrategy.addEventNotifier(exchangeEventNotifier(registry, legacy));
        }
        if (enableRouteEventNotifier) {
            MicrometerRouteEventNotifier notifier = new MicrometerRouteEventNotifier();
            notifier.setSkipCamelInfo(skipCamelInfo);
            if (legacy) {
                notifier.setNamingStrategy(MicrometerRouteEventNotifierNamingStrategy.LEGACY);
            }
            notifier.setMeterRegistry(registry);
            managementStrategy.addEventNotifier(notifier);
        }
        if (enableMessageHistory) {
            if (!context.isMessageHistory()) {
                context.setMessageHistory(true);
            }
            MicrometerMessageHistoryFactory factory = new MicrometerMessageHistoryFactory();
            if (legacy) {
                factory.setNamingStrategy(MicrometerMessageHistoryNamingStrategy.LEGACY);
            }
            factory.setMeterRegistry(registry);
            context.setMessageHistoryFactory(factory);
        }
        if (enableInstrumentedThreadPoolFactory) {
            context.getExecutorServiceManager().setThreadPoolFactory(
                new InstrumentedThreadPoolFactory(registry, context.getExecutorServiceManager().getThreadPoolFactory()));
        }
        if (clearOnReload) {
            managementStrategy.addEventNotifier(new ClearOnReloadNotifier(registry));
        }
    }

    private MicrometerRoutePolicyFactory routePolicyFactory(MeterRegistry registry, boolean legacy) {
        MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
        factory.setSkipCamelInfo(skipCamelInfo);
        if (legacy) {
            factory.setNamingStrategy(MicrometerRoutePolicyNamingStrategy.LEGACY);
        }
        boolean context = "all".equalsIgnoreCase(routePolicyLevel) || "context".equalsIgnoreCase(routePolicyLevel);
        boolean route = !"context".equalsIgnoreCase(routePolicyLevel);
        factory.getPolicyConfiguration().setContextEnabled(context);
        factory.getPolicyConfiguration().setRouteEnabled(route);
        factory.setMeterRegistry(registry);
        return factory;
    }

    private MicrometerExchangeEventNotifier exchangeEventNotifier(MeterRegistry registry, boolean legacy) {
        MicrometerExchangeEventNotifier notifier = new MicrometerExchangeEventNotifier();
        notifier.setSkipCamelInfo(skipCamelInfo);
        notifier.setLogMetricsOnShutdown(logMetricsOnShutdown);
        if (logMetricsOnShutdownFilters != null) {
            notifier.setLogMetricsOnShutdownFilters(logMetricsOnShutdownFilters.split(","));
        }
        notifier.setBaseEndpointURI(baseEndpointURIExchangeEventNotifier);
        notifier.setNamingStrategy(legacy
            ? new MicrometerExchangeEventNotifierNamingStrategyLegacy(baseEndpointURIExchangeEventNotifier)
            : new MicrometerExchangeEventNotifierNamingStrategyDefault(baseEndpointURIExchangeEventNotifier));
        notifier.setMeterRegistry(registry);
        return notifier;
    }

    /**
     * Removes the route metrics, except the exchange counters, when Camel reloads routes.
     */
    private static final class ClearOnReloadNotifier extends SimpleEventNotifierSupport {

        private final MeterRegistry registry;

        ClearOnReloadNotifier(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.RouteReloadedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            if (event instanceof CamelEvent.RouteReloadedEvent reloaded && reloaded.getIndex() >= reloaded.getTotal()) {
                List<Meter> routeMeters = registry.getMeters().stream()
                    .filter(ClearOnReloadNotifier::isRouteMeter)
                    .toList();
                routeMeters.forEach(registry::remove);
            }
        }

        private static boolean isRouteMeter(Meter meter) {
            String name = meter.getId().getName();
            boolean camel = name.startsWith("camel_") || name.startsWith("camel.");
            boolean exchanges = name.startsWith("camel.exchanges.") || name.startsWith("camel_exchanges_");
            return camel && !exchanges && meter.getId().getTag("routeId") != null;
        }
    }
}
