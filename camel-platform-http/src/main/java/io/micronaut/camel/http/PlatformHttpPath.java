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
package io.micronaut.camel.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The path of a platform-http consumer, such as {@code /users/{id}}, matched against request paths.
 *
 * <p>A {@code {name}} placeholder matches one path segment and is exposed as a path parameter. A path
 * that ends with {@code *}, or a consumer with {@code matchOnUriPrefix}, also matches every path below
 * it. A trailing slash in the request path is ignored, as it is for Micronaut routes.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class PlatformHttpPath {

    /**
     * Orders the paths from the most to the least specific: exact paths before prefix paths, then
     * fewer placeholders first, then longer literal text first.
     */
    static final Comparator<PlatformHttpPath> SPECIFICITY = Comparator
        .comparing(PlatformHttpPath::isPrefix)
        .thenComparingInt(p -> p.variables.size())
        .thenComparing(Comparator.comparingInt(PlatformHttpPath::literalLength).reversed());

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^/}]+)}");

    private final String path;
    private final Pattern pattern;
    private final List<String> variables;
    private final boolean prefix;
    private final int literalLength;

    private PlatformHttpPath(String path, Pattern pattern, List<String> variables, boolean prefix, int literalLength) {
        this.path = path;
        this.pattern = pattern;
        this.variables = variables;
        this.prefix = prefix;
        this.literalLength = literalLength;
    }

    /**
     * @param path             The consumer path
     * @param matchOnUriPrefix Whether the path also matches the paths below it
     * @return The parsed path
     */
    static PlatformHttpPath of(String path, boolean matchOnUriPrefix) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        boolean prefix = matchOnUriPrefix;
        if (normalized.endsWith("*")) {
            prefix = true;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        List<String> variables = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        int literalLength = 0;
        Matcher matcher = PLACEHOLDER.matcher(normalized);
        int last = 0;
        while (matcher.find()) {
            String literal = normalized.substring(last, matcher.start());
            regex.append(Pattern.quote(literal)).append("([^/]+)");
            literalLength += literal.length();
            variables.add(matcher.group(1));
            last = matcher.end();
        }
        String tail = normalized.substring(last);
        literalLength += tail.length();
        boolean root = "/".equals(normalized);
        if (!root) {
            regex.append(Pattern.quote(tail));
        }
        if (prefix) {
            regex.append(root ? "/.*" : "(?:/.*)?");
        } else {
            regex.append("/?");
        }
        regex.append('$');
        return new PlatformHttpPath(normalized, Pattern.compile(regex.toString()), List.copyOf(variables), prefix, literalLength);
    }

    /**
     * @param requestPath The raw (percent-encoded) request path, without the server context path
     * @return The decoded path parameters, or {@code null} if the path does not match
     */
    @Nullable Map<String, String> match(String requestPath) {
        Matcher matcher = pattern.matcher(requestPath);
        if (!matcher.matches()) {
            return null;
        }
        if (variables.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            values.put(variables.get(i), decode(matcher.group(i + 1)));
        }
        return values;
    }

    /**
     * @return Whether the path also matches the paths below it
     */
    boolean isPrefix() {
        return prefix;
    }

    /**
     * @return The names of the placeholders
     */
    List<String> variables() {
        return variables;
    }

    private int literalLength() {
        return literalLength;
    }

    @Override
    public String toString() {
        return prefix ? path + "*" : path;
    }

    private static String decode(String segment) {
        // a path segment keeps '+' as is, unlike a form value
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
