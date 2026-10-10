from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.core.annotation import Introspected
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext
from org.apache.camel.spi import AggregationRepository
from org.apache.camel.support import DefaultExchange
from org.junit.jupiter.api import Test


@Introspected
@dataclass
class Item:
    name: str
    quantity: int


@MicronautTest
class BasketRepositoriesTest:

    camel_context: Annotated[CamelContext, Inject]
    baskets: Annotated[AggregationRepository, Inject, Named("baskets")]

    @Test
    def stores_exchanges_in_the_cache(self) -> None:
        exchange = DefaultExchange(self.camel_context)
        exchange.getMessage().setBody(Item("pen", 3))

        self.baskets.add(self.camel_context, "basket-1", exchange)

        assert set(self.baskets.getKeys()) == {"basket-1"}
        assert self.baskets.get(self.camel_context, "basket-1").getMessage().getBody() == Item("pen", 3)
