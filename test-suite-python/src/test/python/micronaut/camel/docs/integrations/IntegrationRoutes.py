# tag::routes[]
from jakarta.inject import Singleton
from java.lang import String
from org.apache.camel.builder import RouteBuilder


@Singleton
class IntegrationRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("file:{{shop.inbox:build/tmp/docs-inbox}}?scheduler=micronaut&scheduler.cron=0/1+*+*+*+*+*")  # <1>
            .convertBodyTo(String)
            .to("direct:import"))

        (self.from_("direct:import")
            .idempotentConsumer(self.body()).idempotentRepository("imports")  # <2>
            .to("mock:imported"))

        (self.from_("direct:order-json")
            .marshal().custom("micronaut-json"))  # <3>
# end::routes[]
