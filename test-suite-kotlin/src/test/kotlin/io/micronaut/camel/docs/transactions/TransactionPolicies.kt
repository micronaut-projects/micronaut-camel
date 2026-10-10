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
package io.micronaut.camel.docs.transactions

// tag::policy[]
import io.micronaut.camel.transaction.MicronautTransactionPolicy
import io.micronaut.context.annotation.Factory
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.support.DefaultTransactionDefinition
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration

@Factory
class TransactionPolicies {

    @Singleton
    @Named("readOnly") // <1>
    fun readOnly(transactionOperations: TransactionOperations<*>): MicronautTransactionPolicy {
        val definition = DefaultTransactionDefinition()
        definition.setReadOnly(true)
        definition.setTimeout(Duration.ofSeconds(5))
        return MicronautTransactionPolicy(transactionOperations, definition) // <2>
    }
}
// end::policy[]
