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
package io.micronaut.camel.executor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "spec.name", value = "MicronautExecutorsTest")
@Property(name = "micronaut.camel.executors.enabled", value = "true")
@Property(name = "micronaut.camel.executors.profiles.bulk", value = "io")
@Property(name = "micronaut.camel.executors.profiles.legacy", value = "camel")
class MicronautExecutorsTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    Threads threads;

    @Test
    void camelUsesTheMicronautThreadPoolFactory() {
        assertInstanceOf(MicronautThreadPoolFactory.class, camelContext.getExecutorServiceManager().getThreadPoolFactory());
    }

    @Test
    void theThreadsEipRunsOnVirtualThreadsOfTheBlockingExecutor() throws Exception {
        MockEndpoint done = camelContext.getEndpoint("mock:threads", MockEndpoint.class);
        done.expectedMessageCount(1);

        producerTemplate.sendBody("direct:threads", "work");

        done.assertIsSatisfied();
        Thread thread = threads.of("threads");
        assertTrue(thread.isVirtual(), thread::toString);
    }

    @Test
    void sedaConsumersRunOnTheBlockingExecutor() throws Exception {
        MockEndpoint done = camelContext.getEndpoint("mock:seda", MockEndpoint.class);
        done.expectedMessageCount(1);

        producerTemplate.sendBody("seda:work", "work");

        done.assertIsSatisfied();
        Thread thread = threads.of("seda");
        assertTrue(thread.isVirtual(), thread::toString);
    }

    @Test
    void aProfileRunsOnTheExecutorItIsMappedTo() throws Exception {
        MockEndpoint done = camelContext.getEndpoint("mock:bulk", MockEndpoint.class);
        done.expectedMessageCount(1);

        producerTemplate.sendBody("direct:bulk", "work");

        done.assertIsSatisfied();
        Thread thread = threads.of("bulk");
        assertFalse(thread.isVirtual(), thread::toString);
        assertTrue(thread.getName().startsWith("io-executor-thread"), thread::getName);
    }

    @Test
    void aProfileMappedToCamelKeepsCamelThreadPools() throws Exception {
        MockEndpoint done = camelContext.getEndpoint("mock:legacy", MockEndpoint.class);
        done.expectedMessageCount(1);

        producerTemplate.sendBody("direct:legacy", "work");

        done.assertIsSatisfied();
        Thread thread = threads.of("legacy");
        assertTrue(thread.getName().startsWith("Camel ("), thread::getName);
    }

    @Test
    void aPoolOfOneThreadRunsItsTasksOneAfterTheOther() throws Exception {
        // Camel fills the unset values of a profile from the default profile: a maximum pool size of 20 and a
        // queue of 1000 tasks, so the pool grows only once 1000 tasks wait, as with a JDK thread pool
        ExecutorService pool = camelContext.getExecutorServiceManager()
            .newThreadPool(this, "sequential", new ThreadPoolProfileBuilder("sequential").poolSize(1).build());
        try {
            AtomicInteger current = new AtomicInteger();
            AtomicInteger max = new AtomicInteger();
            List<Integer> order = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 30; i++) {
                int task = i;
                pool.execute(() -> {
                    max.accumulateAndGet(current.incrementAndGet(), Math::max);
                    sleep(2);
                    order.add(task);
                    current.decrementAndGet();
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals(1, max.get());
            assertEquals(IntStream.range(0, 30).boxed().toList(), order);
        } finally {
            camelContext.getExecutorServiceManager().shutdownNow(pool);
        }
    }

    @Test
    void aPoolWithoutAQueueHandsItsTasksOffOrRejectsThem() throws Exception {
        // as Camel's thread pools: a SynchronousQueue for a maximum queue size of 0 or less, not an unbounded queue
        ExecutorService pool = camelContext.getExecutorServiceManager().newThreadPool(this, "handoff",
            new ThreadPoolProfileBuilder("handoff").poolSize(1).maxPoolSize(2).maxQueueSize(-1)
                .rejectedPolicy(ThreadPoolRejectedPolicy.Abort).build());
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        try {
            for (int i = 0; i < 2; i++) {
                pool.execute(() -> {
                    started.countDown();
                    await(release);
                });
            }
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> { }));
        } finally {
            release.countDown();
            camelContext.getExecutorServiceManager().shutdownNow(pool);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "MicronautExecutorsTest")
    static class Threads {

        final Map<String, Thread> threads = new ConcurrentHashMap<>();

        void record(String route) {
            threads.put(route, Thread.currentThread());
        }

        Thread of(String route) {
            return threads.get(route);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "MicronautExecutorsTest")
    static class ThreadPoolProfiles {

        @Singleton
        @Named("bulk")
        ThreadPoolProfile bulk() {
            return new ThreadPoolProfileBuilder("bulk").poolSize(2).maxPoolSize(2).build();
        }

        @Singleton
        @Named("legacy")
        ThreadPoolProfile legacy() {
            return new ThreadPoolProfileBuilder("legacy").poolSize(1).maxPoolSize(1).build();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "MicronautExecutorsTest")
    static class ThreadRoutes extends RouteBuilder {

        private final Threads threads;

        ThreadRoutes(Threads threads) {
            this.threads = threads;
        }

        @Override
        public void configure() {
            from("direct:threads")
                .threads(2, 4)
                .process(exchange -> threads.record("threads"))
                .to("mock:threads");

            from("seda:work?concurrentConsumers=2")
                .process(exchange -> threads.record("seda"))
                .to("mock:seda");

            from("direct:bulk")
                .threads().executorService("bulk")
                .process(exchange -> threads.record("bulk"))
                .to("mock:bulk");

            from("direct:legacy")
                .threads().executorService("legacy")
                .process(exchange -> threads.record("legacy"))
                .to("mock:legacy");
        }
    }
}
