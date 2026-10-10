# tag::test[]
from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class GreetingRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]  # <1>

    @Test
    def greets(self) -> None:
        greeting = self.producer_template.requestBody("direct:greet", "Fred", String)

        assert greeting == "Hello Fred"
# end::test[]
