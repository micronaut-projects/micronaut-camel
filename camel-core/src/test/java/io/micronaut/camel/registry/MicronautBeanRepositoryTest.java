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
package io.micronaut.camel.registry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MicronautBeanRepositoryTest {

    private ApplicationContext context;
    private MicronautBeanRepository repository;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of("spec.name", "MicronautBeanRepositoryTest"));
        repository = new MicronautBeanRepository(context);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void looksUpABeanByNameAndType() {
        assertSame(context.getBean(Pricer.class), repository.lookupByNameAndType("pricing", Pricer.class));
    }

    @Test
    void looksUpByNameAmongTheBeansOfTheTypeWhenTheNameIsAmbiguous() {
        // two beans are named "audit", but only one of them is an AuditFormatter
        assertNull(repository.lookupByName("audit"));
        assertInstanceOf(AuditFormatter.class, repository.lookupByNameAndType("audit", AuditFormatter.class));
    }

    @Test
    void findsTheSingleBeanOfAType() {
        assertSame(context.getBean(Pricer.class), repository.findSingleByType(Pricer.class));
    }

    @Test
    void findsNoSingleBeanWhenThereIsNoneOrSeveral() {
        assertNull(repository.findSingleByType(Runnable.class));
        assertNull(repository.findSingleByType(Notifier.class));
    }

    interface Pricer {
    }

    interface Notifier {
    }

    @Singleton
    @Named("pricing")
    @Requires(property = "spec.name", value = "MicronautBeanRepositoryTest")
    static class PricingService implements Pricer {
    }

    @Singleton
    @Named("audit")
    @Requires(property = "spec.name", value = "MicronautBeanRepositoryTest")
    static class Auditor {
    }

    @Singleton
    @Named("audit")
    @Requires(property = "spec.name", value = "MicronautBeanRepositoryTest")
    static class AuditFormatter {
    }

    @Singleton
    @Requires(property = "spec.name", value = "MicronautBeanRepositoryTest")
    static class EmailNotifier implements Notifier {
    }

    @Singleton
    @Requires(property = "spec.name", value = "MicronautBeanRepositoryTest")
    static class SmsNotifier implements Notifier {
    }
}
