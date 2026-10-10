from typing import Annotated

from io.micronaut.camel.objectstorage import ObjectStorageConstants
from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import FluentProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class DocumentRoutesTest:

    producer: Annotated[FluentProducerTemplate, Inject]

    @Test
    def documents_are_stored_and_read(self) -> None:
        (self.producer.to("direct:archive-document")
            .withHeader(ObjectStorageConstants.KEY, "invoice-1.txt")
            .withBody("Invoice 1")
            .send())

        assert (self.producer.to("direct:read-document")
            .withHeader(ObjectStorageConstants.KEY, "invoice-1.txt")
            .request(String)) == "Invoice 1"
