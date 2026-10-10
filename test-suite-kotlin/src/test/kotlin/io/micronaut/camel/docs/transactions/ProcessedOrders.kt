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

// tag::repository[]
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Factory
class ProcessedOrders {

    @Singleton
    @Named("processedOrders")
    fun processedOrders(transactionManager: DataSourceTransactionManager): JdbcMessageIdRepository { // <1>
        return JdbcMessageIdRepository(
            transactionManager.dataSource, // <2>
            TransactionTemplate(transactionManager), // <3>
            "orders"
        )
    }
}
// end::repository[]
