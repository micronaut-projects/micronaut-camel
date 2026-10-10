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

import io.micronaut.core.annotation.Internal;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Camel thread pool whose tasks run on a shared Micronaut executor.
 *
 * <p>The pool keeps the limits of its Camel thread pool profile: at most {@code maxConcurrency} of its
 * tasks run at a time, the others wait in a queue of {@code queueCapacity} tasks, in order, and the
 * tasks that do not fit are rejected with the rejected policy of the profile. Camel shuts the pool down
 * when the route or the context stops: that stops the pool, never the Micronaut executor.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ExecutorServiceView extends AbstractExecutorService {

    private final String executorName;
    private final ExecutorService executor;
    private final int maxConcurrency;
    private final int queueCapacity;
    private final ThreadPoolRejectedPolicy rejectedPolicy;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();
    private final Condition room = lock.newCondition();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final Set<Thread> threads = new HashSet<>();
    private int running;
    private boolean shutdown;
    private boolean stopped;

    /**
     * @param executorName   The name of the Micronaut executor
     * @param executor       The Micronaut executor
     * @param maxConcurrency The maximum number of tasks running at a time
     * @param queueCapacity  The maximum number of waiting tasks
     * @param rejectedPolicy What to do with the tasks that do not fit
     */
    ExecutorServiceView(String executorName, ExecutorService executor, int maxConcurrency, int queueCapacity,
                        ThreadPoolRejectedPolicy rejectedPolicy) {
        this.executorName = executorName;
        this.executor = executor;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.queueCapacity = Math.max(0, queueCapacity);
        this.rejectedPolicy = rejectedPolicy;
    }

    @Override
    public void execute(Runnable task) {
        boolean slot = false;
        lock.lock();
        try {
            while (true) {
                if (shutdown) {
                    throw new RejectedExecutionException("The Camel thread pool on the Micronaut executor '" + executorName + "' is shut down");
                }
                if (running < maxConcurrency) {
                    running++;
                    slot = true;
                    break;
                }
                if (queue.size() < queueCapacity) {
                    queue.add(task);
                    return;
                }
                if (rejectedPolicy != ThreadPoolRejectedPolicy.Block) {
                    break;
                }
                room.awaitUninterruptibly();
            }
        } finally {
            lock.unlock();
        }
        if (slot) {
            dispatch(task);
        } else if (rejectedPolicy == ThreadPoolRejectedPolicy.CallerRuns) {
            task.run();
        } else {
            throw new RejectedExecutionException("The Camel thread pool on the Micronaut executor '" + executorName
                + "' is full: " + maxConcurrency + " running and " + queueCapacity + " waiting tasks");
        }
    }

    private void dispatch(Runnable task) {
        try {
            executor.execute(() -> run(task));
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    private void run(Runnable task) {
        Thread thread = Thread.currentThread();
        lock.lock();
        try {
            if (stopped) {
                release();
                return;
            }
            threads.add(thread);
        } finally {
            lock.unlock();
        }
        try {
            task.run();
        } finally {
            Runnable next;
            lock.lock();
            try {
                threads.remove(thread);
                if (stopped) {
                    // shutdownNow may have interrupted the thread, which belongs to the Micronaut executor
                    Thread.interrupted();
                }
                next = queue.poll();
                if (next != null) {
                    room.signal();
                }
            } finally {
                lock.unlock();
            }
            if (next != null) {
                // the next task keeps the slot, so the tasks of a pool of one run in order
                dispatch(next);
            } else {
                release();
            }
        }
    }

    private void release() {
        lock.lock();
        try {
            running--;
            room.signal();
            if (running == 0) {
                idle.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            room.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        lock.lock();
        try {
            shutdown = true;
            stopped = true;
            List<Runnable> waiting = new ArrayList<>(queue);
            queue.clear();
            threads.forEach(Thread::interrupt);
            room.signalAll();
            return waiting;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isShutdown() {
        lock.lock();
        try {
            return shutdown;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isTerminated() {
        lock.lock();
        try {
            return shutdown && running == 0;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lock();
        try {
            while (!(shutdown && running == 0)) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = idle.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "Camel thread pool on the Micronaut executor '" + executorName + "' (" + maxConcurrency + " running, "
            + queueCapacity + " waiting tasks at most)";
    }
}
