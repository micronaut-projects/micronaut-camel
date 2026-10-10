# tag::routes[]
from jakarta.inject import Singleton
from java.lang import String
from org.apache.camel.builder import RouteBuilder


@Singleton
class DocumentRoutes(RouteBuilder):

    def configure(self) -> None:
        (self.from_("direct:archive-document")
            .to("object-storage:documents"))  # <1>

        (self.from_("direct:read-document")
            .to("object-storage:documents?operation=get")  # <2>
            .convertBodyTo(String))
# end::routes[]
