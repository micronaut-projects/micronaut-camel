# tag::repository[]
from typing import Annotated

from io.micronaut.camel.cache import MicronautCacheAggregationRepository
from io.micronaut.camel.json import JsonExchangeCodec
from jakarta.inject import Named, Singleton
from micronaut.cache import SyncCache
from micronaut.context.annotation import Factory
from org.apache.camel.spi import AggregationRepository


@Factory
class BasketRepositories:

    @Singleton
    @Named("baskets")
    def baskets(self,
                cache: Annotated[SyncCache, Named("baskets")],  # <1>
                codec: JsonExchangeCodec) -> AggregationRepository:  # <2>
        return MicronautCacheAggregationRepository(cache, codec)
# end::repository[]
