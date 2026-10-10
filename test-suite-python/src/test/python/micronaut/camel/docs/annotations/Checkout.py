# tag::checkout[]
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.camel.docs.annotations.OrderSender import OrderSender
from org.apache.camel import Produce


@Singleton
class Checkout:

    orders: Annotated[OrderSender, Produce("direct:orders")]  # <1>

    def checkout(self, item: str) -> None:
        self.orders.send(item)  # <2>
# end::checkout[]
