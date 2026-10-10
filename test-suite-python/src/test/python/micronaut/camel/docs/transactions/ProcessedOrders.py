# tag::repository[]
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory
from org.apache.camel.processor.idempotent.jdbc import JdbcMessageIdRepository
from org.springframework.jdbc.datasource import DataSourceTransactionManager
from org.springframework.transaction.support import TransactionTemplate


@Factory
class ProcessedOrders:

    @Singleton
    @Named("processedOrders")
    def processed_orders(self, transaction_manager: DataSourceTransactionManager) -> JdbcMessageIdRepository:  # <1>
        return JdbcMessageIdRepository(
            transaction_manager.getDataSource(),  # <2>
            TransactionTemplate(transaction_manager),  # <3>
            "orders"
        )
# end::repository[]
