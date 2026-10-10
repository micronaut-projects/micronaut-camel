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

/**
 * The headers of the {@code object-storage} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public final class ObjectStorageConstants {

    /**
     * The operation to perform, overriding the {@code operation} option of the endpoint.
     */
    public static final String OPERATION = "CamelObjectStorageOperation";

    /**
     * The key of the object, overriding the {@code key} option of the endpoint. Without either, the
     * {@code CamelFileName} header is the key.
     */
    public static final String KEY = "CamelObjectStorageKey";

    /**
     * The key that the {@code copy} operation copies the object to.
     */
    public static final String DESTINATION_KEY = "CamelObjectStorageDestinationKey";

    /**
     * The content type of the uploaded object, otherwise the {@code Content-Type} header. Set by the
     * {@code retrieve} operation to the content type of the object, when known.
     */
    public static final String CONTENT_TYPE = "CamelObjectStorageContentType";

    /**
     * The ETag of the uploaded object, set by the {@code upload} operation.
     */
    public static final String E_TAG = "CamelObjectStorageETag";

    /**
     * The metadata of the uploaded or retrieved object, as a {@code Map<String, String>}.
     */
    public static final String METADATA = "CamelObjectStorageMetadata";

    private ObjectStorageConstants() {
    }
}
