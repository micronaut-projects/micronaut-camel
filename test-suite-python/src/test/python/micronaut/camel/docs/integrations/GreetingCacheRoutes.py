# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class GreetingCacheRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:remember-greeting")
            .setHeader("CamelRedis.Key", self.simple("greeting:${header.name}"))
            .setHeader("CamelRedis.Value", self.body())
            .to("spring-redis://localhost:6379?command=SET"))  # <1>

        (self.from_("direct:recall-greeting")
            .setHeader("CamelRedis.Key", self.simple("greeting:${header.name}"))
            .to("spring-redis://localhost:6379?command=GET"))  # <2>
# end::routes[]
