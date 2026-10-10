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
package io.micronaut.camel.convert;

import io.micronaut.core.annotation.Internal;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.TypeConverterSupport;
import org.jspecify.annotations.Nullable;

/**
 * The base class of the type converter loaders that the annotation processor generates for classes
 * annotated with Camel's {@code @Converter}. A loader registers itself for each conversion of the
 * class, then dispatches on the source and target types.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
public abstract class GeneratedTypeConverterLoader extends TypeConverterSupport implements TypeConverterLoader {

    /**
     * Registers this loader as the converter from one type to another.
     *
     * @param registry The registry
     * @param from     The source type
     * @param to       The target type
     */
    protected final void addConverter(TypeConverterRegistry registry, Class<?> from, Class<?> to) {
        TypeConverter converter = this;
        registry.addTypeConverter(to, from, converter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> @Nullable T convertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value)
        throws TypeConversionException {
        if (value == null) {
            return null;
        }
        try {
            return (T) doConvert(type, exchange, value);
        } catch (TypeConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new TypeConversionException(value, type, e);
        }
    }

    /**
     * Converts a value with the converter method for its type and the target type.
     *
     * @param type     The target type
     * @param exchange The exchange, if any
     * @param value    The value
     * @return The converted value, or {@code null} if no converter method applies
     * @throws Exception if the converter method fails
     */
    protected abstract @Nullable Object doConvert(Class<?> type, @Nullable Exchange exchange, Object value) throws Exception;
}
