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
package io.micronaut.camel.jsonschema;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.support.ResourceSupport;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Resolves the {@code jsonschema:} resources of Camel, such as {@code json-validator:jsonschema:Order}, to the
 * JSON schema that Micronaut JSON Schema generated at build time for a {@code @JsonSchema} type: by the fully
 * qualified or simple name of the type, or by the name of the schema, such as {@code order} for
 * {@code META-INF/schemas/order.schema.json}. Camel's resource loader looks the resolver up in the registry,
 * as {@code resource-loader-jsonschema}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Named(JsonSchemaResourceResolver.NAME)
@Internal
@Requires(beans = JsonSchemaTypes.class)
final class JsonSchemaResourceResolver implements ResourceResolver {

    /**
     * The scheme of the resources.
     */
    static final String SCHEME = "jsonschema";
    /**
     * The name of the resolver in the registry, which Camel's resource loader looks up for the scheme.
     */
    static final String NAME = "resource-loader-" + SCHEME;

    private final JsonSchemaTypes types;
    private final ResourceLoader resourceLoader;
    private @Nullable CamelContext camelContext;

    /**
     * @param types       The {@code @JsonSchema} types
     * @param environment The environment, whose class loader has the schemas
     */
    JsonSchemaResourceResolver(JsonSchemaTypes types, Environment environment) {
        this.types = types;
        this.resourceLoader = ClassPathResourceLoader.defaultLoader(environment.getClassLoader());
    }

    @Override
    public String getSupportedScheme() {
        return SCHEME;
    }

    @Override
    public Resource resolve(String location) {
        String name = location.startsWith(SCHEME + ":") ? location.substring(SCHEME.length() + 1) : location;
        return new SchemaResource(location, types.resource(name), resourceLoader);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void start() {
        // nothing to start
    }

    @Override
    public void stop() {
        // nothing to stop
    }

    /**
     * A schema on the classpath.
     */
    private static final class SchemaResource extends ResourceSupport {

        private final String path;
        private final ResourceLoader resourceLoader;

        SchemaResource(String location, String path, ResourceLoader resourceLoader) {
            super(SCHEME, location);
            this.path = path;
            this.resourceLoader = resourceLoader;
        }

        @Override
        public boolean exists() {
            return resourceLoader.getResource(path).isPresent();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return resourceLoader.getResourceAsStream(path)
                .orElseThrow(() -> new FileNotFoundException("No JSON schema " + path + " for " + getLocation()));
        }

        @Override
        public @Nullable URL getURL() {
            return resourceLoader.getResource(path).orElse(null);
        }

        @Override
        public URI getURI() {
            URL url = getURL();
            try {
                return url == null ? URI.create("classpath:" + path) : url.toURI();
            } catch (URISyntaxException e) {
                return URI.create("classpath:" + path);
            }
        }
    }
}
