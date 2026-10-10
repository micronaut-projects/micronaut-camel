from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from jakarta.inject import Inject
from java.lang import String
from java.util import Map
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ProducerTemplate
from org.junit.jupiter.api import Test


@MicronautTest
@Property(name="endpoints.health.details-visible", value="ANONYMOUS")
@Property(name="camel.health.exposure-level", value="full")
@Property(name="camel.metrics.enabled", value="true")
# the file consumer of the docs.integrations routes is DOWN until its first poll
@Property(name="camel.health.initial-state", value="up")
class OperationsTest:

    client: Annotated[HttpClient, Inject, Client("/")]
    producer_template: Annotated[ProducerTemplate, Inject]
    meter_registry: Annotated[MeterRegistry, Inject]

    @Test
    def custom_health_checks_are_reported(self) -> None:
        health = self.client.toBlocking().retrieve("/health", Map)
        inventory = health["details"]["camel"]["details"]["inventory"]

        assert inventory["status"] == "UP"
        assert inventory["warehouse"] == "main"

    @Test
    def metrics_are_recorded_in_the_micronaut_registry(self) -> None:
        assert self.producer_template.requestBody("direct:submit-order", "42", String) == "Order 42 placed"

        assert self.meter_registry.get("orders.placed").counter().count() >= 1
        assert self.meter_registry.find("camel.route.policy").tag("routeId", "submit-order").timer() is not None
