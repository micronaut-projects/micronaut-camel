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
package io.micronaut.camel.http;

/**
 * The headers that the Micronaut platform-http engine adds to the Camel message, in addition to the
 * standard {@code CamelHttp*} headers.
 *
 * <p>Like every header whose name starts with {@code Camel}, they are never copied to the HTTP response.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public final class MicronautPlatformHttpConstants {

    /**
     * The Micronaut {@link io.micronaut.http.HttpRequest} being served.
     */
    public static final String HTTP_REQUEST = "CamelMicronautHttpRequest";

    /**
     * The {@link java.net.InetSocketAddress} of the client.
     */
    public static final String REMOTE_ADDRESS = "CamelMicronautHttpRemoteAddress";

    /**
     * The {@link java.net.InetSocketAddress} of the server that received the request.
     */
    public static final String LOCAL_ADDRESS = "CamelMicronautHttpLocalAddress";

    private MicronautPlatformHttpConstants() {
    }
}
