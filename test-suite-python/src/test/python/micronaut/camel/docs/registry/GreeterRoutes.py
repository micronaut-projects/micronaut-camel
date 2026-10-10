# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class GreeterRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:hi")
            .to("bean:greeter?method=greet"))  # <1>
# end::routes[]
