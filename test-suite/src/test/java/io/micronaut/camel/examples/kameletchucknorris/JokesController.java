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
package io.micronaut.camel.examples.kameletchucknorris;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Map;

/**
 * Stands in for {@code https://api.chucknorris.io/jokes/random}, so the test does not need the network.
 */
@Controller("/jokes")
@Requires(env = "example-kamelet-chucknorris")
class JokesController {

    static final String JOKE = "Chuck Norris can unit test an entire application with a single assert.";

    @Get("/random")
    Map<String, String> random() {
        return Map.of("id", "1", "value", JOKE);
    }
}
