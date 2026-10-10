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
package io.micronaut.camel.objectstorage;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageOperations;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

/**
 * The {@code object-storage} Camel component: a producer that uploads, retrieves, deletes, checks and lists
 * objects with Micronaut Object Storage, so that the same route works with AWS S3, Azure Blob Storage,
 * Google Cloud Storage, Oracle Cloud Object Storage or the local file system.
 *
 * <p>The URI names the object storage: {@code object-storage:pictures} uses the
 * {@link ObjectStorageOperations} bean of the {@code micronaut.object-storage.<provider>.pictures}
 * configuration, and {@code object-storage:default} the only (or primary) one. The bucket or container is
 * part of that configuration.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Named(ObjectStorageComponent.SCHEME)
@Requires(classes = ObjectStorageOperations.class)
public class ObjectStorageComponent extends DefaultComponent {

    /**
     * The scheme of the component.
     */
    public static final String SCHEME = "object-storage";

    /**
     * The name that selects the only, or the primary, object storage.
     */
    public static final String DEFAULT_NAME = "default";

    private final BeanContext beanContext;

    /**
     * @param beanContext The bean context, to find the object storage operations
     */
    public ObjectStorageComponent(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String name = remaining.isEmpty() ? DEFAULT_NAME : remaining;
        ObjectStorageEndpoint endpoint = new ObjectStorageEndpoint(uri, this, name);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Finds the object storage operations of a name.
     *
     * @param name The name of the object storage configuration
     * @return The operations
     */
    ObjectStorageOperations<?, ?, ?> operations(String name) {
        return beanContext.findBean(ObjectStorageOperations.class, Qualifiers.byName(name))
            .or(() -> DEFAULT_NAME.equals(name) ? beanContext.findBean(ObjectStorageOperations.class) : java.util.Optional.empty())
            .orElseThrow(() -> new IllegalArgumentException("No object storage named " + name
                + ": configure micronaut.object-storage.<provider>." + name));
    }
}
