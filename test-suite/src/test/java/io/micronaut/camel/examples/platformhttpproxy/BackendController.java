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
package io.micronaut.camel.examples.platformhttpproxy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for {@code http://httpbin.org}, the backend of the example: like httpbin's {@code /get}, it
 * describes the request it receives.
 */
@Controller("/backend")
@Requires(env = "example-platform-http-proxy")
public class BackendController {

    @Get("/get")
    public Map<String, Object> get(HttpRequest<?> request) {
        Map<String, Object> args = new LinkedHashMap<>();
        request.getParameters().forEach((name, values) -> args.put(name, values.get(0)));
        return Map.of(
            "args", args,
            "path", request.getPath(),
            "accept", request.getHeaders().get("Accept"));
    }
}
