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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ObjectDef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns Camel's annotations on Micronaut beans into Micronaut metadata at compile time, so Camel's
 * reflective bean post-processor is not needed.
 *
 * <ul>
 *     <li>{@code @EndpointInject} and {@code @Produce} become injection points qualified with
 *     {@code @CamelInject}, served by a factory generated for the declaring class</li>
 *     <li>{@code @BeanInject} becomes an injection point qualified with {@code @Named}</li>
 *     <li>{@code @PropertyInject} becomes {@code @Value}</li>
 *     <li>{@code @Consume} methods become executable methods, subscribed when Camel starts</li>
 *     <li>{@code @BindToRegistry} on a class makes it a named singleton</li>
 *     <li>{@code @Converter} classes get a generated type converter loader bean</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class CamelAnnotationsVisitor implements TypeElementVisitor<Object, Object> {

    static final String ENDPOINT_INJECT = "org.apache.camel.EndpointInject";
    static final String PRODUCE = "org.apache.camel.Produce";
    static final String BEAN_INJECT = "org.apache.camel.BeanInject";
    static final String PROPERTY_INJECT = "org.apache.camel.PropertyInject";
    static final String CONSUME = "org.apache.camel.Consume";
    static final String BIND_TO_REGISTRY = "org.apache.camel.BindToRegistry";
    static final String CONVERTER = "org.apache.camel.Converter";

    static final String CAMEL_INJECT = "io.micronaut.camel.inject.CamelInject";
    static final String CAMEL_ENDPOINT = "io.micronaut.camel.inject.CamelEndpoint";
    static final String CAMEL_CONSUME = "io.micronaut.camel.inject.CamelConsume";
    static final String CAMEL_POST_PROCESS = "io.micronaut.camel.inject.CamelPostProcess";
    static final String CAMEL_CONVERTERS = "io.micronaut.camel.convert.CamelConverters";

    private static final String INJECT = "jakarta.inject.Inject";
    private static final String NAMED = "jakarta.inject.Named";
    private static final String SINGLETON = "jakarta.inject.Singleton";
    private static final String VALUE = "io.micronaut.context.annotation.Value";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(ENDPOINT_INJECT, PRODUCE, BEAN_INJECT, PROPERTY_INJECT, CONSUME, BIND_TO_REGISTRY, CONVERTER);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        boolean groovy = context.getLanguage() == VisitorContext.Language.GROOVY;
        if (element.hasDeclaredAnnotation(BIND_TO_REGISTRY)) {
            String name = element.stringValue(BIND_TO_REGISTRY).orElse("");
            if (!element.hasDeclaredStereotype("jakarta.inject.Scope") && !element.hasDeclaredStereotype("io.micronaut.context.annotation.Bean")) {
                element.annotate(SINGLETON);
            }
            if (!name.isEmpty()) {
                element.annotate(NAMED, builder -> builder.value(name));
            }
        }
        if (groovy) {
            // Groovy compilations cannot add generated sources: Camel handles the annotations at runtime
            if (element.hasDeclaredAnnotation(CONVERTER)) {
                element.annotate(SINGLETON);
                element.annotate(CAMEL_CONVERTERS);
            } else if (hasCamelMembers(element)) {
                element.annotate(CAMEL_POST_PROCESS);
            }
            return;
        }
        if (element.hasDeclaredAnnotation(CONVERTER)) {
            ConverterGenerator.converterLoader(element, context).ifPresent(loader -> write(loader, element, context));
        }
        // the factory is written while the class is visited: KSP elements are not valid once the round ends
        Map<String, ClassElement> types = injectedTypes(element);
        if (!types.isEmpty()) {
            write(InjectionFactoryGenerator.factory(element, types), element, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (context.getLanguage() == VisitorContext.Language.GROOVY) {
            return;
        }
        if (isEndpointInjection(element)) {
            element.annotate(INJECT);
            qualify(element, element, element.getOwningType(), context);
        } else if (element.hasDeclaredAnnotation(BEAN_INJECT)) {
            element.annotate(INJECT);
            beanInject(element, element);
        } else if (element.hasDeclaredAnnotation(PROPERTY_INJECT)) {
            propertyInject(element, element);
        } else if (element.hasDeclaredAnnotation(BIND_TO_REGISTRY)) {
            context.warn("@BindToRegistry on a field is not supported: declare the bean with a @Factory method", element);
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (context.getLanguage() == VisitorContext.Language.GROOVY || isAccessorOfAnnotatedField(element)) {
            // a Groovy or Kotlin property: its field is the injection point
            return;
        }
        if (element.hasDeclaredAnnotation(CONSUME)) {
            element.annotate(CAMEL_CONSUME);
            return;
        }
        if (element.hasDeclaredAnnotation(BIND_TO_REGISTRY)) {
            context.warn("@BindToRegistry on a method is not supported: annotate the method with @Singleton in a @Factory", element);
            return;
        }
        ParameterElement[] parameters = element.getParameters();
        boolean endpoint = isEndpointInjection(element);
        boolean bean = element.hasDeclaredAnnotation(BEAN_INJECT);
        boolean property = element.hasDeclaredAnnotation(PROPERTY_INJECT);
        if ((endpoint || bean || property) && parameters.length == 1) {
            // a setter: Micronaut injects its parameter
            element.annotate(INJECT);
            if (endpoint) {
                qualify(parameters[0], element, element.getOwningType(), context);
            } else if (bean) {
                beanInject(parameters[0], element);
            } else {
                propertyInject(parameters[0], element);
            }
        }
        visitParameters(parameters, element.getOwningType(), context);
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (context.getLanguage() == VisitorContext.Language.GROOVY) {
            return;
        }
        visitParameters(element.getParameters(), element.getOwningType(), context);
    }

    private static boolean hasCamelMembers(ClassElement element) {
        Set<String> annotations = Set.of(ENDPOINT_INJECT, PRODUCE, BEAN_INJECT, PROPERTY_INJECT, CONSUME);
        return !element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared()
                .annotated(metadata -> annotations.stream().anyMatch(metadata::hasDeclaredAnnotation))).isEmpty()
            || !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()
                .annotated(metadata -> annotations.stream().anyMatch(metadata::hasDeclaredAnnotation))).isEmpty();
    }

    /**
     * @param element A class
     * @return The types of its {@code @EndpointInject} and {@code @Produce} injection points, by name
     */
    private static Map<String, ClassElement> injectedTypes(ClassElement element) {
        Map<String, ClassElement> types = new LinkedHashMap<>();
        for (FieldElement field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
            if (isEndpointInjection(field)) {
                types.putIfAbsent(field.getType().getName(), field.getType());
            }
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            ParameterElement[] parameters = method.getParameters();
            if (isEndpointInjection(method) && parameters.length == 1 && !isAccessorOfAnnotatedField(method)) {
                types.putIfAbsent(parameters[0].getType().getName(), parameters[0].getType());
            }
            addParameterTypes(parameters, types);
        }
        for (ConstructorElement constructor : element.getEnclosedElements(ElementQuery.of(ConstructorElement.class))) {
            addParameterTypes(constructor.getParameters(), types);
        }
        return types;
    }

    private static void addParameterTypes(ParameterElement[] parameters, Map<String, ClassElement> types) {
        for (ParameterElement parameter : parameters) {
            if (isEndpointInjection(parameter)) {
                types.putIfAbsent(parameter.getType().getName(), parameter.getType());
            }
        }
    }

    /**
     * @param method A method
     * @return Whether it is the setter or getter of a property whose field carries a Camel annotation
     */
    private static boolean isAccessorOfAnnotatedField(MethodElement method) {
        String name = method.getName();
        String property;
        if (name.startsWith("set") && name.length() > 3) {
            property = NameUtils.decapitalize(name.substring(3));
        } else if (name.startsWith("get") && name.length() > 3) {
            property = NameUtils.decapitalize(name.substring(3));
        } else {
            return false;
        }
        return method.getOwningType().getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared().named(property)).stream()
            .anyMatch(field -> isEndpointInjection(field) || field.hasDeclaredAnnotation(BEAN_INJECT)
                || field.hasDeclaredAnnotation(PROPERTY_INJECT));
    }

    private void visitParameters(ParameterElement[] parameters, ClassElement owningType, VisitorContext context) {
        for (ParameterElement parameter : parameters) {
            if (isEndpointInjection(parameter)) {
                qualify(parameter, parameter, owningType, context);
            } else if (parameter.hasDeclaredAnnotation(BEAN_INJECT)) {
                beanInject(parameter, parameter);
            } else if (parameter.hasDeclaredAnnotation(PROPERTY_INJECT)) {
                propertyInject(parameter, parameter);
            }
        }
    }

    private static boolean isEndpointInjection(Element element) {
        return element.hasDeclaredAnnotation(ENDPOINT_INJECT) || element.hasDeclaredAnnotation(PRODUCE);
    }

    /**
     * Qualifies an injection point with {@code @CamelInject}, and copies the endpoint of the Camel
     * annotation declared on {@code source} to {@code @CamelEndpoint}.
     */
    private void qualify(TypedElement target, Element source, ClassElement owner, VisitorContext context) {
        boolean produce = source.hasDeclaredAnnotation(PRODUCE);
        String annotation = produce ? PRODUCE : ENDPOINT_INJECT;
        String uri = source.stringValue(annotation).orElse("");
        if (uri.isEmpty()) {
            uri = source.stringValue(annotation, "uri").orElse("");
        }
        if (uri.isEmpty()) {
            context.fail("Set the endpoint URI of @" + (produce ? "Produce" : "EndpointInject")
                + ": Micronaut does not support resolving it from a property of the bean", source);
            return;
        }
        boolean binding = !produce || source.booleanValue(PRODUCE, "binding").orElse(true);
        String endpoint = uri;
        target.annotate(CAMEL_INJECT, builder -> builder.member("owner", owner.getName()));
        target.annotate(CAMEL_ENDPOINT, builder -> builder.member("uri", endpoint).member("binding", binding));
    }

    private static void beanInject(Element target, Element source) {
        String name = source.stringValue(BEAN_INJECT).orElse("");
        if (!name.isEmpty()) {
            target.annotate(NAMED, builder -> builder.value(name));
        }
    }

    private static void propertyInject(Element target, Element source) {
        String key = source.stringValue(PROPERTY_INJECT).orElse("");
        String defaultValue = source.stringValue(PROPERTY_INJECT, "defaultValue").orElse("");
        String placeholder = "${" + key + (defaultValue.isEmpty() ? "" : ":" + defaultValue) + "}";
        target.annotate(VALUE, builder -> builder.value(placeholder));
    }

    private static void write(ObjectDef objectDef, ClassElement originatingElement, VisitorContext context) {
        SourceGenerator generator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
        if (generator == null) {
            context.fail("No source generator for " + context.getLanguage() + ": add micronaut-sourcegen to the annotation processor path", originatingElement);
            return;
        }
        try {
            generator.write(objectDef, context, originatingElement);
        } catch (RuntimeException e) {
            throw new ProcessingException(originatingElement, "Failed to generate " + objectDef.getName() + ": " + e.getMessage(), e);
        }
    }

}
