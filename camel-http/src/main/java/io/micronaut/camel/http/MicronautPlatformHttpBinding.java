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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.cookie.CookieConfiguration;
import org.apache.camel.component.platform.http.cookie.CookieHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.http.HttpUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.apache.camel.util.CollectionHelper.appendEntry;

/**
 * Maps a Micronaut request to a Camel message, and the Camel message back to a Micronaut response, as
 * the other platform-http engines do.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class MicronautPlatformHttpBinding {

    private static final String CONTENT_TYPE_ON_EXCEPTION = "text/plain; charset=utf-8";
    private static final String COOKIES_TO_ADD = MicronautPlatformHttpBinding.class.getName() + ".cookies";
    private static final Set<String> BODILESS_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final PlatformHttpEndpoint endpoint;
    private final PlatformHttpPath httpPath;

    /**
     * @param endpoint The endpoint
     * @param httpPath The path of the consumer
     */
    MicronautPlatformHttpBinding(PlatformHttpEndpoint endpoint, PlatformHttpPath httpPath) {
        this.endpoint = endpoint;
        this.httpPath = httpPath;
    }

    /**
     * Populates the message of a new exchange from the request.
     *
     * @param exchange       The exchange
     * @param request        The request
     * @param path           The request path without the server context path
     * @param pathParameters The values of the placeholders of the consumer path
     * @param body           The request body: a byte array, an input stream or {@code null}
     */
    void readRequest(Exchange exchange, HttpRequest<?> request, String path,
                     Map<String, String> pathParameters, @Nullable Object body) {
        Message message = exchange.getIn();
        Map<String, Object> headers = message.getHeaders();
        HeaderFilterStrategy filter = endpoint.getHeaderFilterStrategy();

        HttpHeaders requestHeaders = request.getHeaders();
        for (String name : requestHeaders.names()) {
            for (String value : requestHeaders.getAll(name)) {
                if (!filter.applyFilterToExternalHeaders(name, value, exchange)) {
                    appendEntry(headers, name, value);
                }
            }
        }
        String authorization = requestHeaders.get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.trim().startsWith("Basic")
            && !filter.applyFilterToExternalHeaders(Exchange.AUTHENTICATION, "Basic", exchange)) {
            appendEntry(headers, Exchange.AUTHENTICATION, "Basic");
        }
        HttpParameters parameters = request.getParameters();
        for (String name : parameters.names()) {
            for (String value : parameters.getAll(name)) {
                if (!filter.applyFilterToExternalHeaders(name, value, exchange)) {
                    appendEntry(headers, name, value);
                }
            }
        }
        pathParameters.forEach((name, value) -> appendEntry(headers, name, value));

        URI uri = request.getUri();
        String rawQuery = uri.getRawQuery();
        headers.put(Exchange.HTTP_METHOD, request.getMethodName());
        headers.put(Exchange.HTTP_PATH, path);
        headers.put(Exchange.HTTP_URI, uri.getRawPath());
        headers.put(Exchange.HTTP_URL, absoluteUrl(request, uri));
        headers.put(Exchange.HTTP_QUERY, rawQuery);
        headers.put(Exchange.HTTP_RAW_QUERY, rawQuery);
        request.getContentType().ifPresent(contentType ->
            headers.computeIfAbsent(Exchange.CONTENT_TYPE, k -> contentType.toString()));
        Charset charset = request.getContentType().flatMap(MediaType::getCharset).orElse(null);
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset.name());
            headers.put(Exchange.HTTP_CHARACTER_ENCODING, charset.name());
        }
        headers.put(MicronautPlatformHttpConstants.HTTP_REQUEST, request);
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            headers.put(MicronautPlatformHttpConstants.REMOTE_ADDRESS, remoteAddress);
        }
        InetSocketAddress serverAddress = request.getServerAddress();
        if (serverAddress != null) {
            headers.put(MicronautPlatformHttpConstants.LOCAL_ADDRESS, serverAddress);
        }
        if (endpoint.isUseCookieHandler()) {
            exchange.setProperty(Exchange.COOKIE_HANDLER, new MicronautCookieHandler(request, exchange));
        }

        if (body instanceof byte[] bytes && isFormUrlEncoded(request)) {
            readForm(exchange, message, bytes, charset);
        } else {
            message.setBody(body);
        }
    }

    /**
     * Creates the response from the message of a processed exchange.
     *
     * @param exchange The exchange
     * @return The response
     * @throws IOException if the body cannot be read
     * @throws NoTypeConversionAvailableException if the body cannot be converted to bytes
     */
    MutableHttpResponse<?> writeResponse(Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        Message message = exchange.getMessage();
        Exception exception = exchange.getException();
        Object body;
        if (exception != null) {
            // do not include the stacktrace unless asked to
            body = endpoint.isMuteException() ? null : ExceptionHelper.stackTraceToString(exception);
            message.setHeader(Exchange.CONTENT_TYPE, CONTENT_TYPE_ON_EXCEPTION);
        } else {
            body = message.getBody();
        }
        int code = HttpUtil.determineResponseCode(exchange, body);
        MutableHttpResponse<Object> response = HttpResponse.status(code, reason(code));

        HeaderFilterStrategy filter = endpoint.getHeaderFilterStrategy();
        TypeConverter typeConverter = exchange.getContext().getTypeConverter();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String name = entry.getKey();
            // the path parameters are request data that must not leak back to the caller
            if (httpPath.variables().contains(name) || isFramingHeader(name)) {
                continue;
            }
            Iterator<?> values = ObjectHelper.createIterator(entry.getValue(), null, true);
            HttpUtil.applyHeader(filter, exchange, values, typeConverter, name, (all, first) -> {
                if (all != null) {
                    all.forEach(v -> response.getHeaders().add(name, v));
                } else if (first != null) {
                    response.getHeaders().add(name, first);
                }
            });
        }
        String contentType = MessageHelper.getContentType(message);
        if (contentType == null && body != null) {
            // Micronaut would otherwise describe the body as JSON
            contentType = body instanceof String
                ? MediaType.TEXT_PLAIN + ";charset=" + ExchangeHelper.getCharsetName(exchange, true)
                : MediaType.APPLICATION_OCTET_STREAM;
        }
        if (contentType != null) {
            response.contentType(contentType);
        }
        @SuppressWarnings("unchecked")
        List<Cookie> cookies = exchange.getProperty(COOKIES_TO_ADD, List.class);
        if (cookies != null) {
            cookies.forEach(response::cookie);
        }
        byte[] bytes = toBytes(exchange, body);
        if (bytes != null && !HttpStatus.NO_CONTENT.equals(response.status())) {
            response.body(bytes);
        }
        return response;
    }

    private static byte @Nullable [] toBytes(Exchange exchange, @Nullable Object body)
        throws IOException, NoTypeConversionAvailableException {
        if (body == null) {
            return null;
        }
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        if (body instanceof String string) {
            return string.getBytes(ExchangeHelper.getCharsetName(exchange, true));
        }
        if (body instanceof ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);
            return bytes;
        }
        if (body instanceof InputStream stream) {
            // read now: the stream may be closed once the exchange is done
            try (stream) {
                return stream.readAllBytes();
            }
        }
        TypeConverter typeConverter = exchange.getContext().getTypeConverter();
        byte[] bytes = typeConverter.tryConvertTo(byte[].class, exchange, body);
        if (bytes != null) {
            return bytes;
        }
        try (InputStream stream = typeConverter.mandatoryConvertTo(InputStream.class, exchange, body)) {
            return stream.readAllBytes();
        }
    }

    private void readForm(Exchange exchange, Message message, byte[] bytes, @Nullable Charset charset) {
        Charset formCharset = charset != null ? charset : StandardCharsets.UTF_8;
        String form = new String(bytes, formCharset);
        HeaderFilterStrategy filter = endpoint.getHeaderFilterStrategy();
        Map<String, Object> values = new LinkedHashMap<>();
        for (String pair : form.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String name = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), formCharset);
            String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1), formCharset);
            if (!filter.applyFilterToExternalHeaders(name, value, exchange)) {
                appendEntry(message.getHeaders(), name, value);
                appendEntry(values, name, value);
            }
        }
        if (endpoint.isPopulateBodyWithForm() && !values.isEmpty()) {
            message.setBody(values);
        } else {
            message.setBody(bytes);
        }
    }

    private static boolean isFormUrlEncoded(HttpRequest<?> request) {
        return !BODILESS_METHODS.contains(request.getMethodName())
            && request.getContentType().map(MediaType.APPLICATION_FORM_URLENCODED_TYPE::matches).orElse(false);
    }

    private static boolean isFramingHeader(String name) {
        String lower = name.toLowerCase(Locale.ENGLISH);
        return "content-length".equals(lower) || "transfer-encoding".equals(lower) || "content-type".equals(lower);
    }

    private static String absoluteUrl(HttpRequest<?> request, URI uri) {
        String host = request.getHeaders().get(HttpHeaders.HOST);
        if (host == null) {
            InetSocketAddress server = request.getServerAddress();
            host = server.getHostString() + ":" + server.getPort();
        }
        return (request.isSecure() ? "https" : "http") + "://" + host + uri.getRawPath();
    }

    private static String reason(int code) {
        try {
            return HttpStatus.getDefaultReason(code);
        } catch (IllegalArgumentException e) {
            return String.valueOf(code);
        }
    }

    /**
     * The cookie handler of an exchange: reads the request cookies and adds cookies to the response.
     */
    private final class MicronautCookieHandler implements CookieHandler {

        private final HttpRequest<?> request;
        private final Exchange exchange;

        MicronautCookieHandler(HttpRequest<?> request, Exchange exchange) {
            this.request = request;
            this.exchange = exchange;
        }

        @Override
        public void addCookie(String name, String value) {
            CookieConfiguration configuration = endpoint.getCookieConfiguration();
            Cookie cookie = Cookie.of(name, value)
                .path(configuration.getCookiePath())
                .secure(configuration.isCookieSecure())
                .httpOnly(configuration.isCookieHttpOnly())
                .sameSite(SameSite.valueOf(configuration.getCookieSameSite().getValue()));
            if (configuration.getCookieDomain() != null) {
                cookie.domain(configuration.getCookieDomain());
            }
            if (configuration.getCookieMaxAge() != null) {
                cookie.maxAge(configuration.getCookieMaxAge());
            }
            responseCookies().add(cookie);
        }

        @Override
        public @Nullable String removeCookie(String name) {
            responseCookies().add(Cookie.of(name, "").path(endpoint.getCookieConfiguration().getCookiePath()).maxAge(0));
            return getCookieValue(name);
        }

        @Override
        public @Nullable String getCookieValue(String name) {
            return request.getCookies().findCookie(name).map(Cookie::getValue).orElse(null);
        }

        @SuppressWarnings("unchecked")
        private List<Cookie> responseCookies() {
            List<Cookie> cookies = exchange.getProperty(COOKIES_TO_ADD, List.class);
            if (cookies == null) {
                cookies = new ArrayList<>();
                exchange.setProperty(COOKIES_TO_ADD, cookies);
            }
            return cookies;
        }
    }
}
