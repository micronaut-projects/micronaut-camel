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

import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.Serial;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The error handler of a transacted route, which processes the exchange in a Micronaut transaction
 * and rolls the transaction back when the exchange fails or is marked for rollback.
 *
 * <p>Redeliveries happen in the transaction, so the exchange is processed synchronously, on the
 * thread that holds the transaction.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautTransactionErrorHandler extends RedeliveryErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautTransactionErrorHandler.class);

    private final MicronautTransactionPolicy transactionPolicy;
    private final String transactionKey;
    private final LoggingLevel rollbackLoggingLevel;
    private final Set<Exchange> inflightTransactedExchanges = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("java:S107") // mirrors the constructor of RedeliveryErrorHandler
    MicronautTransactionErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
                                     @Nullable Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy,
                                     MicronautTransactionPolicy transactionPolicy, @Nullable Predicate retryWhile,
                                     @Nullable ScheduledExecutorService executorService,
                                     LoggingLevel rollbackLoggingLevel, @Nullable Processor onExceptionOccurredProcessor) {
        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, null, null, false, false, false,
            retryWhile, executorService, null, onExceptionOccurredProcessor);
        this.transactionPolicy = transactionPolicy;
        this.rollbackLoggingLevel = rollbackLoggingLevel;
        this.transactionKey = ObjectHelper.getIdentityHashCode(transactionPolicy);
    }

    @Override
    public ErrorHandler clone(Processor output) {
        MicronautTransactionErrorHandler answer = new MicronautTransactionErrorHandler(
            camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, transactionPolicy, retryWhilePolicy,
            executorService, rollbackLoggingLevel, onExceptionProcessor);
        // a shallow copy is fine, as the policies are not changed
        if (exceptionPolicies != null) {
            answer.exceptionPolicies = exceptionPolicies;
        }
        return answer;
    }

    @Override
    public boolean supportTransacted() {
        return true;
    }

    @Override
    public void process(Exchange exchange) {
        if (transactionPolicy.getTransactionDefinition().getPropagationBehavior() != TransactionDefinition.Propagation.REQUIRES_NEW
            && exchange.getUnitOfWork() != null
            && exchange.getUnitOfWork().isTransactedBy(transactionKey)) {
            // already in a transaction of this policy
            processByErrorHandler(exchange);
        } else {
            processInTransaction(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // a transaction is bound to the thread, so the exchange is processed synchronously
        try {
            process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        if (suspendOnly) {
            return;
        }
        super.prepareShutdown(suspendOnly, forced);
        if (forced) {
            // roll back in-flight transactions before the connection pools are closed
            for (Exchange exchange : inflightTransactedExchanges) {
                LOG.debug("Marking in-flight transacted exchange for rollback due to forced shutdown: {}", exchange.getExchangeId());
                exchange.setRollbackOnly(true);
            }
        }
    }

    @Override
    public String toString() {
        if (output == null) {
            return "";
        }
        return "MicronautTransactionErrorHandler:" + transactionPolicy.getTransactionDefinition().getPropagationBehavior() + "[" + getOutput() + "]";
    }

    private void processInTransaction(Exchange exchange) {
        String redelivered = Boolean.toString(exchange.isExternalRedelivered());
        String ids = ExchangeHelper.logIds(exchange);
        try {
            if (exchange.getUnitOfWork() != null) {
                exchange.getUnitOfWork().beginTransactedBy(transactionKey);
            }
            inflightTransactedExchanges.add(exchange);
            LOG.debug("Transaction begin ({}) redelivered({}) for {}", transactionKey, redelivered, ids);
            processInTransactionTemplate(exchange);
            logTransactionCommit(redelivered, ids);
        } catch (TransactionRollbackException e) {
            // only thrown to roll the transaction back
            logTransactionRollback(redelivered, ids, null, true);
        } catch (Exception e) {
            exchange.setException(e);
            logTransactionRollback(redelivered, ids, e, false);
        } finally {
            inflightTransactedExchanges.remove(exchange);
            if (exchange.getUnitOfWork() != null) {
                exchange.getUnitOfWork().endTransactedBy(transactionKey);
            }
        }

        // a rollback of only this transaction must not affect outer transactions
        boolean onlyLast = exchange.isRollbackOnlyLast();
        exchange.setRollbackOnlyLast(false);
        if (onlyLast) {
            LOG.debug("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnlyLast",
                transactionKey, redelivered, ids, exchange.getException());
            exchange.setException(null);
        }
    }

    private void processInTransactionTemplate(Exchange exchange) {
        transactionPolicy.execute(status -> {
            processByErrorHandler(exchange);
            if (preparingShutdown) {
                LOG.debug("Forced shutdown in progress, marking exchange for rollback: {}", exchange.getExchangeId());
                exchange.setRollbackOnly(true);
            }
            Exception exception = exchange.getException();
            if (exception != null || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
                if (!status.isRollbackOnly()) {
                    status.setRollbackOnly();
                }
                LOG.trace("Throwing exception to roll back the transaction of {}", transactionPolicy);
                // throwing makes the transaction manager roll back
                throw exception != null ? exception : new TransactionRollbackException();
            }
            return null;
        });
    }

    private void processByErrorHandler(Exchange exchange) {
        awaitManager.process(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                return MicronautTransactionErrorHandler.super.process(exchange, callback);
            }
        }, exchange);
    }

    private void logTransactionCommit(String redelivered, String ids) {
        if ("true".equals(redelivered) && (rollbackLoggingLevel == LoggingLevel.INFO
            || rollbackLoggingLevel == LoggingLevel.WARN || rollbackLoggingLevel == LoggingLevel.ERROR)) {
            // tell that a redelivered message was committed this time
            LOG.info("Transaction commit ({}) redelivered({}) for {}", transactionKey, redelivered, ids);
        } else {
            LOG.debug("Transaction commit ({}) redelivered({}) for {}", transactionKey, redelivered, ids);
        }
    }

    private void logTransactionRollback(String redelivered, String ids, @Nullable Throwable e, boolean rollbackOnly) {
        Level level = switch (rollbackLoggingLevel) {
            case ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case INFO -> Level.INFO;
            case DEBUG -> Level.DEBUG;
            case TRACE -> Level.TRACE;
            case OFF -> null;
        };
        if (level == null || !LOG.isEnabledForLevel(level)) {
            return;
        }
        if (rollbackOnly) {
            LOG.atLevel(level).log("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                transactionKey, redelivered, ids);
        } else {
            LOG.atLevel(level).log("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                transactionKey, redelivered, ids, e != null ? e.getMessage() : null);
        }
    }

    /**
     * Rolls the transaction back when the exchange is marked for rollback without an exception.
     */
    private static final class TransactionRollbackException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        TransactionRollbackException() {
            super("Exchange marked for rollback", null, false, false);
        }
    }
}
