from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class ShippingRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def lambda_route_builders_are_collected(self) -> None:
        assert self.producer_template.requestBody("direct:ship", "book", String) == "shipped book"
        assert self.producer_template.requestBody("direct:track", "book", String) == "tracking book"
