# tag::routes[]
from jakarta.inject import Singleton
from java.util import Map
from org.apache.camel import CamelAuthorizationException, Exchange
from org.apache.camel.builder import RouteBuilder


@Singleton
class AdminRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.rest("/admin")
            .get("/stats").to("direct:admin-stats"))  # <1>

        (self.from_("direct:admin-stats")
            .onException(CamelAuthorizationException)  # <2>
                .handled(True)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, self.constant(403))
                .setBody(self.constant("Forbidden"))
            .end()
            .policy("adminPolicy")  # <3>
            .setBody(self.constant(Map.of("orders", 42))))
# end::routes[]
