# tag::routes[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from org.apache.camel.builder import LambdaRouteBuilder
from org.apache.camel.builder.endpoint import LambdaEndpointRouteBuilder


@Factory
class ShippingRoutes:

    @Singleton
    def shipping(self) -> LambdaRouteBuilder:  # <1>
        return lambda rb: rb.from_("direct:ship").setBody(rb.simple("shipped ${body}"))

    @Singleton
    def tracking(self) -> LambdaEndpointRouteBuilder:  # <2>
        return lambda rb: rb.from_(rb.direct("track")).setBody(rb.simple("tracking ${body}"))
# end::routes[]
