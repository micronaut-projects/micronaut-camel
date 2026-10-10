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
package io.micronaut.camel.transaction;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.model.errorhandler.TransactionErrorHandlerDefinition;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;

/**
 * The error handler of transacted routes that run in Micronaut transactions.
 *
 * <p>Transacted routes use it automatically. Configure it on a route, or as the error handler of a
 * {@code RouteBuilder}, to change its redelivery policy or the logging level of rollbacks:</p>
 *
 * <pre>{@code
 * errorHandler(new MicronautTransactionErrorHandlerDefinition()
 *     .rollbackLoggingLevel(LoggingLevel.INFO)
 *     .maximumRedeliveries(2));
 * }</pre>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautTransactionErrorHandlerDefinition extends TransactionErrorHandlerDefinition {

    static {
        ErrorHandlerReifier.registerReifier(MicronautTransactionErrorHandlerDefinition.class,
            (route, definition) -> new MicronautTransactionErrorHandlerReifier(route, (MicronautTransactionErrorHandlerDefinition) definition));
    }

    /**
     * Creates a definition with the default settings.
     */
    public MicronautTransactionErrorHandlerDefinition() {
    }

    /**
     * @param source The definition to copy
     */
    public MicronautTransactionErrorHandlerDefinition(MicronautTransactionErrorHandlerDefinition source) {
        super(source);
    }

    @Override
    public MicronautTransactionErrorHandlerDefinition copyDefinition() {
        return new MicronautTransactionErrorHandlerDefinition(this);
    }

    @Override
    public ErrorHandlerFactory cloneBuilder() {
        MicronautTransactionErrorHandlerDefinition answer = new MicronautTransactionErrorHandlerDefinition();
        cloneBuilder(answer);
        return answer;
    }
}
