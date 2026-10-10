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
package io.micronaut.camel.json;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A Camel JSON data format on the Micronaut {@link JsonMapper}: Micronaut Serialization, or Jackson
 * with micronaut-jackson-databind. With Micronaut Serialization, the types are introspected at
 * compile time ({@code @Serdeable}) instead of by reflection. Use it with
 * {@code .marshal().custom("micronaut-json")}, or for the REST DSL with
 * {@code camel.rest.json-data-format=micronaut-json}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautJsonDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    /**
     * The name of the data format.
     */
    public static final String NAME = "micronaut-json";

    private @Nullable CamelContext camelContext;
    private @Nullable JsonMapper jsonMapper;
    private @Nullable Class<?> unmarshalType;
    private boolean useList;

    /**
     * Creates the data format with the {@link JsonMapper} bean.
     */
    public MicronautJsonDataFormat() {
    }

    /**
     * @param jsonMapper The JSON mapper
     */
    public MicronautJsonDataFormat(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String getDataFormatName() {
        return NAME;
    }

    /**
     * @return The type to unmarshal to
     */
    public @Nullable Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * @param unmarshalType The type to unmarshal to, a map when not set
     */
    public void setUnmarshalType(@Nullable Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    /**
     * @return Whether to unmarshal a list of the unmarshal type
     */
    public boolean isUseList() {
        return useList;
    }

    /**
     * @param useList Whether to unmarshal a list of the unmarshal type
     */
    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        mapper().writeValue(stream, graph);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Class<?> type = unmarshalType != null ? unmarshalType : Object.class;
        Argument<?> argument = useList ? Argument.listOf(type) : Argument.of(type);
        return mapper().readValue(stream, argument);
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() {
        mapper();
    }

    private JsonMapper mapper() {
        if (jsonMapper == null) {
            if (camelContext == null) {
                throw new IllegalStateException("The Camel context is not set");
            }
            jsonMapper = camelContext.getRegistry().findSingleByType(JsonMapper.class);
            if (jsonMapper == null) {
                throw new IllegalStateException("No JsonMapper bean: add micronaut-serde-jackson or micronaut-jackson-databind");
            }
        }
        return jsonMapper;
    }
}
