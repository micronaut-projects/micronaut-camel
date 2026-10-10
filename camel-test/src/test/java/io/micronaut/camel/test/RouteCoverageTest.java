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

import io.micronaut.context.ApplicationContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteCoverageTest {

    @TempDir
    Path directory;

    @Test
    void theRouteCoverageIsDumpedWhenCamelStops() throws Exception {
        try (ApplicationContext context = run(CoveredTest.class)) {
            context.getBean(ProducerTemplate.class).requestBody("direct:order", "book");
            context.getBean(CamelTestAnnotations.class).testMethodStarted("ordersAreShipped");
        }

        Path file = directory.resolve(CoveredTest.class.getName() + "-ordersAreShipped.xml");
        assertTrue(Files.exists(file), () -> "no file " + file);
        String xml = Files.readString(file);
        assertTrue(xml.startsWith("<camelRouteCoverage>"), xml);
        assertTrue(xml.contains("<class>" + CoveredTest.class.getName() + "</class>"), xml);
        assertTrue(xml.contains("<method>ordersAreShipped</method>"), xml);
        // the orders route and the warehouse route it calls processed the exchange, the audit route did not
        assertEquals(Set.of("orders=1", "warehouse=1", "audit=0"), routeTotals(xml));
    }

    @Test
    void theFileOfATestWithADescriptiveNameHasASafeName() throws Exception {
        try (ApplicationContext context = run(CoveredTest.class)) {
            context.getBean(ProducerTemplate.class).requestBody("direct:order", "book");
            // a Spock feature name
            context.getBean(CamelTestAnnotations.class).testMethodStarted("orders/returns: are shipped <fast>");
        }

        Path file = directory.resolve(CoveredTest.class.getName() + "-orders_returns__are_shipped__fast_.xml");
        assertTrue(Files.exists(file), () -> "no file " + file);
        assertTrue(Files.readString(file).contains("<method>orders/returns: are shipped &lt;fast&gt;</method>"));
    }

    @Test
    void fileNamesKeepOnlySafeCharacters() {
        assertEquals("com.example.OrderSpec-orders_returns__are_shipped.xml",
            RouteCoverageDumper.fileName("com.example.OrderSpec", "orders/returns: are shipped"));
        // the binary names of nested classes keep their $
        assertEquals("com.example.Order$Spec.xml", RouteCoverageDumper.fileName("com.example.Order$Spec", null));
        assertEquals("a-b_c-1.2.xml", RouteCoverageDumper.fileName("a", "b_c-1.2"));
    }

    @Test
    void theRouteCoverageIsOnlyDumpedForAnnotatedTests() throws Exception {
        try (ApplicationContext context = run(UncoveredTest.class)) {
            context.getBean(ProducerTemplate.class).requestBody("direct:order", "book");
        }

        try (var files = Files.list(directory)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    private static Set<String> routeTotals(String xml) {
        Set<String> totals = new HashSet<>();
        Matcher route = Pattern.compile("<route ([^>]*)>").matcher(xml);
        while (route.find()) {
            String attributes = route.group(1);
            totals.add(attribute(attributes, "id") + "=" + attribute(attributes, "exchangesTotal"));
        }
        return totals;
    }

    private static String attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(attributes);
        return matcher.find() ? matcher.group(1) : "";
    }

    private ApplicationContext run(Class<?> testClass) {
        return ApplicationContext.run(Map.of(
            CamelTestAnnotations.ACTIVE_TEST_CLASS, testClass.getName(),
            CamelTestAnnotations.ROUTE_COVERAGE_DIRECTORY, directory.toString(),
            "camel.test.routes", "orders"));
    }

    @EnableRouteCoverage
    static class CoveredTest {
    }

    static class UncoveredTest {
    }
}
