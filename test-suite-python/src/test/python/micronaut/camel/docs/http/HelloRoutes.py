# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel import Exchange
from org.apache.camel.builder import RouteBuilder


@Singleton
class HelloRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("platform-http:/hello/{name}?httpMethodRestrict=GET")  # <1>
            .setHeader(Exchange.CONTENT_TYPE, self.constant("text/plain"))
            .setBody(self.simple("Hello ${header.name}")))  # <2>
# end::routes[]
