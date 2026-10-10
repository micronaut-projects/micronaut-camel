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
package io.micronaut.camel.examples.aws2s3;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Component;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code aws2-s3} example of camel-spring-boot-examples.
 * <p>
 * Instead of the {@code accessKey}, {@code secretKey} and {@code region} options of the component, Camel
 * autowires the {@code S3Client} bean of Micronaut AWS ({@code micronaut-aws-sdk-v2}), configured with
 * {@code aws.*}. The test runs S3 in LocalStack with Testcontainers and points the client at it with
 * {@code aws.services.s3.endpoint-override}. As LocalStack starts empty, the component creates the bucket
 * ({@code autoCreateBucket}). The example has no test: this one checks that the route consumes, and
 * deletes, an object put in the bucket.
 */
@MicronautTest(environments = "example-aws2-s3")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Aws2S3Test implements TestPropertyProvider {

    // a generic container: the LocalStack module mounts the Docker socket, for Lambda, which S3 does not need
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>("localstack/localstack:4.14.0")
        .withEnv("SERVICES", "s3")
        .withExposedPorts(4566)
        .waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));

    @Inject
    CamelContext camelContext;

    @Inject
    S3Client s3Client;

    @Override
    public Map<String, String> getProperties() {
        LOCALSTACK.start();
        return Map.of(
            "aws.services.s3.endpoint-override", "http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566),
            "aws.s3.path-style-access-enabled", "true",
            "aws.region", "us-east-1",
            "aws.access-key-id", "test",
            "aws.secret-key", "test");
    }

    @AfterAll
    void stopLocalStack() {
        LOCALSTACK.stop();
    }

    @Test
    void theComponentUsesTheMicronautClient() {
        assertSame(s3Client, camelContext.getComponent("aws2-s3", AWS2S3Component.class).getConfiguration().getAmazonS3Client());
    }

    @Test
    void consumesTheObjectsOfTheBucket() {
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .filter(exchange -> "hello.txt".equals(exchange.getMessage().getHeader(AWS2S3Constants.KEY)))
            .whenDone(1)
            .create();

        s3Client.putObject(request -> request.bucket("camel-kafka-connector").key("hello.txt"), RequestBody.fromString("Hello S3"));

        assertTrue(notify.matches(20, TimeUnit.SECONDS));
        // the consumer deletes the objects it read
        assertTrue(s3Client.listObjectsV2(request -> request.bucket("camel-kafka-connector")).contents().isEmpty());
    }
}
