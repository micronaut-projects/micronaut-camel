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

import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorServiceViewTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void close() {
        executor.shutdownNow();
    }

    @Test
    void runsAtMostTheMaximumNumberOfTasksAtATime() throws Exception {
        var view = new ExecutorServiceView("test", executor, 2, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
        var current = new AtomicInteger();
        var max = new AtomicInteger();
        var done = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            view.execute(() -> {
                max.accumulateAndGet(current.incrementAndGet(), Math::max);
                sleep(5);
                current.decrementAndGet();
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(2, max.get());
    }

    @Test
    void aPoolOfOneRunsItsTasksInOrder() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
        List<Integer> order = new CopyOnWriteArrayList<>();
        IntStream.range(0, 50).forEach(i -> view.execute(() -> order.add(i)));
        view.shutdown();
        assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(IntStream.range(0, 50).boxed().toList(), order);
    }

    @Test
    void rejectsTheTasksThatDoNotFit() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, 1, ThreadPoolRejectedPolicy.Abort);
        var release = new CountDownLatch(1);
        view.execute(() -> await(release));
        view.execute(() -> { });
        assertThrows(RejectedExecutionException.class, () -> view.execute(() -> { }));
        release.countDown();
    }

    @Test
    void runsTheTasksThatDoNotFitInTheCallerWithCallerRuns() {
        var view = new ExecutorServiceView("test", executor, 1, 0, ThreadPoolRejectedPolicy.CallerRuns);
        var release = new CountDownLatch(1);
        view.execute(() -> await(release));
        var caller = new Thread[1];
        view.execute(() -> caller[0] = Thread.currentThread());
        assertEquals(Thread.currentThread(), caller[0]);
        release.countDown();
    }

    @Test
    void shuttingThePoolDownLeavesTheExecutorRunning() throws Exception {
        var view = new ExecutorServiceView("test", executor, 4, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
        var ran = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            view.execute(() -> {
                sleep(2);
                ran.incrementAndGet();
            });
        }
        view.shutdown();
        assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(view.isTerminated());
        assertEquals(10, ran.get());
        assertThrows(RejectedExecutionException.class, () -> view.execute(() -> { }));
        assertFalse(executor.isShutdown());
    }

    @Test
    void shutdownNowInterruptsTheRunningTasksAndReturnsTheWaitingOnes() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        view.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        view.execute(() -> { });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        assertEquals(1, view.shutdownNow().size());
        assertTrue(interrupted.await(10, TimeUnit.SECONDS));
        assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
        assertFalse(executor.isShutdown());
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
}
