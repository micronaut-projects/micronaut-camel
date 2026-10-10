from jakarta.inject import Singleton
from micronaut.camel.docs.jsonschema.Order import Order
from org.apache.camel.builder import RouteBuilder


@Singleton
class OrderRoutes(RouteBuilder):

    def configure(self) -> None:
        # tag::validator[]
        (self.from_("direct:checked-orders")
            .to("json-validator:classpath:META-INF/schemas/order.schema.json")  # <1>
            .to("mock:valid-orders"))
        # end::validator[]

        # tag::resource[]
        (self.from_("direct:typed-orders")
            .to("json-validator:jsonschema:Order")  # <1>
            .to("mock:valid-orders"))
        # end::resource[]

        # tag::contract[]
        (self.from_("direct:contract-orders")
            .inputTypeWithValidate(Order)  # <1>
            .to("mock:valid-orders"))
        # end::contract[]
