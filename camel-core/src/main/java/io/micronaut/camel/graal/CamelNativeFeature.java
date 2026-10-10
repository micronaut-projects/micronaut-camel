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
package io.micronaut.camel.graal;

import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.graalvm.nativeimage.hosted.RuntimeSerialization;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Registers what Camel needs at runtime in a native image, when the image is built:
 *
 * <ul>
 *     <li>the classes that Camel's service files under {@code META-INF/services/org/apache/camel} name:
 *     components, data formats, languages, configurers, URI factories, route loaders and the others
 *     Camel resolves by name and instantiates by reflection</li>
 *     <li>the type converter loaders that Camel's {@code TypeConverterLoader} files list</li>
 *     <li>the {@code @Produce} interfaces Camel creates proxies of, and the classes Camel invokes by
 *     reflection, which the Micronaut Camel annotation processor lists under
 *     {@code META-INF/micronaut-camel/native}</li>
 *     <li>the route builders, whose fields and methods Camel inspects, and the exception types that
 *     {@code onException} and {@code doCatch} refer to by name</li>
 *     <li>the getters that Camel reads by reflection to describe the services that components connect to,
 *     such as the URL and user of the data sources of the {@code sql} and {@code jdbc} components</li>
 *     <li>the list of the route resources of the image, which the image cannot always list at runtime</li>
 *     <li>when Camel's {@code DefaultExchangeHolder} is used, which the JDBC, LevelDB and other
 *     aggregation repositories write with Java serialization, the holder and the JDK types of the
 *     bodies and headers it holds, for serialization</li>
 * </ul>
 *
 * <p>Everything else is initialized at runtime, as on the JVM.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class CamelNativeFeature implements Feature {

    static final String SERVICES = "META-INF/services/org/apache/camel/";
    static final String NATIVE_METADATA = "META-INF/micronaut-camel/native/";
    /**
     * The list of the route and Kamelet resources of the image, which {@link NativePackageScanResourceResolver}
     * reads when the image has no file system of its resources to list, as with runtime class loading.
     */
    static final String RESOURCE_INDEX = "META-INF/micronaut-camel/native-resources";
    // the directories of route resources, whose resources reachability-metadata.json includes
    private static final List<String> RESOURCE_DIRECTORIES = List.of("camel/", "camel-rest/", "camel-template/", "kamelets/");
    private static final String TYPE_CONVERTER_LOADER = SERVICES + "TypeConverterLoader";
    private static final String UBER_TYPE_CONVERTER_LOADER = SERVICES + "UberTypeConverterLoader";
    private static final String EXCHANGE_HOLDER = "org.apache.camel.support.DefaultExchangeHolder";
    // the properties that Camel's service location helpers read with its bean introspection, by type
    private static final Map<String, List<String>> SERVICE_PROPERTIES = Map.of(
        "javax.sql.DataSource", List.of("url", "jdbcUrl", "URL", "dataSource", "user", "username", "userName")
    );
    private static final List<Class<?>> SERIALIZED_JDK_TYPES = List.of(
        String.class, Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class,
        Float.class, Double.class, Number.class, BigInteger.class, BigDecimal.class,
        byte[].class, char[].class, Object[].class, String[].class, UUID.class, Date.class,
        ArrayList.class, LinkedList.class, HashMap.class, LinkedHashMap.class,
        TreeMap.class, HashSet.class, LinkedHashSet.class, TreeSet.class
    );

    @Override
    public String getDescription() {
        return "Registers the Camel extensions and the classes Camel uses by reflection";
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Set<String> reflective = new LinkedHashSet<>();
        Set<String> proxies = new LinkedHashSet<>();
        Set<String> resources = new TreeSet<>();
        for (Path entry : access.getApplicationClassPath()) {
            try {
                if (Files.isDirectory(entry)) {
                    scanDirectory(entry, reflective, proxies, resources);
                } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                    scanJar(entry, reflective, proxies, resources);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to scan " + entry + " for Camel services", e);
            }
        }
        RuntimeResourceAccess.addResource(CamelNativeFeature.class.getModule(), RESOURCE_INDEX,
            String.join("\n", resources).getBytes(StandardCharsets.UTF_8));
        for (String className : reflective) {
            Class<?> type = access.findClassByName(className);
            if (type != null) {
                registerAll(type);
            }
        }
        for (String className : proxies) {
            Class<?> type = access.findClassByName(className);
            if (type != null && type.isInterface()) {
                RuntimeProxyCreation.register(type);
            }
        }
        Class<?> routesBuilder = access.findClassByName("org.apache.camel.RoutesBuilder");
        if (routesBuilder != null) {
            access.registerSubtypeReachabilityHandler((duringAnalysis, type) -> registerAll(type), routesBuilder);
        }
        access.registerSubtypeReachabilityHandler((duringAnalysis, type) -> registerType(type), Throwable.class);
        SERVICE_PROPERTIES.forEach((typeName, properties) -> {
            Class<?> serviceType = access.findClassByName(typeName);
            if (serviceType != null) {
                access.registerSubtypeReachabilityHandler((duringAnalysis, type) -> registerGetters(type, properties), serviceType);
            }
        });
        Class<?> exchangeHolder = access.findClassByName(EXCHANGE_HOLDER);
        if (exchangeHolder != null) {
            access.registerReachabilityHandler(duringAnalysis -> registerSerialization(exchangeHolder), exchangeHolder);
        }
    }

    private static void registerGetters(Class<?> type, List<String> properties) {
        try {
            for (Method method : type.getMethods()) {
                String property = propertyName(method.getName());
                if (property != null && method.getParameterCount() == 0 && properties.contains(property)) {
                    RuntimeReflection.register(method);
                }
            }
        } catch (LinkageError e) {
            // a class of an optional dependency that is not on the classpath
        }
    }

    /**
     * @param methodName The name of a method
     * @return The name of the property the method gets, if it is a getter
     */
    static @Nullable String propertyName(String methodName) {
        String name = methodName.startsWith("get") ? methodName.substring(3) : methodName.startsWith("is") ? methodName.substring(2) : null;
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.length() > 1 && Character.isUpperCase(name.charAt(1)) ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static void registerSerialization(Class<?> exchangeHolder) {
        RuntimeSerialization.register(exchangeHolder);
        RuntimeSerialization.register(SERIALIZED_JDK_TYPES.toArray(Class<?>[]::new));
    }

    private static void scanDirectory(Path root, Set<String> reflective, Set<String> proxies, Set<String> resources) throws IOException {
        for (String prefix : RESOURCE_DIRECTORIES) {
            Path directory = root.resolve(prefix);
            if (Files.isDirectory(directory)) {
                try (Stream<Path> files = Files.walk(directory)) {
                    files.filter(Files::isRegularFile).forEach(file -> resources.add(root.relativize(file).toString().replace('\\', '/')));
                }
            }
        }
        for (String prefix : List.of(SERVICES, NATIVE_METADATA)) {
            Path directory = root.resolve(prefix);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    try (InputStream in = Files.newInputStream(file)) {
                        read(name, in, reflective, proxies);
                    }
                }
            }
        }
    }

    private static void scanJar(Path jar, Set<String> reflective, Set<String> proxies, Set<String> resources) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && RESOURCE_DIRECTORIES.stream().anyMatch(name::startsWith)) {
                    resources.add(name);
                }
                if (!entry.isDirectory() && (name.startsWith(SERVICES) || name.startsWith(NATIVE_METADATA))) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        read(name, in, reflective, proxies);
                    }
                }
            }
        }
    }

    /**
     * Reads the class names of a Camel service file or of a native metadata file.
     *
     * @param name       The path of the file
     * @param in         Its content
     * @param reflective The classes to register for reflection
     * @param proxies    The interfaces to register for proxies
     * @throws IOException if the file cannot be read
     */
    static void read(String name, InputStream in, Set<String> reflective, Set<String> proxies) throws IOException {
        List<String> lines = lines(in);
        boolean loaderList = name.equals(TYPE_CONVERTER_LOADER) || name.equals(UBER_TYPE_CONVERTER_LOADER);
        for (String line : lines) {
            if (loaderList) {
                reflective.add(line);
            } else if (name.startsWith(NATIVE_METADATA)) {
                value(line, "reflect=").ifPresent(reflective::add);
                value(line, "proxy=").ifPresent(proxies::add);
            } else {
                value(line, "class=").ifPresent(reflective::add);
            }
        }
    }

    private static java.util.Optional<String> value(String line, String key) {
        return line.startsWith(key) ? java.util.Optional.of(line.substring(key.length()).trim()) : java.util.Optional.empty();
    }

    private static List<String> lines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        }
        return lines;
    }

    private static void registerType(Class<?> type) {
        RuntimeReflection.register(type);
    }

    private static void registerAll(@Nullable Class<?> type) {
        if (type == null) {
            return;
        }
        try {
            RuntimeReflection.register(type);
            RuntimeReflection.register(type.getDeclaredConstructors());
            RuntimeReflection.register(type.getDeclaredMethods());
            RuntimeReflection.register(type.getMethods());
            RuntimeReflection.register(type.getDeclaredFields());
        } catch (LinkageError e) {
            // a class of an optional dependency that is not on the classpath
        }
    }
}
