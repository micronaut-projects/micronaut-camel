# tag::repository[]
from io.micronaut.camel.jdbc import JsonJdbcCamelCodec
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory
from org.apache.camel.processor.aggregate.jdbc import JdbcAggregationRepository
from org.springframework.jdbc.datasource import DataSourceTransactionManager


@Factory
class OrderAggregations:

    @Singleton
    @Named("orderAggregations")
    def order_aggregations(self, transaction_manager: DataSourceTransactionManager,
                           codec: JsonJdbcCamelCodec) -> JdbcAggregationRepository:  # <1>
        repository = JdbcAggregationRepository(
            transaction_manager, "aggregation", transaction_manager.getDataSource())
        repository.setJdbcCamelCodec(codec)  # <2>
        return repository
# end::repository[]
