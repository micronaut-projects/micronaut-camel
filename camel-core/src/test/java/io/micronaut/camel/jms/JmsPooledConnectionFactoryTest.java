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
package io.micronaut.camel.jms;

import io.micronaut.context.annotation.Property;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@MicronautTest
@Property(name = "micronaut.jms.activemq.artemis.enabled", value = "true")
@Property(name = "micronaut.jms.activemq.artemis.connection-string", value = "tcp://localhost:1")
@Property(name = "micronaut.jms.activemq.artemis.username", value = "artemis")
@Property(name = "micronaut.jms.activemq.artemis.password", value = "artemis")
@Property(name = "micronaut.camel.jms.connection-factory", value = "activeMqArtemisConnectionFactory")
@Property(name = "micronaut.camel.jms.pooled", value = "true")
class JmsPooledConnectionFactoryTest {

    @Inject
    CamelContext camelContext;

    @Test
    void thePoolOfMicronautJmsCanBeSelected() {
        assertInstanceOf(JMSConnectionPool.class,
            camelContext.getComponent("jms", JmsComponent.class).getConfiguration().getConnectionFactory());
    }
}
