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
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.jspecify.annotations.Nullable;

/**
 * An endpoint of the {@code micronaut-http} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@UriEndpoint(scheme = MicronautHttpComponent.SCHEME, title = "Micronaut HTTP Client", syntax = "micronaut-http:serviceId/path",
    producerOnly = true, category = {Category.HTTP})
public class MicronautHttpEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private final String clientId;
    private final String path;
    private @Nullable String query;
    private @Nullable String httpMethod;
    private boolean throwExceptionOnFailure = true;
    private HeaderFilterStrategy headerFilterStrategy = defaultHeaderFilterStrategy();
    private volatile @Nullable HttpClient client;

    /**
     * @param uri       The URI
     * @param component The component
     * @param clientId  The service ID or URL
     * @param path      The path of the request
     */
    MicronautHttpEndpoint(String uri, MicronautHttpComponent component, String clientId, String path) {
        super(uri, component);
        this.clientId = clientId;
        this.path = path;
    }

    @Override
    public MicronautHttpComponent getComponent() {
        return (MicronautHttpComponent) super.getComponent();
    }

    /**
     * @return The service ID or URL
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @return The path of the requests, to which the {@code CamelHttpPath} header is appended
     */
    public String getPath() {
        return path;
    }

    /**
     * @return The query of the requests, unless the {@code CamelHttpQuery} header sets one
     */
    public @Nullable String getQuery() {
        return query;
    }

    /**
     * @param query The query of the requests, unless the {@code CamelHttpQuery} header sets one
     */
    public void setQuery(@Nullable String query) {
        this.query = query;
    }

    /**
     * @return The HTTP method, unless the {@code CamelHttpMethod} header sets one
     */
    public @Nullable String getHttpMethod() {
        return httpMethod;
    }

    /**
     * @param httpMethod The HTTP method, unless the {@code CamelHttpMethod} header sets one. Defaults to
     *                   {@code POST} when the message has a body, {@code GET} otherwise.
     */
    public void setHttpMethod(@Nullable String httpMethod) {
        this.httpMethod = httpMethod;
    }

    /**
     * @return Whether a response with an error status fails the exchange
     */
    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * @param throwExceptionOnFailure Whether a response with an error status fails the exchange with a
     *                                {@link MicronautHttpOperationFailedException}. Defaults to {@code true}.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    /**
     * @return The HTTP client of the service or URL
     */
    HttpClient client() {
        HttpClient result = client;
        if (result == null) {
            result = getComponent().client(clientId);
            client = result;
        }
        return result;
    }

    @Override
    public Producer createProducer() {
        return new MicronautHttpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("The micronaut-http component has no consumer: " + getEndpointUri());
    }

    @Override
    public boolean isLenientProperties() {
        // the parameters that are not options are the query of the request
        return true;
    }

    private static HeaderFilterStrategy defaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
        strategy.setOutFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
        strategy.setInFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
        strategy.setLowerCase(true);
        // headers that the client sets from the request itself
        strategy.getOutFilter().addAll(java.util.List.of("content-length", "content-type", "host", "connection",
            "transfer-encoding", "keep-alive", "upgrade", "te", "trailer", "proxy-connection", "accept-encoding"));
        return strategy;
    }
}
