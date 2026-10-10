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
import jakarta.inject.Singleton;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * Reverse proxy route that logs headers around the backend call.
 *
 * Requests are accepted under the {@code /reverse-proxy} path and forwarded to the fixed backend
 * configured via {@code reverse-proxy.target-base-uri}. The {@code CamelHttpPath} header is made relative
 * to the {@code /reverse-proxy} consumer path, so that, for example, {@code /reverse-proxy/get} is
 * forwarded to {@code <target-base-uri>/get}. The remaining path and query string are then appended
 * automatically by the http producer's {@code bridgeEndpoint} mode.
 *
 * NOTE: the consumer path must not be the literal string "proxy" (e.g. "platform-http:proxy").
 * That exact path is a reserved marker in camel-platform-http for a Host-header-based forward proxy,
 * which only the Vert.x platform-http engine implements.
 */
@Singleton
@Requires(env = "example-platform-http-proxy")
public class CamelRouter extends RouteBuilder {

    private static final String PREFIX = "/reverse-proxy";

    @Override
    public void configure() throws Exception {

        // @formatter:off
        from("platform-http:reverse-proxy?matchOnUriPrefix=true")
                .routeId("reverse-proxy")
                // what the stripUriPrefix option of Camel 4.23 does
                .process(exchange -> {
                    String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
                    exchange.getMessage().setHeader(Exchange.HTTP_PATH, path.substring(PREFIX.length()));
                })
                .wireTap("direct:request")
                .log("calling ${properties:reverse-proxy.target-base-uri}${headers." + Exchange.HTTP_PATH + "}")
                .to("{{reverse-proxy.target-base-uri}}?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .wireTap("direct:response");

        from("direct:request")
                .routeId("header-request")
                .log("${headers}");
        from("direct:response")
                .routeId("header-response")
                .log("${headers}");
        // @formatter:on
    }
}
