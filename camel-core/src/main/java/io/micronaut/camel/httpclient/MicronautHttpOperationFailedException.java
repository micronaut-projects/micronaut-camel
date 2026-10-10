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
package io.micronaut.camel.httpclient;

import org.apache.camel.CamelException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The exception of a {@code micronaut-http} request whose response has an error status, unless the endpoint
 * sets {@code throwExceptionOnFailure=false}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautHttpOperationFailedException extends CamelException {

    private final String uri;
    private final int statusCode;
    private final @Nullable String statusText;
    private final @Nullable String responseBody;
    private final Map<String, List<String>> responseHeaders;

    /**
     * @param uri             The URI of the request
     * @param statusCode      The status of the response
     * @param statusText      The reason phrase of the response
     * @param responseBody    The body of the response
     * @param responseHeaders The headers of the response
     */
    public MicronautHttpOperationFailedException(String uri, int statusCode, @Nullable String statusText,
                                                 @Nullable String responseBody, Map<String, List<String>> responseHeaders) {
        super("HTTP operation failed invoking " + uri + " with statusCode: " + statusCode);
        this.uri = uri;
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
    }

    /**
     * @return The URI of the request
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return The status of the response
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return The reason phrase of the response
     */
    public @Nullable String getStatusText() {
        return statusText;
    }

    /**
     * @return The body of the response
     */
    public @Nullable String getResponseBody() {
        return responseBody;
    }

    /**
     * @return The headers of the response
     */
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }
}
