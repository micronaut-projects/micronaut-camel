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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dumps the route coverage of the test, the number of exchanges each route and each processor of a route
 * processed, to an XML file when the Camel context of the test stops, as {@code @EnableRouteCoverage} of
 * Camel's Spring Boot test support does. Tools such as the {@code route-coverage} goal of the
 * {@code camel-report-maven-plugin} read these files.
 *
 * <p>The file is {@code camel-route-coverage/<test class>-<test method>.xml} in the build directory
 * ({@code build}, or {@code target} for a Maven project), or in the directory set with
 * {@code micronaut.camel.test.route-coverage.directory}, where the test method is the last test of the
 * class to run, as the Camel context is shared by the tests of the class. It needs Camel's JMX management:
 * the {@code camel-management} dependency, with {@code camel.main.jmx-enabled} left to {@code true}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableRouteCoverage {
}
