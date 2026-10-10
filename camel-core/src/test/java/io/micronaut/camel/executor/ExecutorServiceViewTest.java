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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
        var view = new ExecutorServiceView("test", executor, 2, 2, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
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
        // the default profile of Camel: the pool grows only once 1000 tasks wait
        var view = new ExecutorServiceView("test", executor, 1, 20, 1000, ThreadPoolRejectedPolicy.CallerRuns);
        List<Integer> order = new CopyOnWriteArrayList<>();
        IntStream.range(0, 50).forEach(i -> view.execute(() -> order.add(i)));
        view.shutdown();
        assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(IntStream.range(0, 50).boxed().toList(), order);
    }

    @Test
    void growsUpToTheMaximumPoolSizeOnlyOnceTheQueueIsFull() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, 3, 2, ThreadPoolRejectedPolicy.Abort);
        var release = new CountDownLatch(1);
        Set<Integer> started = ConcurrentHashMap.newKeySet();
        var done = new CountDownLatch(5);
        for (int i = 1; i <= 5; i++) {
            int task = i;
            view.execute(() -> {
                started.add(task);
                await(release);
                done.countDown();
            });
        }
        // the first task runs, the next two wait in the queue, and the pool grows for the last two
        waitUntil(() -> started.size() == 3);
        Thread.sleep(50);
        assertEquals(Set.of(1, 4, 5), started);
        // the queue is full and the pool at its maximum size
        assertThrows(RejectedExecutionException.class, () -> view.execute(() -> { }));
        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(Set.of(1, 2, 3, 4, 5), started);
    }

    @Test
    void handsTheTasksOffWithoutAQueue() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, 2, 0, ThreadPoolRejectedPolicy.Abort);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            view.execute(() -> {
                started.countDown();
                await(release);
            });
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        assertThrows(RejectedExecutionException.class, () -> view.execute(() -> { }));
        release.countDown();
    }

    @Test
    void appliesTheRejectedPolicyWhenTheExecutorRejectsTheTask() throws Exception {
        ExecutorService closed = Executors.newSingleThreadExecutor();
        closed.shutdown();

        var abort = new ExecutorServiceView("test", closed, 1, 1, 10, ThreadPoolRejectedPolicy.Abort);
        assertThrows(RejectedExecutionException.class, () -> abort.execute(() -> { }));
        abort.shutdown();
        assertTrue(abort.awaitTermination(1, TimeUnit.SECONDS));

        var callerRuns = new ExecutorServiceView("test", closed, 1, 1, 10, ThreadPoolRejectedPolicy.CallerRuns);
        var caller = new Thread[1];
        callerRuns.execute(() -> caller[0] = Thread.currentThread());
        assertEquals(Thread.currentThread(), caller[0]);
        callerRuns.shutdown();
        assertTrue(callerRuns.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void aFailingTaskDoesNotStopTheWaitingTasks() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, 1, 10, ThreadPoolRejectedPolicy.Abort);
        var release = new CountDownLatch(1);
        List<Integer> ran = new CopyOnWriteArrayList<>();
        view.execute(() -> {
            await(release);
            throw new IllegalStateException("failed");
        });
        view.execute(() -> ran.add(1));
        view.execute(() -> ran.add(2));
        release.countDown();
        view.shutdown();
        assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(List.of(1, 2), ran);
    }

    @Test
    void rejectsTheTasksThatDoNotFit() throws Exception {
        var view = new ExecutorServiceView("test", executor, 1, 1, 1, ThreadPoolRejectedPolicy.Abort);
        var release = new CountDownLatch(1);
        view.execute(() -> await(release));
        view.execute(() -> { });
        assertThrows(RejectedExecutionException.class, () -> view.execute(() -> { }));
        release.countDown();
    }

    @Test
    void runsTheTasksThatDoNotFitInTheCallerWithCallerRuns() {
        var view = new ExecutorServiceView("test", executor, 1, 1, 0, ThreadPoolRejectedPolicy.CallerRuns);
        var release = new CountDownLatch(1);
        view.execute(() -> await(release));
        var caller = new Thread[1];
        view.execute(() -> caller[0] = Thread.currentThread());
        assertEquals(Thread.currentThread(), caller[0]);
        release.countDown();
    }

    @Test
    void shuttingThePoolDownLeavesTheExecutorRunning() throws Exception {
        var view = new ExecutorServiceView("test", executor, 4, 4, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
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
        var view = new ExecutorServiceView("test", executor, 1, 1, Integer.MAX_VALUE, ThreadPoolRejectedPolicy.Abort);
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

    @Test
    void runsTheWaitingTasksWhenTheExecutorNoLongerAcceptsTasks() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            var view = new ExecutorServiceView("test", single, 1, 1, 10, ThreadPoolRejectedPolicy.Abort);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            List<Integer> ran = new CopyOnWriteArrayList<>();
            view.execute(() -> {
                started.countDown();
                await(release);
                ran.add(0);
            });
            view.execute(() -> ran.add(1));
            view.execute(() -> ran.add(2));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            // the executor rejects new tasks, and finishes the running one
            single.shutdown();
            release.countDown();
            view.shutdown();
            assertTrue(view.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals(List.of(0, 1, 2), ran);
        } finally {
            single.shutdownNow();
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out");
            Thread.sleep(5);
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
}
