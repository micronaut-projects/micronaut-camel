# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class OrderRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:submit-order").routeId("submit-order")
            .to("micrometer:counter:orders.placed")  # <1>
            .setBody(self.simple("Order ${body} placed")))
# end::routes[]
