# tag::routes[]
from jakarta.inject import Singleton
from java.lang import Integer
from org.apache.camel.builder import RouteBuilder


@Singleton
class InventoryRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:check-stock")
            .setHeader("CamelHttpPath", self.header("item"))
            .to("micronaut-http:inventory/stock?warehouse=north")  # <1>
            .convertBodyTo(Integer))
# end::routes[]
