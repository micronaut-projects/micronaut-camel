# tag::routes[]
from jakarta.inject import Singleton
from java.lang import String
from micronaut.camel.docs.http.Book import Book
from org.apache.camel import Exchange
from org.apache.camel.builder import RouteBuilder
from org.apache.camel.model.rest import RestBindingMode


@Singleton
class BookRoutes(RouteBuilder):

    def __init__(self):
        self.books: dict[str, Book] = {}

    def configure(self) -> None:
        self.restConfiguration().bindingMode(RestBindingMode.json)  # <1>

        (self.rest("/books")
            .post().type(Book).outType(Book).to("direct:saveBook")  # <2>
            .get("/{isbn}").outType(Book).to("direct:findBook"))  # <3>

        (self.from_("direct:saveBook")
            .process(self.save_book)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, self.constant(201)))  # <4>

        (self.from_("direct:findBook")
            .process(self.find_book))

    def save_book(self, exchange: Exchange) -> None:
        book = exchange.getMessage().getBody(Book)
        self.books[book.isbn] = book

    def find_book(self, exchange: Exchange) -> None:
        isbn = exchange.getMessage().getHeader("isbn", String)
        exchange.getMessage().setBody(self.books.get(isbn))
# end::routes[]
