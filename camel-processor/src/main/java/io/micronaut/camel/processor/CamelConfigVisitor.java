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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ObjectDef;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.MainModel;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates code from the Camel catalog for classes annotated with
 * {@code io.micronaut.camel.annotation.CamelConfig}:
 *
 * <ul>
 *     <li>a requirement annotation per component, data format or language, such as
 *     {@code @RequiresCamelKafka}</li>
 *     <li>{@code @ConfigurationProperties} per component, data format or language, such as
 *     {@code KafkaComponentConfiguration} for {@code camel.component.kafka}</li>
 *     <li>{@code @ConfigurationProperties} per camel-main configuration group, such as
 *     {@code CamelMainConfiguration} for {@code camel.main}</li>
 * </ul>
 *
 * <p>The generated configuration documents the Camel options, for IDEs and the configuration
 * reference. Camel itself still applies the options at runtime, with camel-main.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class CamelConfigVisitor implements TypeElementVisitor<Object, Object> {

    static final String CAMEL_CONFIG = "io.micronaut.camel.annotation.CamelConfig";

    private @Nullable CamelCatalog catalog;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(CAMEL_CONFIG);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        AnnotationValue<Annotation> config = element.getAnnotation(CAMEL_CONFIG);
        if (config == null) {
            return;
        }
        SourceGenerator generator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
        if (generator == null) {
            return;
        }
        String packageName = element.getPackageName();
        String[] include = config.stringValues("include");
        Predicate<String> included = (include.length == 0 ? (Predicate<String>) name -> true : matching(include))
            .and(matching(config.stringValues("exclude")).negate());
        Set<String> artifacts = Set.of(config.stringValues("artifacts"));
        CamelCatalog camelCatalog = catalog();

        List<ObjectDef> generated = new ArrayList<>();
        for (String kindName : config.stringValues("requirements")) {
            CamelKind kind = CamelKind.valueOf(kindName);
            for (String name : kind.names(camelCatalog)) {
                if (!included.test(name)) {
                    continue;
                }
                ArtifactModel<?> model = kind.model(camelCatalog, name);
                if (fromArtifacts(model, artifacts)) {
                    generated.add(RequirementGenerator.requirement(packageName, kind, model));
                }
            }
        }
        for (String kindName : config.stringValues("configurations")) {
            CamelKind kind = CamelKind.valueOf(kindName);
            for (String name : kind.names(camelCatalog)) {
                if (!included.test(name)) {
                    continue;
                }
                ArtifactModel<?> model = kind.model(camelCatalog, name);
                if (fromArtifacts(model, artifacts)) {
                    generated.add(ConfigurationGenerator.extensionConfiguration(packageName, kind, model, context));
                }
            }
        }
        String[] mainGroups = config.stringValues("main");
        if (mainGroups.length > 0) {
            Predicate<String> mainIncluded = matching(mainGroups);
            for (Map.Entry<String, List<MainModel.MainOptionModel>> group : mainGroups(camelCatalog.mainModel()).entrySet()) {
                if (mainIncluded.test(group.getKey())) {
                    generated.add(ConfigurationGenerator.mainConfiguration(packageName, group.getKey(),
                        groupDescription(camelCatalog.mainModel(), group.getKey()), group.getValue()));
                }
            }
        }
        for (ObjectDef objectDef : generated) {
            try {
                generator.write(objectDef, context, element);
            } catch (RuntimeException e) {
                throw new ProcessingException(element, "Failed to generate " + objectDef.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    private static boolean fromArtifacts(ArtifactModel<?> model, Set<String> artifacts) {
        return artifacts.isEmpty() || artifacts.contains(model.getArtifactId());
    }

    /**
     * Groups the camel-main options by their prefix, the option name up to its last dot. Not every
     * prefix is declared as a group in the metadata.
     *
     * @param mainModel The camel-main metadata
     * @return The options by group
     */
    static Map<String, List<MainModel.MainOptionModel>> mainGroups(MainModel mainModel) {
        Map<String, List<MainModel.MainOptionModel>> groups = new LinkedHashMap<>();
        for (MainModel.MainOptionModel option : mainModel.getOptions()) {
            String name = option.getName();
            String group = name.substring(0, name.lastIndexOf('.'));
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(option);
        }
        return groups;
    }

    private static @Nullable String groupDescription(MainModel mainModel, String group) {
        return mainModel.getGroups().stream()
            .filter(g -> g.getName().equals(group))
            .map(MainModel.MainGroupModel::getDescription)
            .findFirst()
            .orElse(null);
    }

    private CamelCatalog catalog() {
        if (catalog == null) {
            DefaultCamelCatalog defaultCatalog = new DefaultCamelCatalog(true);
            catalog = defaultCatalog;
        }
        return catalog;
    }

    private static Predicate<String> matching(String[] patterns) {
        if (patterns.length == 0) {
            return name -> false;
        }
        Pattern pattern = Pattern.compile(Arrays.stream(patterns)
            .map(p -> Arrays.stream(p.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*")))
            .collect(Collectors.joining("|")));
        return name -> pattern.matcher(name).matches();
    }
}
