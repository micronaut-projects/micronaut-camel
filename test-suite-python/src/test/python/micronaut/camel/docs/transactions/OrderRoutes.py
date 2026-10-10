# tag::routes[]
from jakarta.inject import Singleton
from java.lang import IllegalStateException
from org.apache.camel.builder import RouteBuilder


@Singleton
class OrderRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:order")
            .transacted()
            .idempotentConsumer(self.header("orderId")).idempotentRepository("processedOrders")  # <1>
                .to("sql:insert into orders(id) values (:#orderId)")
                .filter(self.header("fail"))
                    .throwException(IllegalStateException("Order failed")))  # <2>
# end::routes[]
