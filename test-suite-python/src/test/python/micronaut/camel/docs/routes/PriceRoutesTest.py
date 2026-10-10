from typing import Annotated

from jakarta.inject import Inject
from java.lang import Integer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class PriceRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def prices_an_item(self) -> None:
        assert self.producer_template.requestBody("direct:price", "book", Integer) == 40
