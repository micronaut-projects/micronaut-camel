# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton  # <1>
class GreetingRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:greet")  # <2>
            .routeId("greet")
            .setBody(self.simple("Hello ${body}"))
            .to("log:greetings"))
# end::routes[]
