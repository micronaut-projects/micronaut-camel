# tag::test[]
from typing import Annotated

from io.micronaut.camel.test import MockEndpoints
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext, ProducerTemplate
from org.apache.camel.component.mock import MockEndpoint
from org.junit.jupiter.api import Test


@MicronautTest
@MockEndpoints("log:*")  # <1>
class MockGreetingsTest:

    camel_context: Annotated[CamelContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def greetings_are_logged(self) -> None:
        greetings = self.camel_context.getEndpoint("mock:log:greetings", MockEndpoint)  # <2>
        greetings.expectedBodiesReceived("Hello Fred")

        self.producer_template.sendBody("direct:greet", "Fred")

        greetings.assertIsSatisfied()
# end::test[]
