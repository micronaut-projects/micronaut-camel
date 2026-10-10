from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class GreeterRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def calls_the_bean_by_name(self) -> None:
        assert self.producer_template.requestBody("direct:hi", "Fred", String) == "Hi Fred"
