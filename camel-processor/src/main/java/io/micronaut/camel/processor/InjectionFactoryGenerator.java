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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.util.Map;

/**
 * Generates the factory that serves the {@code @EndpointInject} and {@code @Produce} injection points
 * of a class: a prototype bean per injected type, qualified with the {@code @CamelInject} owner of
 * the class, which asks Camel for the value of the injection point.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class InjectionFactoryGenerator {

    private static final ClassTypeDef CAMEL_INJECTIONS = ClassTypeDef.of("io.micronaut.camel.inject.CamelInjections");
    private static final TypeDef INJECTION_POINT = TypeDef.parameterized(ClassTypeDef.of("io.micronaut.inject.InjectionPoint"), TypeDef.wildcard());
    private static final ClassTypeDef CAMEL_CONTEXT = ClassTypeDef.of("org.apache.camel.CamelContext");

    private InjectionFactoryGenerator() {
    }

    /**
     * @param owner The class that declares the injection points
     * @return The simple name of the generated factory
     */
    static String factoryName(ClassElement owner) {
        String name = owner.getName().substring(owner.getPackageName().isEmpty() ? 0 : owner.getPackageName().length() + 1);
        return name.replace('$', '_').replace('.', '_') + "CamelInjections";
    }

    static ClassDef factory(ClassElement owner, Map<String, ClassElement> types) {
        String packageName = owner.getPackageName();
        ClassDef.ClassDefBuilder builder = ClassDef.builder((packageName.isEmpty() ? "" : packageName + ".") + factoryName(owner))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Factory.class)
            .addAnnotation(Internal.class)
            .addJavadoc("Creates the values that Camel's {@code @EndpointInject} and {@code @Produce} inject into {@link "
                + owner.getName().replace('$', '.') + "}.");
        int index = 0;
        for (ClassElement type : types.values()) {
            ClassTypeDef typeDef = ClassTypeDef.of(type);
            builder.addMethod(MethodDef.builder("inject" + index++)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Prototype.class)
                // only for the qualified injection points: never a candidate for an unqualified one
                .addAnnotation(Secondary.class)
                .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(CamelAnnotationsVisitor.CAMEL_INJECT))
                    .addMember("owner", owner.getName())
                    .build())
                .addParameter("injectionPoint", INJECTION_POINT)
                .addParameter("camelContext", CAMEL_CONTEXT)
                .returns(typeDef)
                .build((aThis, params) -> CAMEL_INJECTIONS
                    .invokeStatic("inject", TypeDef.OBJECT, ExpressionDef.constant(typeDef), params.get(0), params.get(1))
                    .cast(typeDef)
                    .returning()));
        }
        return builder.build();
    }
}
