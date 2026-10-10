# tag::test[]
from typing import Annotated

from io.micronaut.camel.test import EnableRouteCoverage
from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
@EnableRouteCoverage  # <1>
class GreetingCoverageTest:

    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def greets(self) -> None:
        assert self.producer_template.requestBody("direct:greet", "Fred", String) == "Hello Fred"  # <2>
# end::test[]
