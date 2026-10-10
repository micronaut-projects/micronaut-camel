from typing import Annotated

from micronaut.http.annotation import Controller, Get, QueryValue


@Controller("/stock")
class InventoryController:

    @Get("/{item}")
    def stock(self, item: str, warehouse: Annotated[str, QueryValue]) -> str:
        return "12" if warehouse == "north" and item == "book" else "0"
