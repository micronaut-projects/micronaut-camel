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
package io.micronaut.camel.examples.widgetgadget;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.qpid.jms.JmsConnectionFactory;

@Factory
@Requires(env = "example-widget-gadget")
public class AmqpConfig {

    // configuration of the AMQP connection factory
    @Bean
    @Singleton
    public JmsConnectionFactory amqpConnectionFactory(@Value("${amqp.remote-uri}") String remoteUri,
                                                      @Value("${amqp.service.username}") String userName,
                                                      @Value("${amqp.service.password}") String pass) {
        JmsConnectionFactory jmsConnectionFactory = new JmsConnectionFactory();
        jmsConnectionFactory.setRemoteURI(remoteUri);
        jmsConnectionFactory.setUsername(userName);
        jmsConnectionFactory.setPassword(pass);
        return jmsConnectionFactory;
    }
}
