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

import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.apache.camel.FluentProducerTemplate
import spock.lang.Specification

@MicronautTest
class InventoryRoutesTest extends Specification implements TestPropertyProvider {

    @Inject
    FluentProducerTemplate producer

    @Override
    Map<String, String> getProperties() {
        // the inventory service is the InventoryController of this application
        String port = String.valueOf(SocketUtils.findAvailableTcpPort())
        ['micronaut.server.port'               : port,
         'micronaut.http.services.inventory.url': "http://localhost:${port}".toString()]
    }

    void 'stock is checked with the inventory service'() {
        expect:
        producer.to('direct:check-stock').withHeader('item', 'book').request(Integer) == 12
    }
}
