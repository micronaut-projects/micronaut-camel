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

import io.micronaut.core.annotation.Internal;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStoppingEvent;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.TypeConverterSupport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the route coverage of a test class, from Camel's managed Camel context, when the Camel context
 * stops, in the format of Camel's {@code RouteCoverageDumper}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class RouteCoverageDumper extends EventNotifierSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RouteCoverageDumper.class);

    private final String testClass;
    private final Path directory;
    private volatile @Nullable String testMethod;

    /**
     * @param testClass The name of the test class
     * @param directory The directory of the files
     */
    RouteCoverageDumper(String testClass, Path directory) {
        this.testClass = testClass;
        this.directory = directory;
        setIgnoreCamelContextEvents(false);
    }

    /**
     * Lets Camel write the coverage of the processors, which it reads as a DOM document: without
     * camel-xml-jaxp, Camel has no converter from a DOM document to a string.
     *
     * @param camelContext The Camel context
     */
    static void registerDomConverter(CamelContext camelContext) {
        TypeConverterRegistry registry = camelContext.getTypeConverterRegistry();
        if (registry.lookup(String.class, Document.class) == null) {
            registry.addTypeConverter(String.class, Document.class, new TypeConverterSupport() {
                @Override
                public <T> @Nullable T convertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value) {
                    if (!(value instanceof Document document)) {
                        return null;
                    }
                    try {
                        Transformer transformer = TransformerFactory.newInstance().newTransformer();
                        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                        StringWriter writer = new StringWriter();
                        transformer.transform(new DOMSource(document), new StreamResult(writer));
                        return type.cast(writer.toString());
                    } catch (TransformerException e) {
                        throw new TypeConversionException(value, type, e);
                    }
                }
            });
        }
    }

    /**
     * @param testMethod The name of the test method that ran last
     */
    void setTestMethod(String testMethod) {
        this.testMethod = testMethod;
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelContextStoppingEvent;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        dump(((CamelContextStoppingEvent) event).getContext());
    }

    /**
     * Writes the route coverage of the Camel context.
     *
     * @param camelContext The Camel context
     * @return The file written, or {@code null} when JMX is not enabled
     * @throws Exception if the coverage cannot be read or written
     */
    @Nullable Path dump(CamelContext camelContext) throws Exception {
        ManagedCamelContext managed = camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        ManagedCamelContextMBean mbean = managed != null ? managed.getManagedCamelContext() : null;
        if (mbean == null) {
            LOG.warn("Cannot dump the route coverage of {}: Camel's JMX management is not enabled. Add the camel-management "
                + "dependency, and do not set camel.main.jmx-enabled to false", testClass);
            return null;
        }
        String method = testMethod;
        StringBuilder xml = new StringBuilder("<camelRouteCoverage>\n<test>\n  <class>").append(testClass).append("</class>\n");
        if (method != null) {
            xml.append("  <method>").append(method).append("</method>\n");
        }
        xml.append("</test>\n").append(mbean.dumpRoutesCoverageAsXml()).append("\n</camelRouteCoverage>\n");
        Path file = directory.resolve(method != null ? testClass + "-" + method + ".xml" : testClass + ".xml");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Cannot dump the route coverage of {} to {}: {}", testClass, file, e.getMessage(), e);
            return null;
        }
        LOG.info("Dumped the route coverage of {} to {}", testClass, file);
        return file;
    }
}
