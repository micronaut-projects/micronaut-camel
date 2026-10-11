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
package io.micronaut.camel.test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.support.DefaultAutoMockInterceptStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Applies the Camel test annotations of the running Micronaut test, such as {@link MockEndpoints},
 * while Camel is configured and before its routes start.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(property = CamelTestAnnotations.ACTIVE_TEST_CLASS)
final class CamelTestAnnotations extends MainListenerSupport {

    /**
     * The property in which Micronaut Test stores the class of the running test.
     */
    static final String ACTIVE_TEST_CLASS = "micronaut.test.active.spec.clazz";

    private static final Logger LOG = LoggerFactory.getLogger(CamelTestAnnotations.class);
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Class<?> testClass;

    /**
     * @param environment The environment
     */
    CamelTestAnnotations(Environment environment) {
        this.testClass = environment.getRequiredProperty(ACTIVE_TEST_CLASS, Class.class);
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
        CamelContext camelContext = main.getCamelContext();
        applyShutdownTimeout(camelContext);

        MockEndpoints mockEndpoints = testClass.getAnnotation(MockEndpoints.class);
        if (mockEndpoints != null) {
            mock(camelContext, mockEndpoints.value(), false);
        }
        MockEndpointsAndSkip mockAndSkip = testClass.getAnnotation(MockEndpointsAndSkip.class);
        if (mockAndSkip != null) {
            mock(camelContext, mockAndSkip.value(), true);
        }
        ExcludeRoutes excludeRoutes = testClass.getAnnotation(ExcludeRoutes.class);
        if (excludeRoutes != null) {
            String excluded = Arrays.stream(excludeRoutes.value()).map(Class::getName).collect(Collectors.joining(","));
            LOG.info("Excluding routes {} from the Camel context of {}", excluded, testClass.getName());
            DefaultCamelContext.setExcludeRoutes(excluded);
        }
        AutoStartupExclude autoStartupExclude = testClass.getAnnotation(AutoStartupExclude.class);
        if (autoStartupExclude != null) {
            camelContext.setAutoStartupExcludePattern(autoStartupExclude.value());
        }
        UseAdviceWith useAdviceWith = testClass.getAnnotation(UseAdviceWith.class);
        if (useAdviceWith != null && useAdviceWith.value()) {
            LOG.info("Not starting the Camel context of {}: the test starts it after advising the routes", testClass.getName());
            DefaultCamelContext.setNoStart(true);
        }
    }

    private void applyShutdownTimeout(CamelContext camelContext) {
        ShutdownTimeout shutdownTimeout = testClass.getAnnotation(ShutdownTimeout.class);
        int timeout = shutdownTimeout != null ? shutdownTimeout.value() : DEFAULT_SHUTDOWN_TIMEOUT_SECONDS;
        TimeUnit unit = shutdownTimeout != null ? shutdownTimeout.timeUnit() : TimeUnit.SECONDS;
        camelContext.getShutdownStrategy().setTimeout(timeout);
        camelContext.getShutdownStrategy().setTimeUnit(unit);
    }

    private void mock(CamelContext camelContext, String pattern, boolean skip) {
        String resolved = camelContext.resolvePropertyPlaceholders(pattern);
        LOG.info("Mocking{} the endpoints matching {} in the Camel context of {}", skip ? " and skipping" : "", resolved,
            testClass.getName());
        camelContext.getCamelContextExtension().registerAutoMockInterceptStrategy(new DefaultAutoMockInterceptStrategy(resolved, skip));
    }
}
