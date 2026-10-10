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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.apache.camel.CamelExecutionException
import org.apache.camel.ProducerTemplate
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

@MicronautTest(transactional = false)
class TransactionsTest extends Specification {

    @Inject
    ProducerTemplate producerTemplate

    @Inject
    JdbcTemplate jdbcTemplate

    @Inject
    TransactionOperations<?> transactionOperations

    void setup() {
        jdbcTemplate.update('delete from horses')
        jdbcTemplate.update('delete from audit')
        jdbcTemplate.update('delete from orders')
        jdbcTemplate.update('delete from CAMEL_MESSAGEPROCESSED')
    }

    void 'a successful route commits'() {
        when:
        producerTemplate.sendBodyAndHeaders('direct:add-horse', null, [name: 'Varenne', age: 8])

        then:
        horses() == ['Varenne']
        names('audit') == ['Added Varenne']
        producerTemplate.requestBody('direct:count-horses', null, Integer) == 1
    }

    void 'a rollback keeps the new transaction'() {
        when:
        producerTemplate.sendBodyAndHeaders('direct:add-horse', null, [name: 'Seabiscuit', age: 9, fail: true])

        then:
        thrown(CamelExecutionException)
        horses() == []
        names('audit') == ['Added Seabiscuit']
    }

    void 'a failing split rolls back everything'() {
        when:
        producerTemplate.sendBody('direct:add-horses', ['Varenne', 'Varenne'])

        then:
        thrown(CamelExecutionException)
        horses() == []
    }

    void 'a route joins the transaction of the caller'() {
        when:
        transactionOperations.executeWrite { status ->
            producerTemplate.sendBodyAndHeaders('direct:add-horse', null, [name: 'Varenne', age: 8])
            status.setRollbackOnly()
        }

        then:
        horses() == []
    }

    void 'failed orders are not remembered as processed'() {
        when:
        producerTemplate.sendBodyAndHeaders('direct:order', null, [orderId: 'A1', fail: true])

        then:
        thrown(CamelExecutionException)
        names('orders') == []

        when:
        producerTemplate.sendBodyAndHeader('direct:order', null, 'orderId', 'A1')
        producerTemplate.sendBodyAndHeader('direct:order', null, 'orderId', 'A1')

        then:
        names('orders') == ['A1']
    }

    private List<String> horses() {
        jdbcTemplate.queryForList('select name from horses', String)
    }

    private List<String> names(String table) {
        jdbcTemplate.queryForList("select * from $table", String)
    }
}
