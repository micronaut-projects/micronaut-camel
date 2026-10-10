# tag::policies[]
from io.micronaut.camel.security import MicronautSecurityAuthorizationPolicy
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory
from org.apache.camel.spi import AuthorizationPolicy


@Factory
class SecurityPolicies:

    @Singleton
    @Named("adminPolicy")  # <1>
    def admin_policy(self) -> AuthorizationPolicy:
        return MicronautSecurityAuthorizationPolicy("ROLE_ADMIN")  # <2>
# end::policies[]
