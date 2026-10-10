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

import io.micronaut.objectstorage.ObjectStorageOperations;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.DefaultEndpoint;
import org.jspecify.annotations.Nullable;

/**
 * An endpoint of the {@code object-storage} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@UriEndpoint(scheme = ObjectStorageComponent.SCHEME, title = "Micronaut Object Storage", syntax = "object-storage:name",
    producerOnly = true, category = {Category.CLOUD, Category.FILE})
public class ObjectStorageEndpoint extends DefaultEndpoint {

    private final String name;
    private ObjectStorageOperation operation = ObjectStorageOperation.UPLOAD;
    private @Nullable String key;
    private @Nullable ObjectStorageOperations<?, ?, ?> operations;

    /**
     * @param uri       The URI
     * @param component The component
     * @param name      The name of the object storage
     */
    ObjectStorageEndpoint(String uri, ObjectStorageComponent component, String name) {
        super(uri, component);
        this.name = name;
    }

    @Override
    public ObjectStorageComponent getComponent() {
        return (ObjectStorageComponent) super.getComponent();
    }

    /**
     * @return The name of the object storage configuration
     */
    public String getName() {
        return name;
    }

    /**
     * @return The operation, unless the {@code CamelObjectStorageOperation} header sets one
     */
    public ObjectStorageOperation getOperation() {
        return operation;
    }

    /**
     * @param operation The operation, such as {@code upload} (or {@code put}), {@code retrieve} (or {@code get}),
     *                  {@code delete}, {@code exists}, {@code list} or {@code copy}, unless the
     *                  {@code CamelObjectStorageOperation} header sets one. Defaults to {@code upload}.
     */
    public void setOperation(String operation) {
        this.operation = ObjectStorageOperation.parse(operation);
    }

    /**
     * @return The key of the object, unless the {@code CamelObjectStorageKey} header sets one
     */
    public @Nullable String getKey() {
        return key;
    }

    /**
     * @param key The key of the object, unless the {@code CamelObjectStorageKey} header sets one
     */
    public void setKey(@Nullable String key) {
        this.key = key;
    }

    /**
     * @return The object storage operations, those of the object storage of the name by default
     */
    public ObjectStorageOperations<?, ?, ?> getOperations() {
        ObjectStorageOperations<?, ?, ?> result = operations;
        if (result == null) {
            result = getComponent().operations(name);
            operations = result;
        }
        return result;
    }

    /**
     * @param operations The object storage operations to use instead of those of the object storage of the name
     */
    public void setOperations(ObjectStorageOperations<?, ?, ?> operations) {
        this.operations = operations;
    }

    @Override
    public Producer createProducer() {
        return new ObjectStorageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("The object-storage component has no consumer: " + getEndpointUri());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // fail fast on an unknown object storage
        getOperations();
    }
}
