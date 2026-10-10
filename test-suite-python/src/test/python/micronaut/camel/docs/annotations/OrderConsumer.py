# tag::consumer[]
import threading

from jakarta.inject import Singleton
from micronaut.camel.docs.annotations.Price import Price
from org.apache.camel import Consume


@Singleton
class OrderConsumer:

    def __init__(self):
        self._lock = threading.Lock()
        self._received: list[Price] = []

    @Consume("direct:orders")  # <1>
    def receive(self, price: Price) -> None:  # <2>
        with self._lock:
            self._received.append(price)

    def received(self) -> list[Price]:
        with self._lock:
            return list(self._received)
# end::consumer[]
