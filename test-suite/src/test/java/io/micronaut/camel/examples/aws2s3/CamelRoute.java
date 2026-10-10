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

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

/**
 * Consumes the objects of the bucket with the {@code S3Client} of Micronaut AWS.
 */
@Singleton
@Requires(env = "example-aws2-s3")
public class CamelRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("aws2-s3://{{bucketName}}")
            .log("Received body: ${body}");
    }
}
