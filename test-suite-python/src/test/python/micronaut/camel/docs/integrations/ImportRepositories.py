# tag::repository[]
from typing import Annotated

from io.micronaut.camel.cache import MicronautCacheIdempotentRepository
from jakarta.inject import Named, Singleton
from micronaut.cache import SyncCache
from micronaut.context.annotation import Factory
from org.apache.camel.spi import IdempotentRepository


@Factory
class ImportRepositories:

    @Singleton
    @Named("imports")
    def imports(self, cache: Annotated[SyncCache, Named("imports")]) -> IdempotentRepository:  # <1>
        return MicronautCacheIdempotentRepository(cache)
# end::repository[]
