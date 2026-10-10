from typing import Annotated

from io.micronaut.camel.testservices import DocsServices
from jakarta.inject import Inject
from java.lang import String
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import ConsumerTemplate, ProducerTemplate
from org.junit.jupiter.api import AfterAll, Test, TestInstance


# the docs-kafka environment starts a Kafka broker and configures Micronaut Kafka with it
# (see io.micronaut.camel.testservices.DocsServices)
@MicronautTest(environments=["docs-kafka"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name="spec.name", value="MessagingTest")
class MessagingTest:

    application_context: Annotated[ApplicationContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]
    consumer_template: Annotated[ConsumerTemplate, Inject]

    @AfterAll
    def stop_kafka(self) -> None:
        self.application_context.stop()
        DocsServices.stopKafka()

    @Test
    def routes_use_the_brokers_of_micronaut_kafka(self) -> None:
        self.producer_template.sendBody("direct:send-order", "order-1")

        assert self.consumer_template.receiveBody("seda:shipping", 30_000, String) == "order-1"
