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
package io.micronaut.camel.docs.testing;

// tag::test[]
import io.micronaut.camel.test.EnableRouteCoverage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@EnableRouteCoverage // <1>
class GreetingCoverageTest {

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void greets() {
        assertEquals("Hello Fred", producerTemplate.requestBody("direct:greet", "Fred", String.class)); // <2>
    }
}
// end::test[]
