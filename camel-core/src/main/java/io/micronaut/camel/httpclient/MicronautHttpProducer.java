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

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ObjectHelper;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Sends the message as an HTTP request with the Micronaut HTTP client, and maps the response to the message:
 * the body as a byte array, the status to the {@code CamelHttpResponseCode} header and the headers of the
 * response to message headers.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class MicronautHttpProducer extends DefaultAsyncProducer {

    private static final Argument<byte[]> BYTES = Argument.of(byte[].class);

    MicronautHttpProducer(MicronautHttpEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MicronautHttpEndpoint getEndpoint() {
        return (MicronautHttpEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        MutableHttpRequest<?> request;
        try {
            request = createRequest(exchange);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        getEndpoint().client().toAsync().exchange(request, BYTES, BYTES).whenComplete((response, error) -> {
            try {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof HttpClientResponseException e) {
                        readResponse(exchange, request, e.getResponse());
                    } else {
                        exchange.setException(cause);
                    }
                } else {
                    readResponse(exchange, request, response);
                }
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                callback.done(false);
            }
        });
        return false;
    }

    private MutableHttpRequest<?> createRequest(Exchange exchange) throws Exception {
        Message message = exchange.getMessage();
        MicronautHttpEndpoint endpoint = getEndpoint();
        byte[] body = message.getBody() == null ? null : message.getMandatoryBody(byte[].class);
        String method = message.getHeader(Exchange.HTTP_METHOD, String.class);
        if (method == null) {
            method = endpoint.getHttpMethod();
        }
        if (method == null) {
            method = body == null ? "GET" : "POST";
        }
        StringBuilder uri = new StringBuilder(endpoint.getPath());
        String path = message.getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && !path.isEmpty()) {
            if (uri.length() > 0 && uri.charAt(uri.length() - 1) == '/' && path.startsWith("/")) {
                uri.append(path, 1, path.length());
            } else {
                if (!path.startsWith("/") && (uri.length() == 0 || uri.charAt(uri.length() - 1) != '/')) {
                    uri.append('/');
                }
                uri.append(path);
            }
        }
        if (uri.length() == 0) {
            uri.append('/');
        }
        String query = message.getHeader(Exchange.HTTP_QUERY, String.class);
        if (query == null) {
            query = endpoint.getQuery();
        }
        if (query != null && !query.isEmpty()) {
            uri.append('?').append(query);
        }
        MutableHttpRequest<Object> request = HttpRequest.create(HttpMethod.parse(method.toUpperCase(java.util.Locale.ROOT)), uri.toString());
        HeaderFilterStrategy filter = endpoint.getHeaderFilterStrategy();
        for (Map.Entry<String, Object> header : message.getHeaders().entrySet()) {
            Iterator<?> values = ObjectHelper.createIterator(header.getValue(), null, true);
            while (values.hasNext()) {
                Object value = values.next();
                if (value != null && !filter.applyFilterToCamelHeaders(header.getKey(), value, exchange)) {
                    String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
                    if (text != null) {
                        request.getHeaders().add(header.getKey(), text);
                    }
                }
            }
        }
        if (body != null && request.getMethod().permitsRequestBody()) {
            String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
            if (contentType != null) {
                request.contentType(contentType);
            }
            request.body(body);
        }
        return request;
    }

    private void readResponse(Exchange exchange, HttpRequest<?> request, HttpResponse<?> response) throws Exception {
        int status = response.code();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.getHeaders().forEach((name, values) -> headers.put(name, values));
        byte[] body = response.getBody(byte[].class).orElse(null);
        if (status >= 300 && getEndpoint().isThrowExceptionOnFailure()) {
            String text = body == null ? null : new String(body, StandardCharsets.UTF_8);
            exchange.setException(new MicronautHttpOperationFailedException(
                getEndpoint().getClientId() + request.getUri(), status, response.reason(), text, headers));
            return;
        }
        Message message = exchange.getMessage();
        HeaderFilterStrategy filter = getEndpoint().getHeaderFilterStrategy();
        headers.forEach((name, values) -> {
            Object value = values.size() == 1 ? values.get(0) : values;
            if (!filter.applyFilterToExternalHeaders(name, value, exchange)) {
                message.setHeader(name, value);
            }
        });
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, status);
        message.setHeader(Exchange.HTTP_RESPONSE_TEXT, response.reason());
        response.getContentType().ifPresent(type -> message.setHeader(Exchange.CONTENT_TYPE, type.toString()));
        message.setBody(body);
    }

    @Override
    public String toString() {
        return "MicronautHttpProducer[" + getEndpoint().getClientId() + getEndpoint().getPath() + "]";
    }
}
