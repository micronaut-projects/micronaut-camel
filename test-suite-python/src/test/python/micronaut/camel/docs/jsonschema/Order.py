# tag::order[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import NotBlank, Positive
from micronaut.jsonschema import JsonSchema
from micronaut.serde.annotation import Serdeable


@JsonSchema  # <1>
@Serdeable  # <2>
@dataclass
class Order:
    id: Annotated[str, NotBlank]  # <3>
    quantity: Annotated[int, Positive]
# end::order[]
