from typing import Annotated

from jakarta.inject import Inject
from java.lang import Integer, String
from java.util import List
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.transaction import TransactionOperations
from org.apache.camel import CamelExecutionException, ProducerTemplate
from org.junit.jupiter.api import BeforeEach, Test
from org.springframework.jdbc.core import JdbcTemplate


@MicronautTest(transactional=False)
class TransactionsTest:

    producer_template: Annotated[ProducerTemplate, Inject]
    jdbc_template: Annotated[JdbcTemplate, Inject]
    transaction_operations: Annotated[TransactionOperations, Inject]

    @BeforeEach
    def clean_up(self) -> None:
        self.jdbc_template.update("delete from horses")
        self.jdbc_template.update("delete from audit")
        self.jdbc_template.update("delete from orders")
        self.jdbc_template.update("delete from CAMEL_MESSAGEPROCESSED")

    @Test
    def successful_route_commits(self) -> None:
        self.producer_template.sendBodyAndHeaders("direct:add-horse", None, {"name": "Varenne", "age": 8})

        assert self.horses() == ["Varenne"]
        assert self.names("audit") == ["Added Varenne"]
        assert self.producer_template.requestBody("direct:count-horses", None, Integer) == 1

    @Test
    def rollback_keeps_the_new_transaction(self) -> None:
        self.assert_fails(lambda: self.producer_template.sendBodyAndHeaders(
            "direct:add-horse", None, {"name": "Seabiscuit", "age": 9, "fail": True}))

        assert self.horses() == []
        assert self.names("audit") == ["Added Seabiscuit"]

    @Test
    def failing_split_rolls_back_everything(self) -> None:
        self.assert_fails(lambda: self.producer_template.sendBody("direct:add-horses", List.of("Varenne", "Varenne")))

        assert self.horses() == []

    @Test
    def route_joins_the_transaction_of_the_caller(self) -> None:
        def add_horse(status):
            self.producer_template.sendBodyAndHeaders("direct:add-horse", None, {"name": "Varenne", "age": 8})
            status.setRollbackOnly()
            return None

        self.transaction_operations.executeWrite(add_horse)

        assert self.horses() == []

    @Test
    def failed_orders_are_not_remembered_as_processed(self) -> None:
        self.assert_fails(lambda: self.producer_template.sendBodyAndHeaders(
            "direct:order", None, {"orderId": "A1", "fail": True}))
        assert self.names("orders") == []

        self.producer_template.sendBodyAndHeader("direct:order", None, "orderId", "A1")
        self.producer_template.sendBodyAndHeader("direct:order", None, "orderId", "A1")

        assert self.names("orders") == ["A1"]

    def horses(self) -> list[str]:
        return list(self.jdbc_template.queryForList("select name from horses", String))

    def names(self, table: str) -> list[str]:
        return list(self.jdbc_template.queryForList("select * from " + table, String))

    @staticmethod
    def assert_fails(call) -> None:
        try:
            call()
        except CamelExecutionException:
            return
        raise AssertionError("expected a CamelExecutionException")
