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
package io.micronaut.camel;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.TypeConverterSupport;
import org.jspecify.annotations.Nullable;

/**
 * A fallback Camel type converter that delegates to the Micronaut {@link ConversionService}, so the
 * conversions registered with Micronaut are also available to Camel. Camel only consults it when
 * none of its own converters applies.
 *
 * <p>It does not convert to JDK types, such as {@code Boolean} or {@code Integer}, which Camel converts
 * itself: Camel expects such a conversion to fail when the value does not match, for example when it
 * tries to convert {@code "Hello"} to a boolean, while Micronaut converts it to {@code false}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ConversionServiceTypeConverter extends TypeConverterSupport {

    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    ConversionServiceTypeConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public <T> @Nullable T convertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value)
        throws TypeConversionException {
        if (value == null || isJdkType(type) || !conversionService.canConvert(value.getClass(), type)) {
            return null;
        }
        try {
            return conversionService.convert(value, type).orElse(null);
        } catch (RuntimeException e) {
            throw new TypeConversionException(value, type, e);
        }
    }

    private static boolean isJdkType(Class<?> type) {
        return type.isPrimitive() || type.isArray() || type.getName().startsWith("java.");
    }
}
