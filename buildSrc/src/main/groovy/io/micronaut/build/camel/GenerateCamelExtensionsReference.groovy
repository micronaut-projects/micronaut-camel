package io.micronaut.build.camel

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes the reference of the Camel extension modules, which the
 * io.micronaut.build.internal.camel-extensions settings plugin creates from the Camel catalog, as an
 * Asciidoc table that the guide includes.
 */
@CacheableTask
abstract class GenerateCamelExtensionsReference extends DefaultTask {

    private static final Map<String, String> KIND_TITLES = [component: 'component', dataformat: 'data format', language: 'language']

    @Input
    abstract MapProperty<String, Map<String, Object>> getArtifacts()

    @Input
    abstract Property<String> getCamelVersion()

    @Input
    abstract Property<String> getGroupId()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void generate() {
        String[] version = camelVersion.get().split('\\.')
        String docs = "https://camel.apache.org/components/${version[0]}.${version[1]}.x"
        StringBuilder adoc = new StringBuilder()
        adoc << '[cols="2,2,4",options="header"]\n|===\n|Module |Camel artifact |Extensions\n\n'
        artifacts.get().values().each { Map<String, Object> artifact ->
            List<String> links = (artifact.extensions as List<Map<String, Object>>).collect { Map<String, Object> e ->
                String kind = e.kind as String
                String page = kind == 'component' ? "${e.name}-component" : kind == 'dataformat' ? "${e.name}-dataformat" : "${e.name}-language"
                String folder = kind == 'component' ? '' : kind == 'dataformat' ? 'dataformats/' : 'languages/'
                String title = (e.title as String).replace('[', '(').replace(']', ')')
                "${docs}/${folder}${page}.html[${title}] ${KIND_TITLES[kind]}" + (e.deprecated ? ' (deprecated)' : '')
            }
            adoc << "|`${groupId.get()}:${artifact.module}`\n|`${artifact.artifactId}`\n|${links.join(' +\n')}\n\n"
        }
        adoc << '|===\n'
        File file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.text = adoc.toString()
    }
}
