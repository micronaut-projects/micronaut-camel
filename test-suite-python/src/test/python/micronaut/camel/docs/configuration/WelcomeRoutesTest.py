from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.camel.docs.configuration.QueueMonitor import QueueMonitor
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
@Property(name="camel.component.seda.queue-size", value="500")
@Property(name="shop.welcome", value="Bienvenue")
class WelcomeRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]
    queue_monitor: Annotated[QueueMonitor, Inject]

    @Test
    def resolves_placeholders_from_the_environment(self) -> None:
        assert self.producer_template.requestBody("direct:welcome", "Fred", String) == "Bienvenue Fred"

    @Test
    def requires_the_seda_component(self) -> None:
        assert self.queue_monitor.queue_size() == 500
