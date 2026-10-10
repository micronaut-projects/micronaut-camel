# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class WeatherTools(RouteBuilder):

    def configure(self) -> None:
        (self.from_("ai-tool:forecast?tags=weather"  # <1>
                    "&description=Returns the weather forecast of a city"
                    "&parameter.city=string&parameter.city.description=The city&parameter.city.required=true")
            .setBody(self.simple("Sunny in ${header.city}")))  # <2>
# end::routes[]
