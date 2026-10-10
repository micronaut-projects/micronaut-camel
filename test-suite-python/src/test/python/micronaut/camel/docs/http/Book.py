# tag::book[]
from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Book:
    isbn: str
    title: str
# end::book[]
