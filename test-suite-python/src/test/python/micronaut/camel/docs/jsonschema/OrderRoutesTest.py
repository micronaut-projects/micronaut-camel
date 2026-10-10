from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext, CamelExecutionException, ProducerTemplate, ValidationException
from org.apache.camel.component.mock import MockEndpoint
from org.junit.jupiter.api import Test


@MicronautTest
class OrderRoutesTest:

    camel_context: Annotated[CamelContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def validates_with_the_schema_resource(self) -> None:
        self._assert_validates("direct:checked-orders")

    @Test
    def validates_with_the_schema_of_the_type(self) -> None:
        self._assert_validates("direct:typed-orders")

    @Test
    def validates_the_input_type(self) -> None:
        self._assert_validates("direct:contract-orders")

    def _assert_validates(self, uri: str) -> None:
        orders = self.camel_context.getEndpoint("mock:valid-orders", MockEndpoint)
        orders.reset()
        orders.expectedMessageCount(1)

        self.producer_template.sendBody(uri, '{"id":"A-1","quantity":2}')

        orders.assertIsSatisfied()
        try:
            self.producer_template.sendBody(uri, '{"id":"","quantity":0}')
        except CamelExecutionException as e:
            assert isinstance(e.getCause(), ValidationException)
        else:
            assert False, "CamelExecutionException expected"
        orders.assertIsSatisfied()
