# tag::routes[]
from jakarta.inject import Singleton
# end::routes[]
from micronaut.context.annotation import Requires
# tag::routes[]
from org.apache.camel.builder import RouteBuilder


@Singleton
# end::routes[]
@Requires(property="spec.name", value="MessagingTest")
# tag::routes[]
class OrderRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:send-order")
            .to("kafka:orders"))  # <1>

        (self.from_("kafka:orders?groupId=shipping&autoOffsetReset=earliest")  # <2>
            .to("seda:shipping"))
# end::routes[]
