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
package io.micronaut.camel.examples.azurestorageblob;

import com.azure.storage.blob.BlobServiceClient;
import io.micronaut.camel.objectstorage.ObjectStorageConstants;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Ports the {@code azure/camel-example-spring-boot-azure-storage-blob} example of camel-spring-boot-examples,
 * and its {@code BlobSmokeTest}, {@code BlobSnapshotTest} and {@code BlobTagsTest} tests.
 * <p>
 * The {@code BlobServiceClient} is not built by a configuration class: Micronaut Object Storage for Azure
 * creates it from {@code micronaut.object-storage.azure.blobs.endpoint} and the shared key credential of
 * Micronaut Azure ({@code azure.credential.storage-shared-key.*}), and Camel autowires it into the component,
 * so the routes no longer set {@code serviceClient=#serviceClient}. The tests run Azurite with Testcontainers,
 * as the example's tests do, in one test class. As the client is also an object storage, a test uses the
 * {@code object-storage} component with the same container. {@code BlobVersionRouteBuilder}, a demo that the
 * example does not test, is not ported: Azurite does not support blob versioning.
 */
@MicronautTest(environments = "example-azure-storage-blob")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobStorageTest implements TestPropertyProvider {

    static final String ACCOUNT_NAME = "devstoreaccount1";
    static final String ACCOUNT_KEY =
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    static final String CONTAINER_NAME = "camel-blobs";
    static final String BLOB_NAME = "test-blob.txt";

    static final GenericContainer<?> AZURITE = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:3.35.0")
        .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
        .withExposedPorts(10000)
        .waitingFor(Wait.forListeningPort());

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate template;

    @Inject
    FluentProducerTemplate producer;

    @Inject
    BlobServiceClient serviceClient;

    @Override
    public Map<String, String> getProperties() {
        AZURITE.start();
        return Map.of(
            "azure.storage.account-name", ACCOUNT_NAME,
            "azure.storage.container-name", CONTAINER_NAME,
            "micronaut.object-storage.azure.blobs.endpoint",
            "http://" + AZURITE.getHost() + ":" + AZURITE.getMappedPort(10000) + "/" + ACCOUNT_NAME,
            "micronaut.object-storage.azure.blobs.container", CONTAINER_NAME,
            "azure.credential.storage-shared-key.account-name", ACCOUNT_NAME,
            "azure.credential.storage-shared-key.account-key", ACCOUNT_KEY);
    }

    @AfterAll
    void stopAzurite() {
        AZURITE.stop();
    }

    @BeforeEach
    void createContainer() {
        serviceClient.createBlobContainerIfNotExists(CONTAINER_NAME);
    }

    @Test
    void serviceClientInRegistry() {
        assertSame(serviceClient, camelContext.getRegistry().findSingleByType(BlobServiceClient.class));
    }

    @Test
    void uploadAndDownloadBlob() {
        String expected = "hello azurite";
        template.send("direct:uploadBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "smoke.txt");
            exchange.getIn().setBody(new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)));
        });

        Exchange result = template.send("direct:getBlob", exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "smoke.txt"));

        assertNull(result.getException(), "Download should succeed: " + result.getException());
        assertEquals(expected, result.getMessage().getBody(String.class));
    }

    @Test
    void readBlobViaSnapshotId() {
        uploadTestBlob("original snapshot content");
        Exchange snapshotResult = template.send("direct:createSnapshot", exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME));
        String snapshotId = snapshotResult.getMessage().getHeader(BlobConstants.BLOB_SNAPSHOT_ID, String.class);
        assertNotNull(snapshotId, "Snapshot ID should be returned in header");

        uploadTestBlob("modified after snapshot");

        Exchange snapshotRead = template.send("direct:getBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME);
            exchange.getIn().setHeader(BlobConstants.BLOB_SNAPSHOT_ID, snapshotId);
        });
        assertEquals("original snapshot content", snapshotRead.getMessage().getBody(String.class));
        Exchange liveRead = template.send("direct:getBlob", exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME));
        assertEquals("modified after snapshot", liveRead.getMessage().getBody(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void setAndGetBlobTags() {
        uploadTestBlob("hello blob tags");
        Map<String, String> tags = Map.of("status", "quarantine", "category", "document", "priority", "high");

        Exchange setResult = template.send("direct:setBlobTags", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME);
            exchange.getIn().setHeader(BlobConstants.BLOB_TAGS, tags);
        });
        assertNull(setResult.getException(), "setBlobTags should succeed: " + setResult.getException());

        Exchange result = template.send("direct:getBlobTags", exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME));
        assertNull(result.getException(), "getBlobTags should succeed: " + result.getException());
        assertEquals(tags, result.getMessage().getBody(Map.class));
    }

    @Test
    void findBlobsByTags() {
        uploadTestBlob("hello blob tags");
        template.send("direct:setBlobTags", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME);
            exchange.getIn().setHeader(BlobConstants.BLOB_TAGS, Map.of("Environment", "Production"));
        });

        Exchange result = template.send("direct:findBlobsByTags",
            exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_TAG_FILTER, "\"Environment\" = 'Production'"));

        assertNull(result.getException(), "findBlobsByTags should succeed: " + result.getException());
        assertNotNull(result.getMessage().getBody(), "Should return results");
    }

    @Test
    void theObjectStorageComponentUsesTheSameContainer() {
        producer.to("object-storage:blobs").withHeader(ObjectStorageConstants.KEY, "portable.txt").withBody("portable").send();

        Exchange result = template.send("direct:getBlob", exchange -> exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "portable.txt"));
        assertEquals("portable", result.getMessage().getBody(String.class));
    }

    private void uploadTestBlob(String content) {
        template.send("direct:uploadBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, BLOB_NAME);
            exchange.getIn().setBody(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        });
    }
}
