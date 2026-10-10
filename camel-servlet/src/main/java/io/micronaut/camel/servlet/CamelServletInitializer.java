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
package io.micronaut.camel.servlet;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Set;

/**
 * Registers Camel's servlet with the servlet containers of Micronaut Servlet, which run the
 * {@link ServletContainerInitializer} beans of the application. The routes that consume from
 * {@code servlet:} endpoints, and the REST DSL with the {@code servlet} component, are then served
 * by the container, next to the Micronaut servlet.
 *
 * <p>The {@code camel.servlet.mapping.*} options of Camel Spring Boot configure the registration.
 * They are not {@code @ConfigurationProperties}: the module holds the generated configuration of
 * the {@code servlet} component, and Micronaut writes the configuration metadata of a single
 * annotation processing round.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = ServletContainerInitializer.class)
@Requires(property = CamelServletInitializer.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
final class CamelServletInitializer implements ServletContainerInitializer {

    /**
     * The prefix of the options.
     */
    static final String PREFIX = "camel.servlet.mapping";

    private final String contextPath;
    private final String servletName;
    private volatile @Nullable OwnedServlet servlet;

    /**
     * @param contextPath The URL mapping of the servlet, below the context path of the server
     * @param servletName The name of the servlet, which is the {@code servletName} of the endpoints it serves
     */
    CamelServletInitializer(@Value("${" + PREFIX + ".context-path:/camel/*}") String contextPath,
                            @Value("${" + PREFIX + ".servlet-name:CamelServlet}") String servletName) {
        this.contextPath = contextPath;
        this.servletName = servletName;
    }

    @Override
    public void onStartup(@Nullable Set<Class<?>> classes, ServletContext servletContext) {
        OwnedServlet camelServlet = new OwnedServlet();
        ServletRegistration.Dynamic registration = servletContext.addServlet(servletName, camelServlet);
        if (registration == null) {
            throw new IllegalStateException("A servlet named " + servletName + " is already registered: set "
                + PREFIX + ".servlet-name");
        }
        registration.addMapping(contextPath);
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        servlet = camelServlet;
    }

    /**
     * Destroys the servlet when the application stops, if the container has not: Undertow does
     * not, and the servlet must leave Camel's registry of servlets, which belongs to the JVM.
     */
    @PreDestroy
    void destroyServlet() {
        OwnedServlet camelServlet = servlet;
        servlet = null;
        if (camelServlet != null) {
            camelServlet.destroy();
        }
    }

    /**
     * Camel's servlet, destroyed once.
     */
    private static final class OwnedServlet extends CamelHttpTransportServlet {

        @Serial
        private static final long serialVersionUID = 1L;

        private volatile boolean destroyed;

        @Override
        public void destroy() {
            if (!destroyed && getServletConfig() != null) {
                destroyed = true;
                super.destroy();
            }
        }
    }
}
