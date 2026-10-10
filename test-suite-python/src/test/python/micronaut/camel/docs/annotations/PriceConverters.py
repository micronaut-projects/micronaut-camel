# tag::converters[]
from micronaut.camel.docs.annotations.Price import Price
from org.apache.camel import Converter


@Converter  # <1>
class PriceConverters:

    @Converter
    def to_price(self, value: str) -> Price:  # <2>
        item, amount = value.split("=")
        return Price(item, int(amount))
# end::converters[]
