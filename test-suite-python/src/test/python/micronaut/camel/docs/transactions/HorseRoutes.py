# tag::routes[]
from jakarta.inject import Singleton
from org.apache.camel.builder import RouteBuilder


@Singleton
class HorseRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:add-horse")
            .transacted()  # <1>
            .to("sql:insert into horses(name, age) values (:#name, :#age)")  # <2>
            .to("direct:audit")
            .filter(self.header("fail"))
                .rollback("Horse rejected"))  # <3>

        (self.from_("direct:add-horses")
            .transacted()
            .split(self.body())  # <4>
                .to("sql:insert into horses(name) values (:#${body})"))

        (self.from_("direct:audit")
            .transacted("PROPAGATION_REQUIRES_NEW")  # <5>
            .setBody(self.simple("Added ${header.name}"))
            .to("sql:insert into audit(message) values (:#${body})"))

        (self.from_("direct:count-horses")
            .transacted("readOnly")  # <6>
            .to("sql:select count(*) from horses?outputType=SelectOne"))
# end::routes[]
