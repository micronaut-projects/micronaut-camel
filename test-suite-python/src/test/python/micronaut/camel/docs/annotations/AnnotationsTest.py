# tag::test[]
from typing import Annotated

from jakarta.inject import Inject
from micronaut.camel.docs.annotations.Checkout import Checkout
from micronaut.camel.docs.annotations.OrderConsumer import OrderConsumer
from micronaut.camel.docs.annotations.Price import Price
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import EndpointInject, ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class AnnotationsTest:

    orders: Annotated[ProducerTemplate, EndpointInject("direct:orders")]  # <1>
    checkout: Annotated[Checkout, Inject]
    consumer: Annotated[OrderConsumer, Inject]

    @Test
    def orders_reach_the_consumer(self) -> None:
        self.checkout.checkout("book=40")
        self.orders.sendBody("pen=2")

        assert self.consumer.received() == [Price("book", 40), Price("pen", 2)]
# end::test[]
