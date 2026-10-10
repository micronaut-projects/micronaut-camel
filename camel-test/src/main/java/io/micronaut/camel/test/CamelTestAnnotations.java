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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.support.DefaultAutoMockInterceptStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Applies the Camel test annotations of the running Micronaut test, such as {@link MockEndpoints},
 * while Camel is configured and before its routes start.
 *
 * <p>The annotations are read from the annotation metadata of the test class, which a test written in a
 * language that does not retain them on the class at runtime, such as Python, still has.</p>
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

    /**
     * The property setting the directory of the route coverage files of {@link EnableRouteCoverage}.
     */
    static final String ROUTE_COVERAGE_DIRECTORY = "micronaut.camel.test.route-coverage.directory";

    private static final Logger LOG = LoggerFactory.getLogger(CamelTestAnnotations.class);
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final String MANAGED_CAMEL_CONTEXT = "org.apache.camel.api.management.ManagedCamelContext";

    private final Class<?> testClass;
    private final AnnotationMetadata testMetadata;
    private final Environment environment;
    private volatile @Nullable RouteCoverageDumper routeCoverage;

    /**
     * @param environment The environment
     * @param beanContext The bean context
     */
    CamelTestAnnotations(Environment environment, BeanContext beanContext) {
        this.environment = environment;
        this.testClass = environment.getRequiredProperty(ACTIVE_TEST_CLASS, Class.class);
        this.testMetadata = beanContext.findBeanDefinition(testClass)
            .map(BeanDefinition::getAnnotationMetadata)
            .orElse(AnnotationMetadata.EMPTY_METADATA);
    }

    @Override
    public void afterConfigure(BaseMainSupport main) {
        CamelContext camelContext = main.getCamelContext();
        applyShutdownTimeout(camelContext);

        MockEndpoints mockEndpoints = annotation(MockEndpoints.class);
        if (mockEndpoints != null) {
            mock(camelContext, mockEndpoints.value(), false);
        }
        MockEndpointsAndSkip mockAndSkip = annotation(MockEndpointsAndSkip.class);
        if (mockAndSkip != null) {
            mock(camelContext, mockAndSkip.value(), true);
        }
        ExcludeRoutes excludeRoutes = annotation(ExcludeRoutes.class);
        if (excludeRoutes != null) {
            String excluded = Arrays.stream(excludeRoutes.value()).map(Class::getName).collect(Collectors.joining(","));
            LOG.info("Excluding routes {} from the Camel context of {}", excluded, testClass.getName());
            DefaultCamelContext.setExcludeRoutes(excluded);
        }
        AutoStartupExclude autoStartupExclude = annotation(AutoStartupExclude.class);
        if (autoStartupExclude != null) {
            camelContext.setAutoStartupExcludePattern(autoStartupExclude.value());
        }
        if (annotation(EnableRouteCoverage.class) != null) {
            enableRouteCoverage(camelContext);
        }
        UseAdviceWith useAdviceWith = annotation(UseAdviceWith.class);
        if (useAdviceWith != null && useAdviceWith.value()) {
            LOG.info("Not starting the Camel context of {}: the test starts it after advising the routes", testClass.getName());
            DefaultCamelContext.setNoStart(true);
        }
    }

    /**
     * Records the test method that runs, which names the route coverage file.
     *
     * @param testMethod The name of the test method
     */
    void testMethodStarted(String testMethod) {
        RouteCoverageDumper dumper = routeCoverage;
        if (dumper != null) {
            dumper.setTestMethod(testMethod);
        }
    }

    private void enableRouteCoverage(CamelContext camelContext) {
        if (!ClassUtils.isPresent(MANAGED_CAMEL_CONTEXT, CamelTestAnnotations.class.getClassLoader())) {
            LOG.warn("Cannot dump the route coverage of {}: add the camel-management dependency", testClass.getName());
            return;
        }
        Path directory = environment.getProperty(ROUTE_COVERAGE_DIRECTORY, String.class)
            .map(Path::of)
            .orElseGet(CamelTestAnnotations::defaultRouteCoverageDirectory);
        RouteCoverageDumper dumper = new RouteCoverageDumper(testClass.getName(), directory);
        routeCoverage = dumper;
        RouteCoverageDumper.registerDomConverter(camelContext);
        camelContext.getManagementStrategy().addEventNotifier(dumper);
        LOG.info("Dumping the route coverage of {} to {} when Camel stops", testClass.getName(), directory);
    }

    // the build directory: target for a Maven project, build otherwise
    private static Path defaultRouteCoverageDirectory() {
        boolean maven = Files.exists(Path.of("pom.xml"))
            && !Files.exists(Path.of("build.gradle")) && !Files.exists(Path.of("build.gradle.kts"));
        return Path.of(maven ? "target" : "build", "camel-route-coverage");
    }

    private void applyShutdownTimeout(CamelContext camelContext) {
        ShutdownTimeout shutdownTimeout = annotation(ShutdownTimeout.class);
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

    private <A extends Annotation> @Nullable A annotation(Class<A> type) {
        A annotation = testMetadata.synthesize(type);
        return annotation != null ? annotation : testClass.getAnnotation(type);
    }
}
