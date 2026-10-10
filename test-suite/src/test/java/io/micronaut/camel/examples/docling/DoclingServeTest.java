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

import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code docling} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The example converts documents with a docling-serve instance. Its image is several gigabytes, with the models
 * of Docling, and a conversion takes seconds to minutes, so the test does not run it: docling-serve is the
 * {@link DoclingServeController} of the same application, which answers the {@code /v1/convert/source} requests of
 * the docling-serve client of camel-docling with fixed documents. The test runs the server on a known free port to
 * point {@code docling.serve.url} at it, and sets the {@code documents.directory} and {@code output.directory}
 * directories to temporary directories. It checks that each route sends the document to docling-serve, with the
 * format it asks for, and writes the Markdown and the metadata of the response to the output directory.
 * {@code camel.main.run-controller} was dropped: Micronaut owns the application lifecycle.
 */
@MicronautTest(environments = "example-docling")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DoclingServeTest implements TestPropertyProvider {

    private static final byte[] PDF = "%PDF-1.4 sample".getBytes(StandardCharsets.US_ASCII);

    private final Path documents = createTempDirectory("documents");
    private final Path output = createTempDirectory("output");

    @Inject
    CamelContext camelContext;

    @Inject
    DoclingServeController doclingServe;

    @Inject
    JsonMapper jsonMapper;

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
            "micronaut.server.port", String.valueOf(SocketUtils.findAvailableTcpPort()),
            "documents.directory", documents.toString(),
            "output.directory", output.toString());
    }

    @Test
    void convertsDocumentsToMarkdown() throws Exception {
        NotifyBuilder converted = new NotifyBuilder(camelContext).fromRoute("document-to-markdown-converter").whenCompleted(1).create();

        Files.write(documents.resolve("sample-document.pdf"), PDF);

        assertTrue(converted.matches(20, TimeUnit.SECONDS));
        assertEquals(DoclingServeController.MARKDOWN, Files.readString(output.resolve("sample-document.md")));
        assertFalse(Files.exists(documents.resolve("sample-document.pdf")));
        assertSent("sample-document.pdf", "md");
    }

    @Test
    void extractsStructuredData() throws Exception {
        NotifyBuilder extracted = new NotifyBuilder(camelContext).fromRoute("document-metadata-extractor").whenCompleted(1).create();

        Files.createDirectories(documents.resolve("extract"));
        Files.write(documents.resolve("extract").resolve("report.pdf"), PDF);

        assertTrue(extracted.matches(20, TimeUnit.SECONDS));
        Map<?, ?> metadata = jsonMapper.readValue(Files.readString(output.resolve("metadata").resolve("report.json")), Map.class);
        assertEquals("report", metadata.get("name"));
        assertEquals("DoclingDocument", metadata.get("schema_name"));
        assertSent("report.pdf", "json");
    }

    private void assertSent(String filename, String format) {
        Map<String, Object> request = doclingServe.requests.stream()
            .filter(r -> filename.equals(((Map<?, ?>) ((List<?>) r.get("sources")).get(0)).get("filename")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No request for " + filename + ": " + doclingServe.requests));
        Map<?, ?> source = (Map<?, ?>) ((List<?>) request.get("sources")).get(0);
        assertEquals(Base64.getEncoder().encodeToString(PDF), source.get("base64_string"));
        assertTrue(((List<?>) ((Map<?, ?>) request.get("options")).get("to_formats")).contains(format), request.toString());
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory("camel-docling-" + prefix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
