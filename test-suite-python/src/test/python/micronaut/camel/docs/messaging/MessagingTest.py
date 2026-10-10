from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.test.support import TestPropertyProvider
from org.apache.camel import ConsumerTemplate, ProducerTemplate
from org.junit.jupiter.api import AfterAll, Test, TestInstance
from org.testcontainers.kafka import KafkaContainer


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name="spec.name", value="MessagingTest")
class MessagingTest(TestPropertyProvider):

    application_context: Annotated[ApplicationContext, Inject]
    producer_template: Annotated[ProducerTemplate, Inject]
    consumer_template: Annotated[ConsumerTemplate, Inject]

    def getProperties(self) -> dict[str, str]:
        self.kafka = KafkaContainer("apache/kafka:4.2.0")
        self.kafka.start()
        # what Micronaut Kafka is configured with, here the broker of the test
        return {"kafka.bootstrap.servers": self.kafka.getBootstrapServers()}

    @AfterAll
    def stop_kafka(self) -> None:
        self.application_context.stop()
        self.kafka.stop()

    @Test
    def routes_use_the_brokers_of_micronaut_kafka(self) -> None:
        self.producer_template.sendBody("direct:send-order", "order-1")

        assert self.consumer_template.receiveBody("seda:shipping", 30_000, String) == "order-1"
