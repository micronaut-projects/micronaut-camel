# tag::service[]
from jakarta.inject import Singleton


@Singleton
class PriceService:

    def price_of(self, item: str) -> int:
        return len(item) * 10
# end::service[]
