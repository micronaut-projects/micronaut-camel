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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "PlatformHttpTest")
class PlatformHttpTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    CamelContext camelContext;

    @Inject
    MicronautPlatformHttpEngine engine;

    @Test
    void theComponentUsesTheMicronautEngine() {
        PlatformHttpComponent component = camelContext.getComponent("platform-http", PlatformHttpComponent.class);
        assertSame(engine, component.getEngine());
        assertTrue(engine.getServerPort() > 0);
    }

    @Test
    void servesAPlatformHttpRoute() {
        HttpResponse<String> response = client().exchange(HttpRequest.GET("/hello"), String.class);

        assertEquals(HttpStatus.OK, response.status());
        assertEquals("Hello World", response.body());
        assertEquals("text/plain", response.getContentType().map(MediaType::getName).orElse(null));
    }

    @Test
    void pathParametersBecomeHeaders() {
        assertEquals("user 42 of team a b", client().retrieve("/teams/a%20b/users/42"));
    }

    @Test
    void queryParametersBecomeHeaders() {
        HttpResponse<String> response = client().exchange(HttpRequest.GET("/greet?name=Fred&other=Barney"), String.class);

        assertEquals("Hello Fred and Barney", response.body());
        // without a Content-Type header, text is sent as text/plain
        assertEquals("text/plain;charset=UTF-8", response.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void httpHeadersDescribeTheRequest() {
        String described = client().retrieve(HttpRequest.GET("/describe/me?x=1&y=2").header("X-Trace", "abc"));

        assertEquals("GET /describe/me x=1&y=2 /describe/me abc", described);
    }

    @Test
    void postedBodyIsEchoed() {
        HttpResponse<String> response = client().exchange(
            HttpRequest.POST("/echo", "{\"name\":\"Fred\"}").contentType(MediaType.APPLICATION_JSON_TYPE), String.class);

        assertEquals("{\"name\":\"Fred\"}", response.body());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().orElse(null));
    }

    @Test
    void formBodyIsAMapAndHeaders() {
        String form = client().retrieve(HttpRequest.POST("/form", "a=1&b=x%20y")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        assertEquals("{a=1, b=x y} 1", form);
    }

    @Test
    void responseCodeAndHeadersComeFromTheMessage() {
        HttpResponse<String> response = client().exchange(HttpRequest.POST("/created", "thing"), String.class);

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("created thing", response.body());
        assertEquals("abc", response.header("X-Custom"));
        // Camel headers and request headers are not copied to the response
        assertNull(response.header(Exchange.HTTP_METHOD));
        assertNull(response.header("X-Request"));
    }

    @Test
    void bodyCanBeBytesOrAStream() {
        HttpResponse<String> response = client().exchange(HttpRequest.GET("/bytes"), String.class);
        assertEquals("bytes", response.body());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, response.getContentType().orElse(null));
        assertEquals("stream", client().retrieve("/stream"));
    }

    @Test
    void emptyBodyIsNoContent() {
        HttpResponse<String> response = client().exchange(HttpRequest.GET("/empty"), String.class);

        assertEquals(HttpStatus.NO_CONTENT, response.status());
    }

    @Test
    void unknownPathIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> client().retrieve("/missing"));
    }

    @Test
    void wrongMethodIsNotAllowed() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client().retrieve(HttpRequest.POST("/hello", "x")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, e.getStatus());
        assertEquals("GET", e.getResponse().header(HttpHeaders.ALLOW));
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> client().retrieve(
            HttpRequest.POST("/json-only", "<a/>").contentType(MediaType.APPLICATION_XML_TYPE)));
        assertEquals("ok", client().retrieve(
            HttpRequest.POST("/json-only", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
    }

    @Test
    void exceptionIsAnInternalServerError() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client().retrieve("/boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
        assertEquals("", e.getResponse().getBody(String.class).orElse(""));
    }

    @Test
    void unmutedExceptionReturnsTheStackTrace() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client().retrieve("/boom-loud"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
        assertTrue(e.getResponse().getBody(String.class).orElse("").contains("IllegalStateException: Kaboom"));
    }

    @Test
    void controllersAndCamelRoutesShareTheServer() {
        assertEquals("from controller", client().retrieve("/shared"));
        assertEquals("from camel", client().retrieve(HttpRequest.POST("/shared", "x")));
        // a controller wins over a Camel route on the same path and method
        assertEquals("controller wins", client().retrieve("/both"));
    }

    @Test
    void serverFiltersApplyToCamelRoutes() {
        HttpResponse<String> response = client().exchange(HttpRequest.GET("/hello"), String.class);
        assertEquals("true", response.header("X-Filtered"));

        assertStatus(HttpStatus.UNAUTHORIZED, () -> client().retrieve("/secured"));
        assertEquals("secret", client().retrieve(HttpRequest.GET("/secured").header("X-Token", "letmein")));
    }

    @Test
    void stoppingARouteRemovesItsPath() throws Exception {
        assertEquals("on", client().retrieve("/toggle"));

        camelContext.getRouteController().stopRoute("toggle");
        assertStatus(HttpStatus.NOT_FOUND, () -> client().retrieve("/toggle"));

        camelContext.getRouteController().startRoute("toggle");
        assertEquals("on", client().retrieve("/toggle"));
    }

    @Test
    void prefixMatchingServesSubPaths() {
        assertEquals("/files/a/b.txt", client().retrieve("/files/a/b.txt"));
        assertEquals("/files", client().retrieve("/files"));
    }

    @Test
    void slowRoutesTimeOut() {
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> client().retrieve("/slow"));
    }

    @Test
    void streamedRequestBodyIsAnInputStream() {
        assertEquals("InputStream=streamed", client().retrieve(HttpRequest.POST("/streamed", "streamed")));
    }

    @Test
    void cookieHandlerReadsAndAddsCookies() {
        HttpResponse<String> response = client().exchange(
            HttpRequest.GET("/cookies").cookie(io.micronaut.http.cookie.Cookie.of("flavour", "oat")), String.class);

        assertEquals("oat", response.body());
        assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).startsWith("visited=yes"));
    }

    @Test
    void requestContextIsPropagated() {
        assertEquals("/context", client().retrieve("/context"));
    }

    private BlockingHttpClient client() {
        return httpClient.toBlocking();
    }

    private static void assertStatus(HttpStatus status, Executable executable) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, executable);
        assertEquals(status, e.getStatus());
    }

    @Singleton
    @Requires(property = "spec.name", value = "PlatformHttpTest")
    static class Routes extends RouteBuilder {

        @Override
        public void configure() {
            from("platform-http:/hello?httpMethodRestrict=GET")
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody(constant("Hello World"));

            from("platform-http:/teams/{team}/users/{id}")
                .setBody(simple("user ${header.id} of team ${header.team}"));

            from("platform-http:/greet")
                .setBody(simple("Hello ${header.name} and ${header.other}"));

            from("platform-http:/describe/me")
                .setBody(simple("${header.CamelHttpMethod} ${header.CamelHttpPath} ${header.CamelHttpQuery} "
                    + "${header.CamelHttpUri} ${header.X-Trace}"));

            from("platform-http:/echo?httpMethodRestrict=POST")
                .convertBodyTo(String.class);

            from("platform-http:/form?httpMethodRestrict=POST")
                .setBody(simple("${body} ${header.a}"));

            from("platform-http:/created?httpMethodRestrict=POST")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201))
                .setHeader("X-Custom", constant("abc"))
                .setHeader("X-Request", constant("not copied"))
                .removeHeader("X-Request")
                .setBody(simple("created ${bodyAs(String)}"));

            from("platform-http:/bytes")
                .process(e -> e.getMessage().setBody("bytes".getBytes(StandardCharsets.UTF_8)));

            from("platform-http:/stream")
                .process(e -> e.getMessage().setBody(new ByteArrayInputStream("stream".getBytes(StandardCharsets.UTF_8))));

            from("platform-http:/empty")
                .setBody(constant(null));

            from("platform-http:/json-only?httpMethodRestrict=POST&consumes=application/json")
                .setBody(constant("ok"));

            from("platform-http:/boom")
                .throwException(new IllegalStateException("Kaboom"));

            from("platform-http:/boom-loud?muteException=false")
                .throwException(new IllegalStateException("Kaboom"));

            from("platform-http:/shared?httpMethodRestrict=POST")
                .setBody(constant("from camel"));

            from("platform-http:/both?httpMethodRestrict=GET")
                .setBody(constant("camel loses"));

            from("platform-http:/secured")
                .setBody(constant("secret"));

            from("platform-http:/toggle").routeId("toggle")
                .setBody(constant("on"));

            from("platform-http:/files?matchOnUriPrefix=true")
                .setBody(header(Exchange.HTTP_PATH));

            from("platform-http:/slow?requestTimeout=100")
                .delay(2000)
                .setBody(constant("too late"));

            from("platform-http:/streamed?useStreaming=true")
                .process(e -> {
                    Object body = e.getMessage().getBody();
                    String type = body instanceof java.io.InputStream ? "InputStream" : body.getClass().getSimpleName();
                    e.getMessage().setBody(type + "=" + e.getMessage().getBody(String.class));
                });

            from("platform-http:/cookies?useCookieHandler=true")
                .process(e -> {
                    org.apache.camel.component.platform.http.cookie.CookieHandler cookies =
                        e.getProperty(Exchange.COOKIE_HANDLER, org.apache.camel.component.platform.http.cookie.CookieHandler.class);
                    cookies.addCookie("visited", "yes");
                    e.getMessage().setBody(cookies.getCookieValue("flavour"));
                });

            from("platform-http:/context")
                .process(e -> e.getMessage().setBody(
                    io.micronaut.http.context.ServerRequestContext.currentRequest().map(HttpRequest::getPath).orElse("none")));
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "PlatformHttpTest")
    static class SharedController {

        @Get(uri = "/shared", produces = MediaType.TEXT_PLAIN)
        String shared() {
            return "from controller";
        }

        @Get(uri = "/both", produces = MediaType.TEXT_PLAIN)
        String both() {
            return "controller wins";
        }
    }

    @ServerFilter("/**")
    @Requires(property = "spec.name", value = "PlatformHttpTest")
    static class TokenFilter {

        @RequestFilter
        @Nullable HttpResponse<?> checkToken(HttpRequest<?> request) {
            if (!request.getPath().equals("/secured") || "letmein".equals(request.getHeaders().get("X-Token"))) {
                return null;
            }
            return HttpResponse.unauthorized();
        }

        @ResponseFilter
        void mark(MutableHttpResponse<?> response) {
            response.header("X-Filtered", "true");
        }
    }
}
