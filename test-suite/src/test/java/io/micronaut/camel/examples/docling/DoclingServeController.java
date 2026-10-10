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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the {@code /v1/convert/source} operation of docling-serve: it answers with a fixed Markdown
 * document, or a fixed Docling document when the request asks for JSON, and records the requests.
 */
@Controller("/v1/convert")
@Requires(env = "example-docling")
class DoclingServeController {

    static final String MARKDOWN = "# Sample document\n\nConverted by the docling-serve stub.\n";

    final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();

    @Post("/source")
    Map<String, Object> convertSource(@Body Map<String, Object> request) {
        requests.add(request);
        Map<?, ?> source = (Map<?, ?>) ((List<?>) request.get("sources")).get(0);
        String filename = (String) source.get("filename");
        String name = filename.substring(0, filename.lastIndexOf('.'));
        List<?> formats = (List<?>) ((Map<?, ?>) request.get("options")).get("to_formats");
        Map<String, Object> document = formats.contains("json")
            ? Map.of("filename", filename, "json_content", Map.of(
                "schema_name", "DoclingDocument",
                "version", "1.7.0",
                "name", name,
                "origin", Map.of("mimetype", "application/pdf", "binary_hash", 1, "filename", filename)))
            : Map.of("filename", filename, "md_content", MARKDOWN);
        return Map.of(
            "document", document,
            "status", "success",
            "errors", List.of(),
            "processing_time", 0.1,
            "timings", Map.of());
    }
}
