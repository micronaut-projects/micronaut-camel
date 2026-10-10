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

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;

@Singleton
@Requires(env = "example-quartz")
public class QuartzRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // the cron expression is a placeholder, defaulting to the original one, so the test can fire it every second
        from("quartz://myGroup/myTimerName?cron={{quartz.cron:0/5+*+*+*+*+?}}")
            .setBody(constant("Test"))
            .to("log:info");
    }

}
