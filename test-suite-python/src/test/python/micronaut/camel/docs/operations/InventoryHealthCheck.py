# tag::check[]
from jakarta.inject import Singleton
from org.apache.camel.health import HealthCheckResultBuilder
from org.apache.camel.impl.health import AbstractHealthCheck


@Singleton  # <1>
class InventoryHealthCheck(AbstractHealthCheck):

    def __init__(self):
        super().__init__("shop", "inventory")  # <2>

    def doCall(self, builder: HealthCheckResultBuilder, options: dict[str, object]) -> None:
        builder.detail("warehouse", "main")  # <3>
        builder.up()
# end::check[]
