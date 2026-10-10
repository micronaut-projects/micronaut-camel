# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class ParcelRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:parcel")
            .choice()
                .when(self.header("weight").isGreaterThan(20))  # <1>
                    .setBody(self.constant("freight"))
                .when(self.header("country").in_("CH", "NO"))  # <2>
                    .setBody(self.simple("customs for ${header.country}"))  # <3>
                .otherwise()
                    .setBody(self.constant("post"))
            .end())
# end::routes[]
