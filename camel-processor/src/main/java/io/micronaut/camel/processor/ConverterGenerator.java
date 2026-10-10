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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import jakarta.inject.Singleton;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generates a type converter loader bean for a class annotated with Camel's {@code @Converter}, in
 * place of the loader that camel-package-maven-plugin generates for Maven builds. It registers each
 * method annotated with {@code @Converter} that takes the value, and optionally the exchange.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ConverterGenerator {

    private static final ClassTypeDef LOADER = ClassTypeDef.of("io.micronaut.camel.convert.GeneratedTypeConverterLoader");
    private static final ClassTypeDef REGISTRY = ClassTypeDef.of("org.apache.camel.spi.TypeConverterRegistry");
    private static final ClassTypeDef EXCHANGE = ClassTypeDef.of("org.apache.camel.Exchange");
    private static final String EXCHANGE_NAME = "org.apache.camel.Exchange";

    private ConverterGenerator() {
    }

    static Optional<ClassDef> converterLoader(ClassElement element, VisitorContext context) {
        List<MethodElement> methods = new ArrayList<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS
            .onlyDeclared()
            .annotated(metadata -> metadata.hasDeclaredAnnotation(CamelAnnotationsVisitor.CONVERTER)))) {
            if (method.booleanValue(CamelAnnotationsVisitor.CONVERTER, "fallback").orElse(false)) {
                context.warn("Fallback converters are not supported: register a TypeConverter with the TypeConverterRegistry", method);
                continue;
            }
            ParameterElement[] parameters = method.getParameters();
            boolean valid = !method.getReturnType().isVoid() && method.isPublic()
                && (parameters.length == 1 || parameters.length == 2 && parameters[1].getType().isAssignable(EXCHANGE_NAME));
            if (!valid) {
                context.fail("A @Converter method must be public, return a value, and take the value to convert and optionally the Exchange", method);
                continue;
            }
            methods.add(method);
        }
        if (methods.isEmpty()) {
            return Optional.empty();
        }
        ClassTypeDef owner = ClassTypeDef.of(element);
        boolean instanceMethods = methods.stream().anyMatch(m -> !m.isStatic());
        FieldDef converters = FieldDef.builder("converters", owner)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer(owner.instantiate())
            .build();

        String packageName = element.getPackageName();
        String simpleName = InjectionFactoryGenerator.factoryName(element).replace("CamelInjections", "TypeConverterLoader");
        ClassDef.ClassDefBuilder builder = ClassDef.builder((packageName.isEmpty() ? "" : packageName + ".") + simpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(LOADER)
            .addAnnotation(Singleton.class)
            .addAnnotation(Internal.class)
            .addJavadoc("Loads the type converters of {@link " + element.getName().replace('$', '.') + "} into Camel.");
        if (instanceMethods) {
            builder.addField(converters);
        }

        builder.addMethod(MethodDef.builder("load")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("registry", REGISTRY)
            .build((aThis, params) -> StatementDef.multi(methods.stream()
                .map(method -> (StatementDef) aThis.invoke("addConverter", TypeDef.VOID, params.get(0),
                    ExpressionDef.constant(typeOf(method.getParameters()[0].getType())),
                    ExpressionDef.constant(typeOf(method.getReturnType()))))
                .toList())));

        builder.addMethod(MethodDef.builder("doConvert")
            .addModifiers(Modifier.PROTECTED)
            .overrides()
            .addParameter("type", TypeDef.parameterized(ClassTypeDef.of(Class.class), TypeDef.wildcard()))
            .addParameter("exchange", EXCHANGE.makeNullable())
            .addParameter("value", TypeDef.OBJECT)
            .addThrows(TypeDef.of(Exception.class))
            .returns(TypeDef.OBJECT.makeNullable())
            .build((aThis, params) -> {
                List<StatementDef> statements = new ArrayList<>();
                for (MethodElement method : methods) {
                    TypeDef from = wrapper(typeOf(method.getParameters()[0].getType()));
                    List<ExpressionDef> arguments = new ArrayList<>();
                    arguments.add(params.get(2).cast(from));
                    if (method.getParameters().length == 2) {
                        arguments.add(params.get(1));
                    }
                    ExpressionDef call = method.isStatic()
                        ? owner.invokeStatic(method.getName(), typeOf(method.getReturnType()), arguments)
                        : aThis.field(converters).invoke(method.getName(), typeOf(method.getReturnType()), arguments);
                    statements.add(params.get(0).equalsReferentially(ExpressionDef.constant(typeOf(method.getReturnType())))
                        .doIf(isInstance(params.get(2), from).doIf(call.returning())));
                }
                statements.add(ExpressionDef.nullValue().returning());
                return StatementDef.multi(statements);
            }));
        return Optional.of(builder.build());
    }

    private static TypeDef typeOf(ClassElement type) {
        if (type.isArray()) {
            return typeOf(type.fromArray()).array(type.getArrayDimensions());
        }
        if (type.isPrimitive()) {
            return TypeDef.primitive(type.getName());
        }
        return ClassTypeDef.of(type);
    }

    private static ExpressionDef.ConditionExpressionDef isInstance(ExpressionDef value, TypeDef type) {
        if (type instanceof ClassTypeDef classType) {
            return value.instanceOf(classType);
        }
        // an array type: Camel only calls the converter for the registered source type
        return value.invoke("getClass", TypeDef.of(Class.class)).equalsReferentially(ExpressionDef.constant(type));
    }

    private static TypeDef wrapper(TypeDef type) {
        if (type instanceof TypeDef.Primitive primitive) {
            return primitive.wrapperType();
        }
        return type;
    }
}
