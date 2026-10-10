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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.AnnotationObjectDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import org.apache.camel.tooling.model.ArtifactModel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import javax.lang.model.element.Modifier;

/**
 * Generates the requirement annotation of a Camel component, data format or language, such as
 * {@code @RequiresCamelKafka}. It bundles the conditions under which a bean that integrates with
 * the extension applies: the extension is on the classpath, and it is not disabled with
 * {@code camel.component.<name>.enabled=false}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class RequirementGenerator {

    private RequirementGenerator() {
    }

    /**
     * @param kind The kind of extension
     * @param name The name of the extension
     * @return The simple name of its requirement annotation, such as {@code RequiresCamelKafka}
     */
    static String annotationName(CamelKind kind, String name) {
        String suffix = kind == CamelKind.COMPONENT ? "" : kind.typeSuffix();
        return "RequiresCamel" + Names.typeName(name) + suffix;
    }

    /**
     * @param kind The kind of extension
     * @param name The name of the extension
     * @return The conditions of the requirement annotation
     */
    static List<AnnotationDef> requirements(CamelKind kind, String name) {
        return List.of(
            AnnotationDef.builder(Requires.class)
                .addMember("resources", kind.serviceResource(name))
                .build(),
            AnnotationDef.builder(Requires.class)
                .addMember("property", kind.prefix(name) + ".enabled")
                .addMember("notEquals", "false")
                .build()
        );
    }

    static AnnotationObjectDef requirement(String packageName, CamelKind kind, ArtifactModel<?> model) {
        String name = model.getName();
        ClassTypeDef elementType = ClassTypeDef.of(ElementType.class);
        return AnnotationObjectDef.builder(packageName + "." + annotationName(kind, name))
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Requires the Camel " + Names.javadoc(model.getTitle()) + " " + kind.description()
                + " ({@code " + model.getGroupId() + ":" + model.getArtifactId() + "}) on the classpath, and not disabled with {@code "
                + kind.prefix(name) + ".enabled=false}.")
            .addAnnotation(Documented.class)
            .addAnnotation(AnnotationDef.builder(Retention.class)
                .addMember("value", ClassTypeDef.of(RetentionPolicy.class).getStaticField("RUNTIME", ClassTypeDef.of(RetentionPolicy.class)))
                .build())
            .addAnnotation(AnnotationDef.builder(Target.class)
                .addMember("value", List.of(
                    elementType.getStaticField("TYPE", elementType),
                    elementType.getStaticField("METHOD", elementType),
                    elementType.getStaticField("PACKAGE", elementType)))
                .build())
            .addAnnotations(requirements(kind, name))
            .build();
    }
}
