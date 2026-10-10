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

import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.ErrorHandlerHelper;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.spi.TransactedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A Camel {@link TransactedPolicy} that runs the rest of a route in a Micronaut transaction, so that
 * {@code transacted()} routes and Micronaut {@code @Transactional} code share transactions.
 *
 * <p>When Micronaut Data transactions are available, a policy is registered for each propagation
 * behavior, named like the Spring policies of Camel: {@code PROPAGATION_REQUIRED},
 * {@code PROPAGATION_REQUIRES_NEW}, {@code PROPAGATION_MANDATORY}, {@code PROPAGATION_SUPPORTS},
 * {@code PROPAGATION_NOT_SUPPORTED}, {@code PROPAGATION_NEVER} and {@code PROPAGATION_NESTED}.
 * {@code transacted()} uses {@code PROPAGATION_REQUIRED}, and {@code transacted("PROPAGATION_REQUIRES_NEW")}
 * uses another one. Declare a bean of this type to use other transaction settings or another
 * data source.</p>
 *
 * <p>The transaction rolls back when the exchange fails or is marked for rollback, for example with
 * {@code rollback()} or {@code markRollbackOnly()}. Redeliveries happen in the same transaction, and
 * an exception handled by {@code onException} does not roll the transaction back.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautTransactionPolicy implements TransactedPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautTransactionPolicy.class);

    private final TransactionOperations<?> transactionOperations;
    private final TransactionDefinition transactionDefinition;

    /**
     * Creates a policy for transactions with the {@link TransactionDefinition.Propagation#REQUIRED} propagation.
     *
     * @param transactionOperations The transaction operations
     */
    public MicronautTransactionPolicy(TransactionOperations<?> transactionOperations) {
        this(transactionOperations, TransactionDefinition.DEFAULT);
    }

    /**
     * @param transactionOperations The transaction operations
     * @param transactionDefinition The definition of the transactions
     */
    public MicronautTransactionPolicy(TransactionOperations<?> transactionOperations, TransactionDefinition transactionDefinition) {
        this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations");
        this.transactionDefinition = Objects.requireNonNull(transactionDefinition, "transactionDefinition");
    }

    /**
     * @return The transaction operations
     */
    public TransactionOperations<?> getTransactionOperations() {
        return transactionOperations;
    }

    /**
     * @return The definition of the transactions
     */
    public TransactionDefinition getTransactionDefinition() {
        return transactionDefinition;
    }

    /**
     * Runs a callback in a transaction of this policy.
     *
     * @param callback The callback
     * @param <R>      The result type
     * @return The result of the callback
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R execute(TransactionCallback<?, R> callback) {
        return (R) ((TransactionOperations) transactionOperations).execute(transactionDefinition, (TransactionCallback) callback);
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        // nothing to prepare
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        // The route uses a transactional error handler from here on. The processor is also wrapped
        // in it, as routes can change the propagation, for example from REQUIRED to REQUIRES_NEW.
        RouteDefinition routeDefinition = (RouteDefinition) route.getRoute();
        ErrorHandlerFactory builder = routeDefinition.getErrorHandlerFactory();
        if (builder instanceof RefErrorHandlerDefinition ref && ErrorHandlerHelper.isErrorHandlerFactoryConfigured(ref.getRef())) {
            LOG.debug("Looking up error handler with ref: {}", ref.getRef());
            builder = ErrorHandlerHelper.lookupErrorHandlerFactory(route, ref.getRef(), true);
        }

        MicronautTransactionErrorHandlerDefinition transactional;
        if (builder instanceof MicronautTransactionErrorHandlerDefinition configured) {
            LOG.debug("The configured error handler is transactional: {}", configured);
            transactional = (MicronautTransactionErrorHandlerDefinition) configured.cloneBuilder();
        } else if (builder != null && builder.supportTransacted()) {
            throw new RuntimeCamelException("The transactional error handler '" + builder + "' is not a "
                + MicronautTransactionErrorHandlerDefinition.class.getName() + ", which Micronaut transactions require");
        } else {
            LOG.debug("No transactional error handler configured, using a default {}", MicronautTransactionErrorHandlerDefinition.class.getSimpleName());
            transactional = new MicronautTransactionErrorHandlerDefinition();
        }
        transactional.setTransactedPolicy(this);
        if (builder != null) {
            // keep the onException clauses of the configured error handler
            route.addErrorHandlerFactoryReference(builder, transactional);
        }

        Processor answer;
        try {
            ModelCamelContext camelContext = (ModelCamelContext) route.getCamelContext();
            answer = camelContext.getModelReifierFactory().createErrorHandler(route, transactional, processor);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        route.setErrorHandlerFactory(transactional);
        return answer;
    }

    @Override
    public String toString() {
        return "MicronautTransactionPolicy[" + transactionDefinition.getPropagationBehavior() + "]";
    }
}
