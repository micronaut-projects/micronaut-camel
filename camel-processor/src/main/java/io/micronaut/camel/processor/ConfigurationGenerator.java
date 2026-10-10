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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.MainModel;
import org.jspecify.annotations.Nullable;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@code @ConfigurationProperties} class that documents the options of a Camel
 * component, data format or language, or of a camel-main configuration group. The class is only
 * active when an option under its prefix is set.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ConfigurationGenerator {

    private static final String REQUIRES_PACKAGE = "io.micronaut.camel.requires";
    private static final ClassTypeDef NULLABLE = ClassTypeDef.of("org.jspecify.annotations.Nullable");

    private ConfigurationGenerator() {
    }

    static ClassDef extensionConfiguration(String packageName, CamelKind kind, ArtifactModel<?> model, VisitorContext context) {
        String name = model.getName();
        String prefix = kind.prefix(name);
        ClassDef.ClassDefBuilder builder = ClassDef.builder(packageName + "." + Names.typeName(name) + kind.typeSuffix() + "Configuration")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Configuration of the Camel " + Names.javadoc(model.getTitle()) + " " + kind.description()
                + " ({@code " + model.getGroupId() + ":" + model.getArtifactId() + "}). "
                + Names.javadoc(model.getDescription())
                + "\n\n<p>Camel applies these options when the " + kind.description() + " is created.</p>");
        String requirement = REQUIRES_PACKAGE + "." + RequirementGenerator.annotationName(kind, name);
        if (context.getClassElement(requirement).isPresent()) {
            builder.addAnnotation(ClassTypeDef.of(requirement));
        } else {
            builder.addAnnotations(RequirementGenerator.requirements(kind, name));
        }
        if (model.isDeprecated()) {
            builder.addAnnotation(Deprecated.class);
        }
        configurationProperties(builder, prefix);

        Set<String> names = new HashSet<>();
        for (BaseOptionModel option : kind.options(model)) {
            if (names.add(option.getName())) {
                addOption(builder, option.getName(), option);
            }
        }
        if (names.add("enabled")) {
            addProperty(builder, "enabled", TypeDef.of(Boolean.class),
                "Whether Camel applies the options under {@code " + prefix + "}.", "true", false);
        }
        return builder.build();
    }

    static ClassDef mainConfiguration(String packageName, String group, @Nullable String description, List<MainModel.MainOptionModel> options) {
        String prefix = Names.kebabPrefix(group);
        ClassDef.ClassDefBuilder builder = ClassDef.builder(packageName + "." + Names.typeName(group) + "Configuration")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("The {@code " + group + "} options of Camel. " + Names.javadoc(description)
                + "\n\n<p>Camel applies these options when it starts.</p>");
        configurationProperties(builder, prefix);
        Set<String> names = new HashSet<>();
        for (MainModel.MainOptionModel option : options) {
            String name = option.getName().substring(group.length() + 1);
            if (names.add(name)) {
                addOption(builder, name, option);
            }
        }
        return builder.build();
    }

    private static void configurationProperties(ClassDef.ClassDefBuilder builder, String prefix) {
        builder.addAnnotation(AnnotationDef.builder(Requires.class).addMember("property", prefix).build())
            .addAnnotation(AnnotationDef.builder(ConfigurationProperties.class).addMember("value", prefix).build());
    }

    private static void addOption(ClassDef.ClassDefBuilder builder, String name, BaseOptionModel option) {
        StringBuilder doc = new StringBuilder(Names.javadoc(description(option)));
        List<String> enums = option.getEnums();
        if (enums != null && !enums.isEmpty() && !"boolean".equals(option.getType())) {
            doc.append(" Possible values: ").append(Names.javadoc(String.join(", ", enums))).append('.');
        }
        String javaType = option.getJavaType();
        TypeDef type = type(option);
        if (TypeDef.STRING.equals(type) && "object".equals(option.getType()) && javaType != null && !javaType.startsWith("java.lang")) {
            doc.append(" The name of a bean of type {@code ").append(Names.javadoc(javaType))
                .append("}, as {@code #bean:name}, or {@code #class:} and a class name.");
            if (option.isAutowired()) {
                doc.append(" When not set, Camel uses the single bean of that type, if there is one.");
            }
        } else if ("duration".equals(option.getType())) {
            doc.append(" A duration such as {@code 5s} or {@code 500ms}, or a number of milliseconds.");
        }
        if (option.isSecret()) {
            doc.append(" This option is sensitive.");
        }
        String defaultValue = defaultValue(option);
        addProperty(builder, name, type, doc.toString(), defaultValue, option.isDeprecated());
    }

    private static void addProperty(ClassDef.ClassDefBuilder builder, String name, TypeDef type, String doc,
                                    @Nullable String defaultValue, boolean deprecated) {
        String fieldName = SourceVersion.isKeyword(name) || !SourceVersion.isIdentifier(name) ? "option" + Names.typeName(name) : name;
        String accessor = Names.typeName(NameUtils.capitalize(name));
        String fullDoc = defaultValue != null ? doc + " Default: {@code " + Names.javadoc(defaultValue) + "}." : doc;

        FieldDef field = FieldDef.builder(fieldName, type)
            .addModifiers(Modifier.PRIVATE)
            .addAnnotation(NULLABLE)
            .build();
        builder.addField(field);

        MethodDef.MethodDefBuilder getter = MethodDef.builder("get" + accessor)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(NULLABLE)
            .addJavadoc(fullDoc + "\n\n@return The value, or {@code null} when it is not set");
        MethodDef.MethodDefBuilder setter = MethodDef.builder("set" + accessor)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(fullDoc + "\n\n@param " + fieldName + " The value");
        if (defaultValue != null) {
            setter.addAnnotation(AnnotationDef.builder(Bindable.class).addMember("defaultValue", defaultValue).build());
        }
        if (deprecated) {
            getter.addAnnotation(Deprecated.class);
            setter.addAnnotation(Deprecated.class);
        }
        builder.addMethod(getter.returns(type).build((aThis, params) -> aThis.field(field).returning()));
        builder.addMethod(setter
            .addParameter(fieldName, type)
            .build((aThis, params) -> aThis.field(field).assign(params.get(0))));
    }

    private static String description(BaseOptionModel option) {
        String description = option.getDescription();
        if (description == null || description.isBlank()) {
            description = option.getDisplayName();
        }
        return description == null ? option.getName() : description;
    }

    private static @Nullable String defaultValue(BaseOptionModel option) {
        Object value = option.getDefaultValue();
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.isEmpty() || text.contains("${") || "array".equals(option.getType()) || "object".equals(option.getType()) && option.isMultiValue()) {
            return null;
        }
        return text;
    }

    /**
     * Maps the type of a Camel option to the type of the generated property. Options whose value is
     * a Camel or third-party type are documented as strings: Camel resolves them to beans.
     *
     * @param option The option
     * @return The property type
     */
    static TypeDef type(BaseOptionModel option) {
        String type = option.getType();
        String javaType = option.getJavaType() == null ? "" : option.getJavaType();
        if (option.isMultiValue() || javaType.startsWith("java.util.Map") || javaType.startsWith("java.util.Properties")) {
            return TypeDef.parameterized(Map.class, String.class, String.class);
        }
        if (type == null) {
            return TypeDef.STRING;
        }
        return switch (type) {
            case "boolean" -> TypeDef.of(Boolean.class);
            case "integer" -> switch (javaType) {
                case "long", "java.lang.Long" -> TypeDef.of(Long.class);
                default -> TypeDef.of(Integer.class);
            };
            case "number" -> switch (javaType) {
                case "float", "java.lang.Float" -> TypeDef.of(Float.class);
                default -> TypeDef.of(Double.class);
            };
            case "array" -> TypeDef.parameterized(List.class, String.class);
            case "object" -> javaType.startsWith("java.util.List") || javaType.startsWith("java.util.Set")
                || javaType.startsWith("java.util.Collection") ? TypeDef.parameterized(List.class, String.class) : TypeDef.STRING;
            default -> TypeDef.STRING;
        };
    }
}
