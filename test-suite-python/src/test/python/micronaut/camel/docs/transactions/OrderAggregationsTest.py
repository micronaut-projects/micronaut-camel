from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.core.annotation import Introspected
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext
from org.apache.camel.processor.aggregate.jdbc import JdbcAggregationRepository
from org.apache.camel.support import DefaultExchange
from org.junit.jupiter.api import Test


@Introspected
@dataclass
class Line:
    item: str
    quantity: int


@MicronautTest(transactional=False)
class OrderAggregationsTest:

    camel_context: Annotated[CamelContext, Inject]
    repository: Annotated[JdbcAggregationRepository, Inject, Named("orderAggregations")]

    @Test
    def stores_exchanges_as_json(self) -> None:
        # started by Camel when a route uses it
        self.camel_context.addService(self.repository)
        exchange = DefaultExchange(self.camel_context)
        exchange.getMessage().setBody(Line("book", 2))
        exchange.getMessage().setHeader("customer", "Fred")

        self.repository.add(self.camel_context, "basket-1", exchange)

        stored = self.repository.get(self.camel_context, "basket-1")
        assert stored.getMessage().getBody() == Line("book", 2)
        assert stored.getMessage().getHeader("customer") == "Fred"
        self.repository.remove(self.camel_context, "basket-1", stored)
        self.repository.confirm(self.camel_context, stored.getExchangeId())
