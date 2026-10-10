from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
class ParcelRoutesTest:

    producer_template: Annotated[ProducerTemplate, Inject]

    @Test
    def routes_parcels(self) -> None:
        assert self.ship({"weight": 30, "country": "CH"}) == "freight"
        assert self.ship({"weight": 2, "country": "NO"}) == "customs for NO"
        assert self.ship({"weight": 2, "country": "FR"}) == "post"

    def ship(self, headers) -> str:
        return self.producer_template.requestBodyAndHeaders("direct:parcel", "parcel", headers, String)
