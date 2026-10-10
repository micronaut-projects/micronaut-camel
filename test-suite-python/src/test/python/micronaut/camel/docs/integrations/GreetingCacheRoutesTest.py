from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.test.support import TestPropertyProvider
from org.apache.camel import FluentProducerTemplate
from org.junit.jupiter.api import AfterAll, Test, TestInstance
from org.springframework.data.redis.core import RedisTemplate
from org.testcontainers.containers import GenericContainer


# runs Redis with Testcontainers
@MicronautTest(environments=["docs-redis"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GreetingCacheRoutesTest(TestPropertyProvider):

    producer: Annotated[FluentProducerTemplate, Inject]
    redis_template: Annotated[RedisTemplate, Inject]

    def getProperties(self) -> dict[str, str]:
        self.redis = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
        self.redis.start()
        return {"redis.uri": f"redis://{self.redis.getHost()}:{self.redis.getMappedPort(6379)}"}

    @AfterAll
    def stop_redis(self) -> None:
        self.redis.stop()

    @Test
    def routes_use_the_redis_of_the_application(self) -> None:
        self.producer.to("direct:remember-greeting").withHeader("name", "fred").withBody("Hello Fred").send()

        assert self.producer.to("direct:recall-greeting").withHeader("name", "fred").request(String) == "Hello Fred"
        assert self.redis_template.opsForValue().get("greeting:fred") == "Hello Fred"
