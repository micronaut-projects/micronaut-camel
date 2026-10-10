from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from java.nio.file import Files, Path
from java.util import Map
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext, ProducerTemplate
from org.apache.camel.component.mock import MockEndpoint
from org.junit.jupiter.api import Test


@MicronautTest
class IntegrationRoutesTest:

    camel_context: Annotated[CamelContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def files_are_imported_once(self) -> None:
        imported = self.camel_context.getEndpoint("mock:imported", MockEndpoint)
        imported.expectedBodiesReceived("order-1")

        inbox = Files.createDirectories(Path.of("build/tmp/docs-inbox"))
        Files.writeString(inbox.resolve("a.txt"), "order-1")
        imported.assertIsSatisfied()

        self.producer_template.sendBody("direct:import", "order-1")
        imported.assertIsSatisfied()

    @Test
    def orders_are_written_as_json(self) -> None:
        assert self.producer_template.requestBody("direct:order-json", Map.of("item", "book"), String) == '{"item":"book"}'
