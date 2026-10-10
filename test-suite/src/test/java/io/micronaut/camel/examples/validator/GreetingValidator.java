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
package io.micronaut.camel.examples.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named("greetingValidator")
@Requires(env = "example-validator")
public class GreetingValidator extends Validator {

    private static final Logger LOG = LoggerFactory.getLogger(GreetingValidator.class);

    private final String greeting;

    public GreetingValidator(@Value("${greeting}") String greeting) {
        this.greeting = greeting;
    }

    @Override
    public void validate(Message message, DataType type) throws ValidationException {
        Object body = message.getBody();
        LOG.info("Validating : [{}]", body);
        if (body instanceof String && body.equals(greeting)) {
            LOG.info("OK");
        } else {
            throw new ValidationException(message.getExchange(), "Wrong content");
        }
    }

}
