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

import java.util.Locale;

/**
 * The operations of the {@code object-storage} component.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public enum ObjectStorageOperation {

    /**
     * Stores the message body (also {@code put}). The key is set by the {@code CamelObjectStorageKey}
     * header, the {@code key} option or the {@code CamelFileName} header.
     */
    UPLOAD,

    /**
     * Reads an object into the message body as a byte array, or {@code null} when there is no such object
     * (also {@code get}).
     */
    RETRIEVE,

    /**
     * Deletes an object.
     */
    DELETE,

    /**
     * Sets the message body to whether an object exists.
     */
    EXISTS,

    /**
     * Sets the message body to the keys of the objects, as a {@code Set<String>}.
     */
    LIST,

    /**
     * Copies an object to the key of the {@code CamelObjectStorageDestinationKey} header.
     */
    COPY;

    /**
     * Parses an operation, ignoring case, with {@code put} for {@link #UPLOAD} and {@code get} for
     * {@link #RETRIEVE}.
     *
     * @param value The name of the operation
     * @return The operation
     */
    public static ObjectStorageOperation parse(String value) {
        String name = value.trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "PUT" -> UPLOAD;
            case "GET" -> RETRIEVE;
            default -> valueOf(name);
        };
    }
}
