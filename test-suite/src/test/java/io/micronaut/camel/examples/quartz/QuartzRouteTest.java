/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.camel.examples.quartz;

import io.micronaut.camel.test.MockEndpoints;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * Ports the {@code quartz} example of camel-spring-boot-examples, which has no test.
 * <p>
 * The route is a Micronaut singleton. Its cron expression became a placeholder whose default is the
 * original expression (every 5 seconds); the test configuration fires it every second.
 * {@code camel.main.run-controller} and {@code camel.main.duration-max-seconds} were dropped: Micronaut
 * owns the application lifecycle.
 */
@MicronautTest(environments = "example-quartz")
@MockEndpoints("log:info")
class QuartzRouteTest {

    @Inject
    CamelContext camelContext;

    @Test
    void quartzTriggersTheRoute() throws Exception {
        MockEndpoint log = camelContext.getEndpoint("mock:log:info", MockEndpoint.class);
        log.expectedMinimumMessageCount(1);
        log.allMessages().body().isEqualTo("Test");

        log.setResultWaitTime(TimeUnit.SECONDS.toMillis(10));
        log.assertIsSatisfied();
    }
}
