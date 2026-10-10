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
package io.micronaut.camel.examples.pojo;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.Consume;
import org.apache.camel.Produce;

@Singleton
@Requires(env = "example-pojo")
public class NumberPojo {

    @Produce("log:out")
    private MagicNumber magic;

    @Consume(value = "direct:numbers", predicate = "${body} < 100")
    public void lowNumber(int number) {
        magic.onMagicNumber("Got a low number " + number);
    }

    @Consume(value = "direct:numbers", predicate = "${body} >= 100")
    public void highNumber(int number) {
        magic.onMagicNumber("Got a high number " + number);
    }

}
