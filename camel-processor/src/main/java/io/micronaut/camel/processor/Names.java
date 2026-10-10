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
package io.micronaut.camel.processor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Naming and documentation helpers for the generated types.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class Names {

    private Names() {
    }

    /**
     * Converts a Camel name, such as {@code aws2-s3} or {@code camel.vault.springConfig}, into an upper
     * camel case type name, such as {@code Aws2S3} or {@code CamelVaultSpringConfig}.
     *
     * @param name The name
     * @return The type name
     */
    static String typeName(String name) {
        StringBuilder result = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, '_');
        }
        return result.toString();
    }

    /**
     * Converts a camel case configuration prefix, such as {@code camel.vault.springConfig}, to kebab
     * case, such as {@code camel.vault.spring-config}.
     *
     * @param prefix The prefix
     * @return The kebab case prefix
     */
    static String kebabPrefix(String prefix) {
        List<String> parts = new ArrayList<>();
        for (String part : prefix.split("\\.")) {
            parts.add(NameUtils.hyphenate(part).toLowerCase(Locale.ENGLISH));
        }
        return String.join(".", parts);
    }

    /**
     * Escapes Camel's plain text descriptions for Javadoc.
     *
     * @param text The text
     * @return The escaped text
     */
    static String javadoc(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("@", "&#64;")
            .replace("*/", "*&#47;")
            .replace("\\u", "\\\\u")
            // sourcegen hands Javadoc to JavaPoet as a format string
            .replace("$", "$$")
            .trim();
    }
}
