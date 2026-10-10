# tag::routes[]
from jakarta.inject import Singleton
from micronaut.camel.docs.routes.PriceService import PriceService
from org.apache.camel.builder import RouteBuilder


@Singleton
class PriceRoutes(RouteBuilder):

    def __init__(self, price_service: PriceService):  # <1>
        self.price_service = price_service

    def configure(self) -> None:
        (self.from_("direct:price")
            .bean(self.price_service, "price_of"))  # <2>
# end::routes[]
