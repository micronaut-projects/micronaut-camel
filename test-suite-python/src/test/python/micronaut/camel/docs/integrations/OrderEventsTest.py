from typing import Annotated

from jakarta.inject import Inject
from micronaut.camel.docs.integrations.OrderPlaced import OrderPlaced
from micronaut.context.event import ApplicationEventPublisher
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext, ProducerTemplate
from org.apache.camel.component.mock import MockEndpoint
from org.junit.jupiter.api import Test


@MicronautTest
class OrderEventsTest:

    camel_context: Annotated[CamelContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]
    publisher: Annotated[ApplicationEventPublisher[OrderPlaced], Inject]

    @Test
    def routes_publish_and_consume_application_events(self) -> None:
        placed = self.camel_context.getEndpoint("mock:placed", MockEndpoint)
        placed.expectedBodiesReceived("placed book", "placed pen")

        self.producer_template.sendBody("direct:place-order", OrderPlaced("book"))
        self.publisher.publishEvent(OrderPlaced("pen"))

        placed.assertIsSatisfied()
