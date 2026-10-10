# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class TicketRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:open-ticket")
            .to("bean-validator:ticket")  # <1>
            .setBody(self.simple("opened ${body.title}")))
# end::routes[]
