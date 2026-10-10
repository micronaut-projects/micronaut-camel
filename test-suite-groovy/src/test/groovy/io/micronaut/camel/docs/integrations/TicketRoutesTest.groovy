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
package io.micronaut.camel.docs.integrations

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.validation.ValidatorFactory
import org.apache.camel.CamelContext
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.component.bean.validator.BeanValidationException
import org.apache.camel.component.bean.validator.BeanValidatorComponent
import spock.lang.Specification

@MicronautTest
class TicketRoutesTest extends Specification {

    @Inject
    FluentProducerTemplate producer

    @Inject
    CamelContext camelContext

    @Inject
    ValidatorFactory validatorFactory

    void 'the component uses Micronaut Validation'() {
        when:
        def component = camelContext.getComponent('bean-validator', BeanValidatorComponent)

        then:
        component.validatorFactory.is(validatorFactory)
        !validatorFactory.class.name.contains('hibernate')
    }

    void 'valid tickets are opened'() {
        expect:
        producer.to('direct:open-ticket').withBody(new Ticket('Broken build', 'fred@example.com')).request(String) == 'opened Broken build'
    }

    void 'invalid tickets are rejected'() {
        when:
        def exchange = producer.to('direct:open-ticket').withBody(new Ticket(' ', 'not an email')).send()

        then:
        exchange.exception instanceof BeanValidationException
        (exchange.exception as BeanValidationException).constraintViolations*.propertyPath*.toString() as Set == ['title', 'reporter'] as Set
    }
}
