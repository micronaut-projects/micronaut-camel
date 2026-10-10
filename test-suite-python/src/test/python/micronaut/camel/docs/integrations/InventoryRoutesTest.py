from typing import Annotated

from jakarta.inject import Inject
from java.lang import Integer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import FluentProducerTemplate
from org.junit.jupiter.api import Test


# the docs-inventory environment runs the server on a free port, which is that of the inventory service: the
# InventoryController of this application (see io.micronaut.camel.testservices.DocsServices)
@MicronautTest(environments=["docs-inventory"])
class InventoryRoutesTest:

    producer: Annotated[FluentProducerTemplate, Inject]

    @Test
    def stock_is_checked_with_the_inventory_service(self) -> None:
        assert self.producer.to("direct:check-stock").withHeader("item", "book").request(Integer) == 12
