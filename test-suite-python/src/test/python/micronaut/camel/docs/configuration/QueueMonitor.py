# tag::bean[]
from io.micronaut.camel.requires import RequiresCamelSeda
from jakarta.inject import Singleton
from org.apache.camel import CamelContext
from org.apache.camel.component.seda import SedaComponent


@Singleton
@RequiresCamelSeda  # <1>
class QueueMonitor:

    def __init__(self, camel_context: CamelContext):
        self.camel_context = camel_context

    def queue_size(self) -> int:
        return self.camel_context.getComponent("seda", SedaComponent).getQueueSize()  # <2>
# end::bean[]
