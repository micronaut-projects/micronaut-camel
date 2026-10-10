# tag::policy[]
from io.micronaut.camel.transaction import MicronautTransactionPolicy
from jakarta.inject import Named, Singleton
from java.time import Duration
from micronaut.context.annotation import Factory
from micronaut.transaction import TransactionOperations
from micronaut.transaction.support import DefaultTransactionDefinition


@Factory
class TransactionPolicies:

    @Singleton
    @Named("readOnly")  # <1>
    def read_only(self, transaction_operations: TransactionOperations) -> MicronautTransactionPolicy:
        definition = DefaultTransactionDefinition()
        definition.setReadOnly(True)
        definition.setTimeout(Duration.ofSeconds(5))
        return MicronautTransactionPolicy(transaction_operations, definition)  # <2>
# end::policy[]
