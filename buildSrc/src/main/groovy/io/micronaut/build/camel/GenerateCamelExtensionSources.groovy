package io.micronaut.build.camel

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Writes the sources of a Camel extension module: a class annotated with {@code @CamelConfig}, from
 * which the processor generates the configuration of the extensions of one Camel artifact, and the
 * {@code package-info.java} of its package.
 */
@CacheableTask
@CompileStatic
abstract class GenerateCamelExtensionSources extends DefaultTask {

    @Input
    abstract Property<String> getArtifactId()

    @Input
    abstract Property<String> getPackageName()

    @Input
    abstract Property<String> getTitle()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        String packageName = this.packageName.get()
        File dir = new File(outputDirectory.get().asFile, packageName.replace('.', '/'))
        outputDirectory.get().asFile.deleteDir()
        dir.mkdirs()
        String header = """/*
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
"""
        String title = this.title.get().replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
        new File(dir, 'package-info.java').text = header + """/**
 * The configuration of Camel ${title}, generated from the Camel catalog. Camel applies these options; the
 * classes document them.
 *
 * @since 1.0.0
 */
@NullMarked
package ${packageName};

import org.jspecify.annotations.NullMarked;
"""
        new File(dir, 'CamelExtensionConfigurations.java').text = header + """package ${packageName};

import io.micronaut.camel.annotation.CamelConfig;
import io.micronaut.core.annotation.Internal;

/**
 * Generates {@code @ConfigurationProperties} in this package for the components, data formats and
 * languages of {@code ${artifactId.get()}}.
 *
 * @since 1.0.0
 */
@Internal
@CamelConfig(
    configurations = {CamelConfig.Kind.COMPONENT, CamelConfig.Kind.DATAFORMAT, CamelConfig.Kind.LANGUAGE},
    artifacts = "${artifactId.get()}"
)
final class CamelExtensionConfigurations {

    private CamelExtensionConfigurations() {
    }
}
"""
    }
}
