# tag::bean[]
from jakarta.inject import Named, Singleton


@Singleton
@Named("greeter")  # <1>
class Greeter:

    def greet(self, name: str) -> str:
        return "Hi " + name
# end::bean[]
