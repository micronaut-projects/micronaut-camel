# tag::ticket[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Email, NotBlank
from micronaut.core.annotation import Introspected


@Introspected  # <1>
@dataclass
class Ticket:
    title: Annotated[str, NotBlank]
    reporter: Annotated[str, Email]
# end::ticket[]
