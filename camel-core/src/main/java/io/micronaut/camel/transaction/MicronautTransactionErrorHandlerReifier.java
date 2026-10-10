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
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Creates the {@link MicronautTransactionErrorHandler} of a {@link MicronautTransactionErrorHandlerDefinition}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautTransactionErrorHandlerReifier extends ErrorHandlerReifier<MicronautTransactionErrorHandlerDefinition> {

    /**
     * @param route      The route
     * @param definition The error handler definition
     */
    MicronautTransactionErrorHandlerReifier(Route route, MicronautTransactionErrorHandlerDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        MicronautTransactionPolicy policy = resolveTransactionPolicy();
        ObjectHelper.notNull(policy, "transactionPolicy", this);
        MicronautTransactionErrorHandler answer = new MicronautTransactionErrorHandler(
            camelContext,
            processor,
            resolveLogger(),
            getProcessor(definition.getOnRedeliveryProcessor(), definition.getOnRedeliveryRef()),
            resolveRedeliveryPolicy(),
            policy,
            resolveRetryWhilePolicy(),
            getExecutorService(definition.getExecutorServiceBean(), definition.getExecutorServiceRef()),
            resolveRollbackLoggingLevel(),
            getProcessor(definition.getOnExceptionOccurredProcessor(), definition.getOnExceptionOccurredRef())
        );
        configure(answer);
        return answer;
    }

    private @Nullable MicronautTransactionPolicy resolveTransactionPolicy() {
        if (definition.getTransactedPolicy() instanceof MicronautTransactionPolicy policy) {
            return policy;
        }
        if (definition.getTransactedPolicyRef() != null) {
            return mandatoryLookup(definition.getTransactedPolicyRef(), MicronautTransactionPolicy.class);
        }
        Map<String, MicronautTransactionPolicy> policies = findByTypeWithName(MicronautTransactionPolicy.class);
        if (policies.size() == 1) {
            return policies.values().iterator().next();
        }
        MicronautTransactionPolicy policy = findSingleByType(MicronautTransactionPolicy.class);
        if (policy != null) {
            return policy;
        }
        return lookupByNameAndType(MicronautTransactionPolicies.PROPAGATION_REQUIRED, MicronautTransactionPolicy.class);
    }

    private CamelLogger resolveLogger() {
        CamelLogger answer = definition.getLoggerBean();
        if (answer == null && definition.getLoggerRef() != null) {
            answer = mandatoryLookup(definition.getLoggerRef(), CamelLogger.class);
        }
        if (answer == null) {
            answer = new CamelLogger(LoggerFactory.getLogger(MicronautTransactionErrorHandler.class), LoggingLevel.ERROR);
        }
        if (definition.getLevel() != null) {
            answer.setLevel(parse(LoggingLevel.class, definition.getLevel()));
        }
        return answer;
    }

    private LoggingLevel resolveRollbackLoggingLevel() {
        if (definition.getRollbackLoggingLevel() != null) {
            return parse(LoggingLevel.class, definition.getRollbackLoggingLevel());
        }
        return LoggingLevel.WARN;
    }

    private RedeliveryPolicy resolveRedeliveryPolicy() {
        RedeliveryPolicyDefinition policyDefinition = definition.getRedeliveryPolicy();
        String ref = definition.getRedeliveryPolicyRef();
        if (policyDefinition == null && ref != null) {
            // the reference may be to a definition
            policyDefinition = lookupByNameAndType(ref, RedeliveryPolicyDefinition.class);
        }
        if (policyDefinition != null) {
            return ErrorHandlerReifier.createRedeliveryPolicy(policyDefinition, camelContext, null);
        }
        if (ref != null) {
            return mandatoryLookup(ref, RedeliveryPolicy.class);
        }
        return RedeliveryPolicy.DEFAULT_POLICY;
    }

    private @Nullable Predicate resolveRetryWhilePolicy() {
        Predicate answer = definition.getRetryWhilePredicate();
        if (answer == null && definition.getRetryWhileRef() != null) {
            Language bean = camelContext.resolveLanguage("bean");
            answer = bean.createPredicate(definition.getRetryWhileRef());
            answer.initPredicate(camelContext);
        }
        return answer;
    }

    private @Nullable ScheduledExecutorService getExecutorService(@Nullable ScheduledExecutorService executorService,
                                                                  @Nullable String executorServiceRef) {
        if (executorService != null && !executorService.isShutdown()) {
            return executorService;
        }
        if (executorServiceRef == null) {
            // the error handler uses the executor service of the Camel context
            return null;
        }
        ScheduledExecutorService answer = lookupByNameAndType(executorServiceRef, ScheduledExecutorService.class);
        if (answer == null) {
            ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
            ThreadPoolProfile profile = manager.getThreadPoolProfile(executorServiceRef);
            answer = manager.newScheduledThreadPool(this, executorServiceRef, profile);
        }
        if (answer == null) {
            throw new IllegalArgumentException("ExecutorService " + executorServiceRef + " not found in registry.");
        }
        return answer;
    }
}
