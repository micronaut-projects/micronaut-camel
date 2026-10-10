from typing import Annotated

from jakarta.inject import Inject
from jakarta.validation import ValidatorFactory
from java.lang import String
from micronaut.camel.docs.integrations.Ticket import Ticket
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import CamelContext, FluentProducerTemplate
from org.apache.camel.component.bean.validator import BeanValidationException, BeanValidatorComponent
from org.junit.jupiter.api import Test


@MicronautTest
class TicketRoutesTest:

    producer: Annotated[FluentProducerTemplate, Inject]
    camel_context: Annotated[CamelContext, Inject]
    validator_factory: Annotated[ValidatorFactory, Inject]

    @Test
    def the_component_uses_micronaut_validation(self) -> None:
        component = self.camel_context.getComponent("bean-validator", BeanValidatorComponent)

        assert component.getValidatorFactory() is self.validator_factory
        assert "hibernate" not in self.validator_factory.getClass().getName()

    @Test
    def valid_tickets_are_opened(self) -> None:
        assert (self.producer.to("direct:open-ticket")
            .withBody(Ticket("Broken build", "fred@example.com"))
            .request(String)) == "opened Broken build"

    @Test
    def invalid_tickets_are_rejected(self) -> None:
        exchange = self.producer.to("direct:open-ticket").withBody(Ticket(" ", "not an email")).send()

        e = exchange.getException()
        assert isinstance(e, BeanValidationException)
        invalid = {violation.getPropertyPath().toString() for violation in e.getConstraintViolations()}
        assert invalid == {"title", "reporter"}
