# tag::sender[]
from abc import ABC, abstractmethod


class OrderSender(ABC):

    @abstractmethod
    def send(self, order: str) -> None:
        ...
# end::sender[]
