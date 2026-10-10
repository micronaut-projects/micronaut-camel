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
package io.micronaut.camel.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates code from the Camel catalog into the package of the annotated class, with the
 * {@code micronaut-camel-processor} annotation processor.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface CamelConfig {

    /**
     * The camel-main configuration groups to generate {@code @ConfigurationProperties} for, such as
     * {@code camel.main} or {@code camel.vault.*}. {@code *} matches any characters.
     *
     * @return The group patterns
     */
    String[] main() default {};

    /**
     * The kinds of Camel extension to generate a requirement annotation for, such as
     * {@code @RequiresCamelKafka} for the {@code kafka} component.
     *
     * @return The kinds
     */
    Kind[] requirements() default {};

    /**
     * The kinds of Camel extension to generate {@code @ConfigurationProperties} for, such as
     * {@code KafkaComponentConfiguration} for {@code camel.component.kafka}.
     *
     * @return The kinds
     */
    Kind[] configurations() default {};

    /**
     * The names of the Camel extensions to generate code for, all of them by default. {@code *}
     * matches any characters.
     *
     * @return The name patterns
     */
    String[] include() default {};

    /**
     * The names of the Camel extensions to skip. {@code *} matches any characters.
     *
     * @return The name patterns
     */
    String[] exclude() default {};

    /**
     * A kind of Camel extension.
     */
    enum Kind {
        /**
         * A component, configured under {@code camel.component.<name>}.
         */
        COMPONENT,
        /**
         * A data format, configured under {@code camel.dataformat.<name>}.
         */
        DATAFORMAT,
        /**
         * A language, configured under {@code camel.language.<name>}.
         */
        LANGUAGE
    }
}
