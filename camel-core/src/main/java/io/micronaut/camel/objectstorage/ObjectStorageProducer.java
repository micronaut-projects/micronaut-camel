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

import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Performs the operation of an {@code object-storage} endpoint.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class ObjectStorageProducer extends DefaultProducer {

    ObjectStorageProducer(ObjectStorageEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public ObjectStorageEndpoint getEndpoint() {
        return (ObjectStorageEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getMessage();
        ObjectStorageOperations<?, ?, ?> operations = getEndpoint().getOperations();
        switch (operation(message)) {
            case UPLOAD -> upload(operations, message);
            case RETRIEVE -> retrieve(operations, message);
            case DELETE -> operations.delete(key(message));
            case EXISTS -> message.setBody(operations.exists(key(message)));
            case LIST -> message.setBody(operations.listObjects());
            case COPY -> operations.copy(key(message), destinationKey(message));
            default -> throw new IllegalStateException("Unsupported operation");
        }
    }

    private ObjectStorageOperation operation(Message message) {
        Object header = message.getHeader(ObjectStorageConstants.OPERATION);
        if (header instanceof ObjectStorageOperation operation) {
            return operation;
        }
        if (header != null) {
            return ObjectStorageOperation.parse(header.toString());
        }
        return getEndpoint().getOperation();
    }

    private String key(Message message) {
        String key = message.getHeader(ObjectStorageConstants.KEY, String.class);
        if (key == null) {
            key = getEndpoint().getKey();
        }
        if (key == null) {
            key = message.getHeader(Exchange.FILE_NAME, String.class);
        }
        if (key == null) {
            throw new IllegalArgumentException("No object key: set the " + ObjectStorageConstants.KEY
                + " header, the key option or the " + Exchange.FILE_NAME + " header");
        }
        return key;
    }

    private static String destinationKey(Message message) {
        String key = message.getHeader(ObjectStorageConstants.DESTINATION_KEY, String.class);
        if (key == null) {
            throw new IllegalArgumentException("No destination key: set the " + ObjectStorageConstants.DESTINATION_KEY + " header");
        }
        return key;
    }

    private void upload(ObjectStorageOperations<?, ?, ?> operations, Message message) throws Exception {
        String key = key(message);
        byte[] bytes = message.getMandatoryBody(byte[].class);
        String contentType = message.getHeader(ObjectStorageConstants.CONTENT_TYPE, String.class);
        if (contentType == null) {
            contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        }
        Map<String, String> metadata = metadata(message);
        UploadRequest request = contentType == null
            ? UploadRequest.fromBytes(bytes, key)
            : UploadRequest.fromBytes(bytes, key, contentType);
        if (!metadata.isEmpty()) {
            request.setMetadata(metadata);
        }
        UploadResponse<?> response = operations.upload(request);
        message.setHeader(ObjectStorageConstants.KEY, response.getKey());
        message.setHeader(ObjectStorageConstants.E_TAG, response.getETag());
    }

    private void retrieve(ObjectStorageOperations<?, ?, ?> operations, Message message) throws Exception {
        Optional<ObjectStorageEntry<?>> entry = operations.retrieve(key(message));
        if (entry.isEmpty()) {
            message.setBody(null);
            return;
        }
        ObjectStorageEntry<?> object = entry.get();
        try (InputStream stream = object.getInputStream()) {
            message.setBody(stream.readAllBytes());
        }
        object.getContentType().ifPresent(type -> message.setHeader(ObjectStorageConstants.CONTENT_TYPE, type));
        if (!object.getMetadata().isEmpty()) {
            message.setHeader(ObjectStorageConstants.METADATA, object.getMetadata());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> metadata(Message message) {
        Object metadata = message.getHeader(ObjectStorageConstants.METADATA);
        return metadata instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}
