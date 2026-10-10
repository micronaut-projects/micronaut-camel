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
package io.micronaut.camel.test;

import io.micronaut.camel.inject.CamelPostProcess;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.test.context.TestContext;
import io.micronaut.test.context.TestExecutionListener;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.support.PluginHelper;

/**
 * Applies Camel's bean post-processor to a test instance marked with {@link CamelPostProcess}, as
 * Groovy tests are: Micronaut Test injects test instances without creating them as beans.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(classes = TestExecutionListener.class)
final class CamelTestInstancePostProcessor implements TestExecutionListener {

    private final BeanContext beanContext;
    private final BeanProvider<CamelContext> camelContext;

    /**
     * @param beanContext  The bean context
     * @param camelContext The Camel context
     */
    CamelTestInstancePostProcessor(BeanContext beanContext, BeanProvider<CamelContext> camelContext) {
        this.beanContext = beanContext;
        this.camelContext = camelContext;
    }

    @Override
    public void beforeTestExecution(TestContext testContext) throws Exception {
        Object testInstance = testContext.getTestInstance();
        // the marker is in the annotation metadata of the test class, not in its bytecode
        if (testInstance != null && beanContext.findBeanDefinition(testInstance.getClass())
            .filter(definition -> definition.hasStereotype(CamelPostProcess.class)).isPresent()) {
            PluginHelper.getBeanPostProcessor(camelContext.get())
                .postProcessBeforeInitialization(testInstance, testInstance.getClass().getName());
        }
    }
}
