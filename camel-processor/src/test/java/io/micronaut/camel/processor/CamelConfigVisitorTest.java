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
package io.micronaut.camel.processor;

import io.micronaut.annotation.processing.test.JavaParser;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.MainModel;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelConfigVisitorTest {

    @Test
    void generatesRequirementAnnotations() {
        Map<String, String> sources = generate("""
            package test;

            import io.micronaut.camel.annotation.CamelConfig;

            @CamelConfig(requirements = {CamelConfig.Kind.COMPONENT, CamelConfig.Kind.DATAFORMAT}, include = {"aws2-s3", "jackson"})
            final class Marker {
            }
            """);

        String component = sources.get("RequiresCamelAws2S3.java");
        assertTrue(component.contains("public @interface RequiresCamelAws2S3"), component);
        assertTrue(component.contains("resources = \"classpath:META-INF/services/org/apache/camel/component/aws2-s3\""), component);
        assertTrue(component.contains("property = \"camel.component.aws2-s3.enabled\""), component);
        assertTrue(component.contains("notEquals = \"false\""), component);

        String dataFormat = sources.get("RequiresCamelJacksonDataFormat.java");
        assertTrue(dataFormat.contains("classpath:META-INF/services/org/apache/camel/dataformat/jackson"), dataFormat);
        assertEquals(2, sources.size(), sources::toString);
    }

    @Test
    void generatesComponentConfiguration() {
        Map<String, String> sources = generate("""
            package test;

            import io.micronaut.camel.annotation.CamelConfig;

            @CamelConfig(configurations = CamelConfig.Kind.COMPONENT, include = "timer")
            final class Marker {
            }
            """);

        String configuration = sources.get("TimerComponentConfiguration.java");
        assertTrue(configuration.contains("@ConfigurationProperties(\"camel.component.timer\")"), configuration);
        assertTrue(configuration.contains("property = \"camel.component.timer\""), configuration);
        // the requirement annotation generated into micronaut-camel-core
        assertTrue(configuration.contains("@RequiresCamelTimer"), configuration);
        assertTrue(configuration.contains("public void setIncludeMetadata(Boolean includeMetadata)"), configuration);
        assertTrue(configuration.contains("public Boolean getEnabled()"), configuration);
        assertTrue(configuration.contains("defaultValue = \"true\""), configuration);
        // endpoint options are not component options
        assertFalse(configuration.contains("setPeriod"), configuration);
    }

    @Test
    void mapsOptionTypes() {
        Map<String, String> sources = generate("""
            package test;

            import io.micronaut.camel.annotation.CamelConfig;

            @CamelConfig(configurations = CamelConfig.Kind.COMPONENT, include = "kafka")
            final class Marker {
            }
            """);

        String configuration = sources.get("KafkaComponentConfiguration.java");
        assertTrue(configuration.contains("private String brokers;"), configuration);
        assertTrue(configuration.contains("private Integer maxPollRecords;"), configuration);
        assertTrue(configuration.contains("private Map<String, String> additionalProperties;"), configuration);
        // a bean of a Kafka type is referenced by name
        assertTrue(configuration.contains("private String kafkaClientFactory;"), configuration);
        assertTrue(configuration.contains("When not set, Camel uses the single bean of that type"), configuration);
    }

    @Test
    void generatesMainConfigurationGroups() {
        Map<String, String> sources = generate("""
            package test;

            import io.micronaut.camel.annotation.CamelConfig;

            @CamelConfig(main = {"camel.main", "camel.vault.*"})
            final class Marker {
            }
            """);

        String main = sources.get("CamelMainConfiguration.java");
        assertTrue(main.contains("@ConfigurationProperties(\"camel.main\")"), main);
        assertTrue(main.contains("public void setShutdownTimeout(Integer shutdownTimeout)"), main);
        // a group the metadata does not declare, kebab-cased for Micronaut
        String springConfig = sources.get("CamelVaultSpringConfigConfiguration.java");
        assertTrue(springConfig.contains("@ConfigurationProperties(\"camel.vault.spring-config\")"), springConfig);
        assertTrue(sources.containsKey("CamelVaultAwsConfiguration.java"));
        assertFalse(sources.containsKey("CamelRestConfiguration.java"));
    }

    @Test
    void groupsMainOptionsByPrefix() {
        MainModel model = new DefaultCamelCatalog(true).mainModel();
        Map<String, List<MainModel.MainOptionModel>> groups = CamelConfigVisitor.mainGroups(model);

        assertTrue(groups.containsKey("camel.main"));
        assertTrue(groups.containsKey("camel.vault.springConfig"));
        assertEquals(model.getOptions().size(), groups.values().stream().mapToInt(List::size).sum());
    }

    @Test
    void namesAreJavaIdentifiers() {
        assertEquals("Aws2S3", Names.typeName("aws2-s3"));
        assertEquals("CamelClusterJgroupsRaft", Names.typeName("camel.cluster.jgroups-raft"));
        assertEquals("camel.telemetry-dev", Names.kebabPrefix("camel.telemetryDev"));
        assertEquals("a &lt;b&gt; &#64;c $$d", Names.javadoc("a <b> @c $d"));
    }

    private static Map<String, String> generate(String source) {
        try (JavaParser parser = new JavaParser()) {
            Iterable<? extends JavaFileObject> generated = parser.generate("test.Marker", source);
            return StreamSupport.stream(generated.spliterator(), false)
                .filter(file -> file.getName().endsWith(".java"))
                .collect(Collectors.toMap(
                    file -> file.getName().substring(file.getName().lastIndexOf('/') + 1),
                    CamelConfigVisitorTest::read));
        }
    }

    private static String read(JavaFileObject file) {
        try {
            return file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
