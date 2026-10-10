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
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.scan.DefaultPackageScanResourceResolver;
import org.apache.camel.util.AntPathMatcher;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Map;

/**
 * Resolves classpath patterns, such as the {@code classpath:camel/*} pattern of the YAML routes, in
 * a native image. Camel's resolver only lists {@code jar:} and {@code file:} URLs, while a native image
 * serves its resources from the {@code resource:} file system.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public final class NativePackageScanResourceResolver extends DefaultPackageScanResourceResolver {

    private static final URI RESOURCES = URI.create("resource:/");
    private static final String CLASSPATH = "classpath:";

    private @Nullable FileSystem resourceFileSystem;

    /**
     * @return Whether the code runs in a native image
     */
    public static boolean inNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    @Override
    public Collection<Resource> findResources(String location) throws Exception {
        Collection<Resource> resources = super.findResources(location);
        if (!resources.isEmpty() || !location.startsWith(CLASSPATH)) {
            return resources;
        }
        String pattern = location.substring(CLASSPATH.length());
        String root = AntPathMatcher.INSTANCE.determineRootDir(pattern);
        String subPattern = pattern.substring(root.length());
        Path start = fileSystem().getPath("/" + root);
        if (!Files.exists(start)) {
            return resources;
        }
        ResourceLoader loader = PluginHelper.getResourceLoader(getCamelContext());
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String relative = start.relativize(file).toString();
                if (AntPathMatcher.INSTANCE.match(subPattern, relative)) {
                    resources.add(loader.resolveResource(CLASSPATH + root + relative));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        return resources;
    }

    @Override
    protected void doStop() throws Exception {
        if (resourceFileSystem != null) {
            resourceFileSystem.close();
            resourceFileSystem = null;
        }
        super.doStop();
    }

    private FileSystem fileSystem() throws IOException {
        if (resourceFileSystem == null) {
            resourceFileSystem = FileSystems.newFileSystem(RESOURCES, Map.of());
        }
        return resourceFileSystem;
    }
}
