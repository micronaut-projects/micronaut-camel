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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Registers a {@link MicronautTransactionPolicy} for each propagation behavior, named like the Spring
 * transaction policies of Camel, using the primary Micronaut {@link TransactionOperations}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@Internal
@Requires(classes = TransactionOperations.class)
@Requires(beans = TransactionOperations.class)
final class MicronautTransactionPolicies {

    /**
     * The name of the policy that {@code transacted()} uses by default.
     */
    static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";

    private final TransactionOperations<?> transactionOperations;

    /**
     * @param transactionOperations The primary transaction operations
     */
    MicronautTransactionPolicies(TransactionOperations<?> transactionOperations) {
        this.transactionOperations = transactionOperations;
    }

    @Singleton
    @Primary
    @Named(PROPAGATION_REQUIRED)
    MicronautTransactionPolicy required() {
        return policy(TransactionDefinition.Propagation.REQUIRED);
    }

    @Singleton
    @Named("PROPAGATION_REQUIRES_NEW")
    MicronautTransactionPolicy requiresNew() {
        return policy(TransactionDefinition.Propagation.REQUIRES_NEW);
    }

    @Singleton
    @Named("PROPAGATION_MANDATORY")
    MicronautTransactionPolicy mandatory() {
        return policy(TransactionDefinition.Propagation.MANDATORY);
    }

    @Singleton
    @Named("PROPAGATION_SUPPORTS")
    MicronautTransactionPolicy supports() {
        return policy(TransactionDefinition.Propagation.SUPPORTS);
    }

    @Singleton
    @Named("PROPAGATION_NOT_SUPPORTED")
    MicronautTransactionPolicy notSupported() {
        return policy(TransactionDefinition.Propagation.NOT_SUPPORTED);
    }

    @Singleton
    @Named("PROPAGATION_NEVER")
    MicronautTransactionPolicy never() {
        return policy(TransactionDefinition.Propagation.NEVER);
    }

    @Singleton
    @Named("PROPAGATION_NESTED")
    MicronautTransactionPolicy nested() {
        return policy(TransactionDefinition.Propagation.NESTED);
    }

    private MicronautTransactionPolicy policy(TransactionDefinition.Propagation propagation) {
        return new MicronautTransactionPolicy(transactionOperations, TransactionDefinition.of(propagation));
    }
}
