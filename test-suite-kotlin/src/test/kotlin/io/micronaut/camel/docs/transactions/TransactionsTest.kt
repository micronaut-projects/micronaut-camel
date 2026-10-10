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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.apache.camel.CamelExecutionException
import org.apache.camel.ProducerTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

@MicronautTest(transactional = false)
class TransactionsTest {

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @Inject
    lateinit var jdbcTemplate: JdbcTemplate

    @Inject
    lateinit var transactionOperations: TransactionOperations<*>

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("delete from horses")
        jdbcTemplate.update("delete from audit")
        jdbcTemplate.update("delete from orders")
        jdbcTemplate.update("delete from CAMEL_MESSAGEPROCESSED")
    }

    @Test
    fun successfulRouteCommits() {
        producerTemplate.sendBodyAndHeaders("direct:add-horse", null, mapOf<String, Any>("name" to "Varenne", "age" to 8))

        assertEquals(listOf("Varenne"), horses())
        assertEquals(listOf("Added Varenne"), names("audit"))
        assertEquals(1, producerTemplate.requestBody("direct:count-horses", null, Int::class.javaObjectType))
    }

    @Test
    fun rollbackKeepsTheNewTransaction() {
        assertThrows(CamelExecutionException::class.java) {
            producerTemplate.sendBodyAndHeaders("direct:add-horse", null, mapOf<String, Any>("name" to "Seabiscuit", "age" to 9, "fail" to true))
        }

        assertEquals(listOf<String?>(), horses())
        assertEquals(listOf("Added Seabiscuit"), names("audit"))
    }

    @Test
    fun failingSplitRollsBackEverything() {
        assertThrows(CamelExecutionException::class.java) {
            producerTemplate.sendBody("direct:add-horses", listOf("Varenne", "Varenne"))
        }

        assertEquals(listOf<String?>(), horses())
    }

    @Test
    fun routeJoinsTheTransactionOfTheCaller() {
        transactionOperations.executeWrite { status ->
            producerTemplate.sendBodyAndHeaders("direct:add-horse", null, mapOf<String, Any>("name" to "Varenne", "age" to 8))
            status.setRollbackOnly()
        }

        assertEquals(listOf<String?>(), horses())
    }

    @Test
    fun failedOrdersAreNotRememberedAsProcessed() {
        assertThrows(CamelExecutionException::class.java) {
            producerTemplate.sendBodyAndHeaders("direct:order", null, mapOf<String, Any>("orderId" to "A1", "fail" to true))
        }
        assertEquals(listOf<String?>(), names("orders"))

        producerTemplate.sendBodyAndHeader("direct:order", null, "orderId", "A1")
        producerTemplate.sendBodyAndHeader("direct:order", null, "orderId", "A1")

        assertEquals(listOf("A1"), names("orders"))
    }

    private fun horses(): List<String?> = jdbcTemplate.queryForList("select name from horses", String::class.java)

    private fun names(table: String): List<String?> = jdbcTemplate.queryForList("select * from $table", String::class.java)
}
