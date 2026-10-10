# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class OrderEventRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:place-order")
            .to("micronaut-event:micronaut.camel.docs.integrations.OrderPlaced"))  # <1>

        (self.from_("micronaut-event:micronaut.camel.docs.integrations.OrderPlaced")  # <2>
            .setBody(self.simple("placed ${body.item}"))
            .to("mock:placed"))
# end::routes[]
