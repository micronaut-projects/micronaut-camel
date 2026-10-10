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

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.URISupport;

import java.util.Map;

/**
 * The {@code micronaut-http} Camel component: a producer that sends the message as an HTTP request with the
 * Micronaut HTTP client, so that requests to a service ID use Micronaut's service discovery and load
 * balancing, the HTTP client configuration of the service ({@code micronaut.http.services.<id>.*}), client
 * filters and tracing.
 *
 * <p>The URI is a service ID and a path, {@code micronaut-http:inventory/stock}, or a URL,
 * {@code micronaut-http:https://example.com/stock}. Parameters that are not options of the endpoint are
 * added to the query of the request.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Named(MicronautHttpComponent.SCHEME)
@Requires(classes = HttpClientRegistry.class)
@Requires(beans = HttpClientRegistry.class)
public class MicronautHttpComponent extends DefaultComponent {

    /**
     * The scheme of the component.
     */
    public static final String SCHEME = "micronaut-http";

    private final HttpClientRegistry<HttpClient> clientRegistry;

    /**
     * @param clientRegistry The registry of the HTTP clients
     */
    public MicronautHttpComponent(HttpClientRegistry<HttpClient> clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String target = remaining;
        String clientId;
        String path;
        int schemeEnd = target.indexOf("://");
        if (schemeEnd > 0) {
            int pathStart = target.indexOf('/', schemeEnd + 3);
            clientId = pathStart < 0 ? target : target.substring(0, pathStart);
            path = pathStart < 0 ? "" : target.substring(pathStart);
        } else {
            while (target.startsWith("/")) {
                target = target.substring(1);
            }
            int pathStart = target.indexOf('/');
            clientId = pathStart < 0 ? target : target.substring(0, pathStart);
            path = pathStart < 0 ? "" : target.substring(pathStart);
        }
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("No service ID or URL in " + uri);
        }
        MicronautHttpEndpoint endpoint = new MicronautHttpEndpoint(uri, this, clientId, path);
        setProperties(endpoint, parameters);
        if (!parameters.isEmpty()) {
            // the remaining parameters are the query of the request
            endpoint.setQuery(URISupport.createQueryString(parameters));
            parameters.clear();
        }
        return endpoint;
    }

    /**
     * @param clientId The service ID or URL
     * @return The HTTP client of the service or URL
     */
    HttpClient client(String clientId) {
        return clientRegistry.getClient((HttpVersionSelection) null, clientId, null);
    }
}
