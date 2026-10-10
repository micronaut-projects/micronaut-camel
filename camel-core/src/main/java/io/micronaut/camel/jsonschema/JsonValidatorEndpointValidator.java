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
package io.micronaut.camel.jsonschema;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.service.ServiceHelper;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Camel validator of a {@code @JsonSchema} type that sends the body, as JSON, to a {@code json-validator}
 * endpoint of the schema generated for the type at build time, {@code json-validator:jsonschema:<type>}. Used
 * when the application has {@code camel-json-validator} instead of the Micronaut JSON Schema validator; it
 * throws the {@code JsonValidationException} of the component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class JsonValidatorEndpointValidator extends Validator {

    private final Class<?> type;
    private final String uri;
    private final Lock lock = new ReentrantLock();
    private volatile @Nullable Producer producer;

    /**
     * @param type The {@code @JsonSchema} type
     */
    JsonValidatorEndpointValidator(Class<?> type) {
        this.type = type;
        this.uri = "json-validator:" + JsonSchemaResourceResolver.SCHEME + ":" + type.getName();
    }

    @Override
    public void validate(Message message, DataType dataType) throws ValidationException {
        Exchange exchange = message.getExchange();
        if (message.getBody() == null) {
            throw new ValidationException(exchange, "No body to validate against the JSON schema of " + type.getName());
        }
        Exchange copy = exchange.copy();
        try {
            // a type instance becomes JSON with the JSON type converter of Micronaut Camel
            copy.getIn().setBody(message.getMandatoryBody(String.class));
            producer().process(copy);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("JSON schema validation of " + type.getName() + " failed: " + e.getMessage(), exchange, e);
        }
        if (copy.getException() instanceof ValidationException e) {
            throw e;
        } else if (copy.getException() != null) {
            throw new ValidationException("JSON schema validation of " + type.getName() + " failed: " + copy.getException().getMessage(),
                exchange, copy.getException());
        }
    }

    private Producer producer() throws Exception {
        Producer result = producer;
        if (result == null) {
            lock.lock();
            try {
                result = producer;
                if (result == null) {
                    CamelContext camelContext = Objects.requireNonNull(getCamelContext(), "camelContext");
                    result = camelContext.getEndpoint(uri).createProducer();
                    ServiceHelper.startService(result);
                    producer = result;
                }
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
        producer = null;
    }
}
