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
package io.micronaut.camel.objectstorage;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "ObjectStorageComponentTest")
@Property(name = "micronaut.object-storage.local.pictures.path", value = "build/tmp/object-storage-test/pictures")
class ObjectStorageComponentTest {

    @Inject
    FluentProducerTemplate producer;

    @Test
    void uploadsRetrievesListsAndDeletesObjects() throws Exception {
        Path directory = Path.of("build/tmp/object-storage-test/pictures");
        producer.to("direct:store").withHeader(Exchange.FILE_NAME, "cat.txt").withBody("meow").send();
        assertEquals("meow", Files.readString(directory.resolve("cat.txt")));

        Exchange uploaded = producer.to("object-storage:pictures?operation=put")
            .withHeader(ObjectStorageConstants.KEY, "dog.txt")
            .withHeader(Exchange.CONTENT_TYPE, "text/plain")
            .withBody("woof".getBytes(StandardCharsets.UTF_8))
            .send();
        assertNull(uploaded.getException());
        assertEquals("dog.txt", uploaded.getMessage().getHeader(ObjectStorageConstants.KEY));
        assertNotNull(uploaded.getMessage().getHeader(ObjectStorageConstants.E_TAG));

        byte[] dog = producer.to("object-storage:pictures?operation=get&key=dog.txt").request(byte[].class);
        assertArrayEquals("woof".getBytes(StandardCharsets.UTF_8), dog);

        assertNull(producer.to("object-storage:pictures?operation=retrieve&key=missing.txt").request());
        assertEquals(Boolean.TRUE, producer.to("object-storage:pictures?operation=exists&key=cat.txt").request(Boolean.class));
        assertTrue(producer.to("object-storage:pictures?operation=list").request(Set.class).containsAll(Set.of("cat.txt", "dog.txt")));

        producer.to("object-storage:pictures")
            .withHeader(ObjectStorageConstants.OPERATION, ObjectStorageOperation.COPY)
            .withHeader(ObjectStorageConstants.KEY, "dog.txt")
            .withHeader(ObjectStorageConstants.DESTINATION_KEY, "puppy.txt")
            .send();
        assertEquals("woof", Files.readString(directory.resolve("puppy.txt")));

        producer.to("object-storage:pictures?operation=delete").withHeader(ObjectStorageConstants.KEY, "cat.txt").send();
        assertFalse(producer.to("object-storage:pictures?operation=exists&key=cat.txt").request(Boolean.class));
        assertTrue(Files.exists(directory.resolve("dog.txt")));
    }

    @Test
    void theDefaultNameIsTheOnlyObjectStorage() {
        producer.to("object-storage:default?key=bird.txt").withBody("tweet").send();

        assertEquals("tweet", producer.to("object-storage:?operation=get&key=bird.txt").request(String.class));
    }

    @Test
    void anUploadNeedsAKey() {
        Exchange exchange = producer.to("object-storage:pictures").withBody("nameless").send();

        assertTrue(exchange.getException() instanceof IllegalArgumentException);
    }

    @Singleton
    @Requires(property = "spec.name", value = "ObjectStorageComponentTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:store").to("object-storage:pictures");
        }
    }
}
