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
package io.micronaut.camel.examples.docling;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

@Singleton
@Requires(env = "example-docling")
public class DoclingServeRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("file:{{documents.directory}}?delete=true&include=.*\\.(pdf|docx|pptx|html|md)$")
            .routeId("document-to-markdown-converter")
            .log("Processing document: ${header.CamelFileName}")
            .to("docling:convert-to-markdown?operation=CONVERT_TO_MARKDOWN&useDoclingServe=true&doclingServeUrl={{docling.serve.url}}&contentInBody=true")
            .log("Document converted to Markdown: ${header.CamelFileName}")
            .setHeader("CamelFileName", simple("${file:name.noext}.md"))
            .to("file:{{output.directory}}")
            .log("Saved converted document to: {{output.directory}}/${header.CamelFileName}");

        from("file:{{documents.directory}}/extract?delete=true&include=.*\\.(pdf|docx|pptx)$")
            .routeId("document-metadata-extractor")
            .log("Extracting metadata from: ${header.CamelFileName}")
            .to("docling:extract-structured-data?operation=EXTRACT_STRUCTURED_DATA&useDoclingServe=true&doclingServeUrl={{docling.serve.url}}&contentInBody=true")
            .marshal().json(JsonLibrary.Jackson)
            .log("Metadata extracted from: ${header.CamelFileName}")
            .setHeader("CamelFileName", simple("${file:name.noext}.json"))
            .to("file:{{output.directory}}/metadata")
            .log("Saved metadata to: {{output.directory}}/metadata/${header.CamelFileName}");
    }
}
