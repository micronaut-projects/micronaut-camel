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
package io.micronaut.camel.aot;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.micronaut.aot.core.AOTCodeGenerator;
import io.micronaut.aot.core.AOTContext;
import io.micronaut.aot.core.AOTModule;
import io.micronaut.aot.core.Runtime;
import io.micronaut.camel.optimizations.CamelServiceIndex;
import io.micronaut.core.optim.StaticOptimizations;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Indexes Camel's service files under {@code META-INF/services/org/apache/camel} when the application is
 * built: the class names of the components, data formats, languages, configurers and the other extensions
 * that Camel resolves by name, and the lists of type converter loaders. The generated code loads the index
 * as a {@link CamelServiceIndex} static optimization, which Micronaut Camel uses instead of looking up the
 * service files on the classpath, and falls back to the classpath for the files that are not in the index.
 * The lists of type converter loaders are also read by name, as the jars without directory entries have no
 * {@code META-INF/services/org/apache/camel} directory for the class loader to find.
 *
 * <p>Enable it with the {@code camel.service.index.enabled} option of Micronaut AOT.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@AOTModule(
    id = CamelServiceIndexGenerator.ID,
    description = "Indexes Camel's service files (components, data formats, languages, configurers and type converter loaders) when the application is built",
    enabledOn = {Runtime.JIT, Runtime.NATIVE}
)
public final class CamelServiceIndexGenerator implements AOTCodeGenerator {

    /**
     * The ID of the optimization.
     */
    public static final String ID = "camel.service.index";

    /**
     * The simple name of the generated loader class.
     */
    static final String LOADER = "CamelServiceIndexLoader";

    private static final String ROOT = CamelServiceIndex.ROOT;
    private static final List<String> LIST_FILES = List.of("TypeConverterLoader", "UberTypeConverterLoader", "FallbackTypeConverter");
    // the entries per generated method, which keeps the methods under the size limit of the JVM
    private static final int CHUNK_SIZE = 250;

    @Override
    public void generate(AOTContext context) {
        Index index = scan(classLoader());
        context.registerGeneratedSourceFile(context.javaFile(loader(index)));
        context.registerServiceImplementation(StaticOptimizations.Loader.class, context.getPackageName() + "." + LOADER);
        context.addDiagnostics(ID, "Indexed " + index.classes.size() + " Camel service files and " + index.lists.size() + " lists");
    }

    private ClassLoader classLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : CamelServiceIndexGenerator.class.getClassLoader();
    }

    /**
     * Reads Camel's service files on a class path.
     *
     * @param classLoader The class loader of the application
     * @return The index
     */
    static Index scan(ClassLoader classLoader) {
        Index index = new Index();
        try {
            Enumeration<URL> roots = classLoader.getResources(ROOT.substring(0, ROOT.length() - 1));
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                URLConnection connection = root.openConnection();
                if (connection instanceof JarURLConnection jar) {
                    connection.setUseCaches(false);
                    try (JarFile file = jar.getJarFile()) {
                        for (JarEntry entry : file.stream().toList()) {
                            if (!entry.isDirectory() && entry.getName().startsWith(ROOT)) {
                                try (InputStream in = file.getInputStream(entry)) {
                                    index.read(entry.getName().substring(ROOT.length()), in);
                                }
                            }
                        }
                    }
                } else if ("file".equals(root.getProtocol())) {
                    Path directory = Path.of(root.toURI());
                    try (Stream<Path> files = Files.walk(directory)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            try (InputStream in = Files.newInputStream(file)) {
                                index.read(directory.relativize(file).toString().replace('\\', '/'), in);
                            }
                        }
                    }
                }
            }
            // ClassLoader.getResources finds no directory in the jars that have no directory entries, so the lists,
            // which Camel reads from every jar, are also read by name: the index must have them all
            for (String list : LIST_FILES) {
                Enumeration<URL> files = classLoader.getResources(ROOT + list);
                while (files.hasMoreElements()) {
                    URLConnection connection = files.nextElement().openConnection();
                    connection.setUseCaches(false);
                    try (InputStream in = connection.getInputStream()) {
                        index.read(list, in);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot index the Camel service files", e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot index the Camel service files", e);
        }
        return index;
    }

    private static TypeSpec loader(Index index) {
        ClassName indexType = ClassName.get(CamelServiceIndex.class);
        TypeName classesType = ParameterizedTypeName.get(Map.class, String.class, String.class);
        TypeName listsType = ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class),
            ParameterizedTypeName.get(List.class, String.class));
        TypeSpec.Builder type = TypeSpec.classBuilder(LOADER)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(StaticOptimizations.Loader.class), indexType))
            .addJavadoc("The index of Camel's service files, computed by the $L optimization of Micronaut AOT.\n", ID);
        CodeBlock.Builder load = CodeBlock.builder()
            .addStatement("$T classes = new $T<>($L)", classesType, HashMap.class, Math.max(16, index.classes.size() * 2))
            .addStatement("$T lists = new $T<>()", listsType, HashMap.class);
        List<Map.Entry<String, String>> classes = new ArrayList<>(index.classes.entrySet());
        for (int chunk = 0; chunk * CHUNK_SIZE < classes.size(); chunk++) {
            String name = "classes" + chunk;
            MethodSpec.Builder method = MethodSpec.methodBuilder(name)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(classesType, "classes");
            for (Map.Entry<String, String> entry : classes.subList(chunk * CHUNK_SIZE, Math.min(classes.size(), (chunk + 1) * CHUNK_SIZE))) {
                method.addStatement("classes.put($S, $S)", entry.getKey(), entry.getValue());
            }
            type.addMethod(method.build());
            load.addStatement("$L(classes)", name);
        }
        index.lists.forEach((path, names) -> {
            CodeBlock values = names.stream().map(name -> CodeBlock.of("$S", name)).collect(CodeBlock.joining(", "));
            load.addStatement("lists.put($S, $T.of($L))", path, List.class, values);
        });
        load.addStatement("return new $T(classes, lists)", indexType);
        type.addMethod(MethodSpec.methodBuilder("load")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(indexType)
            .addCode(load.build())
            .build());
        return type.build();
    }

    /**
     * The service files read from the class path.
     */
    static final class Index {
        final Map<String, String> classes = new TreeMap<>();
        final Map<String, List<String>> lists = new LinkedHashMap<>();
        private final Map<String, Set<String>> listEntries = new LinkedHashMap<>();
        private final Set<String> read = new LinkedHashSet<>();

        void read(String path, InputStream in) throws IOException {
            if (LIST_FILES.contains(path)) {
                Set<String> entries = listEntries.computeIfAbsent(path, p -> new LinkedHashSet<>());
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        entries.add(trimmed);
                    }
                }
                lists.put(path, List.copyOf(entries));
            } else if (read.add(path)) {
                // the first file on the class path wins, as with ClassLoader.getResource
                Properties properties = new Properties();
                properties.load(in);
                String className = properties.getProperty("class");
                if (className != null && !className.isBlank()) {
                    classes.put(path, className.trim());
                }
            }
        }
    }
}
