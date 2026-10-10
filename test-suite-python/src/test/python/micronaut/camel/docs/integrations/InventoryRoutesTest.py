from typing import Annotated

from jakarta.inject import Inject
from java.lang import Integer
from micronaut.core.io.socket import SocketUtils
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.test.support import TestPropertyProvider
from org.apache.camel import FluentProducerTemplate
from org.junit.jupiter.api import Test, TestInstance


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryRoutesTest(TestPropertyProvider):

    producer: Annotated[FluentProducerTemplate, Inject]

    def getProperties(self) -> dict[str, str]:
        # the inventory service is the InventoryController of this application
        port = str(SocketUtils.findAvailableTcpPort())
        return {
            "micronaut.server.port": port,
            "micronaut.http.services.inventory.url": f"http://localhost:{port}",
        }

    @Test
    def stock_is_checked_with_the_inventory_service(self) -> None:
        assert self.producer.to("direct:check-stock").withHeader("item", "book").request(Integer) == 12
