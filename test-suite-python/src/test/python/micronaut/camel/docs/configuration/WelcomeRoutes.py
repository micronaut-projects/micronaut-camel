# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class WelcomeRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:welcome")
            .setBody(self.simple("{{shop.welcome:Welcome}} ${body}")))  # <1>
# end::routes[]
