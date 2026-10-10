from typing import Annotated

from io.micronaut.camel.testservices import DocsServices
from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.camel import FluentProducerTemplate
from org.junit.jupiter.api import AfterAll, Test
from org.springframework.data.redis.core import RedisTemplate


# the docs-redis environment starts a Redis server and configures Micronaut Redis with it
# (see io.micronaut.camel.testservices.DocsServices)
@MicronautTest(environments=["docs-redis"])
class GreetingCacheRoutesTest:

    producer: Annotated[FluentProducerTemplate, Inject]
    redis_template: Annotated[RedisTemplate, Inject]

    @AfterAll
    @staticmethod
    def stop_redis() -> None:
        DocsServices.stopRedis()

    @Test
    def routes_use_the_redis_of_the_application(self) -> None:
        self.producer.to("direct:remember-greeting").withHeader("name", "fred").withBody("Hello Fred").send()

        assert self.producer.to("direct:recall-greeting").withHeader("name", "fred").request(String) == "Hello Fred"
        assert self.redis_template.opsForValue().get("greeting:fred") == "Hello Fred"
