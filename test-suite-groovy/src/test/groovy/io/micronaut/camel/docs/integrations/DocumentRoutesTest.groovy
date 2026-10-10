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

import io.micronaut.camel.objectstorage.ObjectStorageConstants
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.apache.camel.FluentProducerTemplate
import spock.lang.Specification

@MicronautTest
class DocumentRoutesTest extends Specification {

    @Inject
    FluentProducerTemplate producer

    void 'documents are stored and read'() {
        when:
        producer.to('direct:archive-document')
            .withHeader(ObjectStorageConstants.KEY, 'invoice-1.txt')
            .withBody('Invoice 1')
            .send()

        then:
        producer.to('direct:read-document')
            .withHeader(ObjectStorageConstants.KEY, 'invoice-1.txt')
            .request(String) == 'Invoice 1'
    }
}
