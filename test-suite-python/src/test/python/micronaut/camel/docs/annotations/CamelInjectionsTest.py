from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.camel.docs.registry.Greeter import Greeter
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import BeanInject, PropertyInject
from org.junit.jupiter.api import Test


# Camel's other injection annotations on the attributes of a Python bean
@Singleton
class Greetings:

    greeter: Annotated[Greeter, BeanInject("greeter")]
    welcome: Annotated[str, PropertyInject("shop.welcome")]
    farewell: Annotated[str, PropertyInject("shop.farewell", defaultValue="Bye")]

    def greet(self, name: str) -> str:
        return self.welcome + ", " + self.greeter.greet(name) + ". " + self.farewell


@MicronautTest
@Property(name="shop.welcome", value="Welcome")
class CamelInjectionsTest:

    greetings: Annotated[Greetings, Inject]

    @Test
    def beans_and_properties_are_injected(self) -> None:
        assert self.greetings.greet("Fred") == "Welcome, Hi Fred. Bye"
