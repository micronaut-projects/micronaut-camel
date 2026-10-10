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
package io.micronaut.camel.event;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

/**
 * Receives the Micronaut application events of the type of its endpoint, on the thread that
 * publishes them.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class MicronautEventConsumer extends DefaultConsumer {

    MicronautEventConsumer(MicronautEventEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public MicronautEventEndpoint getEndpoint() {
        return (MicronautEventEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getEndpoint().getComponent().register(this);
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().getComponent().unregister(this);
        super.doStop();
    }

    void onEvent(Object event) {
        if (!getEndpoint().getEventType().isInstance(event)) {
            return;
        }
        Exchange exchange = createExchange(false);
        exchange.getMessage().setBody(event);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing the application event " + event, exchange, exchange.getException());
        }
        releaseExchange(exchange, false);
    }
}
