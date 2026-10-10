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
package io.micronaut.camel.docs.jsonschema;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ValidationException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class OrderRoutesTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void validatesWithTheSchemaResource() throws Exception {
        assertValidates("direct:checked-orders");
    }

    @Test
    void validatesWithTheSchemaOfTheType() throws Exception {
        assertValidates("direct:typed-orders");
    }

    @Test
    void validatesTheInputType() throws Exception {
        assertValidates("direct:contract-orders");
    }

    private void assertValidates(String uri) throws Exception {
        MockEndpoint orders = camelContext.getEndpoint("mock:valid-orders", MockEndpoint.class);
        orders.reset();
        orders.expectedMessageCount(1);

        producerTemplate.sendBody(uri, "{\"id\":\"A-1\",\"quantity\":2}");

        orders.assertIsSatisfied();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
            () -> producerTemplate.sendBody(uri, "{\"id\":\"\",\"quantity\":0}"));
        assertInstanceOf(ValidationException.class, e.getCause());
        orders.assertIsSatisfied();
    }
}
