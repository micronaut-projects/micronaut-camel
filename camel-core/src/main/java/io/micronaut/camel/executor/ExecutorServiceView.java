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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Camel thread pool whose tasks run on a shared Micronaut executor.
 *
 * <p>The pool keeps the limits of its Camel thread pool profile, as a JDK {@code ThreadPoolExecutor} does: up
 * to {@code corePoolSize} of its tasks run at a time, the others wait in a queue of {@code queueCapacity} tasks,
 * in order, and only when the queue is full do more tasks run at a time, up to {@code maxPoolSize}; the tasks
 * that do not fit then are rejected with the rejected policy of the profile. A queue capacity of 0 hands the
 * tasks off, as a {@code SynchronousQueue}. Each running task of the pool is a worker on the Micronaut
 * executor, which runs the waiting tasks after its own, as the threads of a JDK thread pool do, so the tasks
 * of a pool of one run one after the other. Camel shuts the pool down when the route or the context stops:
 * that stops the pool, never the Micronaut executor.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
final class ExecutorServiceView extends AbstractExecutorService {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorServiceView.class);

    private final String executorName;
    private final ExecutorService executor;
    private final int corePoolSize;
    private final int maxPoolSize;
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
     * @param corePoolSize   The number of tasks that run at a time before tasks wait in the queue
     * @param maxPoolSize    The maximum number of tasks running at a time, once the queue is full
     * @param queueCapacity  The maximum number of waiting tasks, 0 to hand the tasks off
     * @param rejectedPolicy What to do with the tasks that do not fit
     */
    ExecutorServiceView(String executorName, ExecutorService executor, int corePoolSize, int maxPoolSize, int queueCapacity,
                        ThreadPoolRejectedPolicy rejectedPolicy) {
        this.executorName = executorName;
        this.executor = executor;
        this.corePoolSize = Math.max(1, corePoolSize);
        this.maxPoolSize = Math.max(this.corePoolSize, maxPoolSize);
        this.queueCapacity = Math.max(0, queueCapacity);
        this.rejectedPolicy = rejectedPolicy;
    }

    @Override
    public void execute(Runnable task) {
        boolean worker = false;
        lock.lock();
        try {
            while (true) {
                if (shutdown) {
                    throw new RejectedExecutionException("The Camel thread pool on the Micronaut executor '" + executorName + "' is shut down");
                }
                if (running < corePoolSize) {
                    running++;
                    worker = true;
                    break;
                }
                if (queue.size() < queueCapacity) {
                    queue.add(task);
                    return;
                }
                if (running < maxPoolSize) {
                    // the queue is full: grow, as a JDK thread pool
                    running++;
                    worker = true;
                    break;
                }
                if (rejectedPolicy != ThreadPoolRejectedPolicy.Block) {
                    break;
                }
                room.awaitUninterruptibly();
            }
        } finally {
            lock.unlock();
        }
        if (worker) {
            startWorker(task);
        } else {
            reject(task, null);
        }
    }

    private void startWorker(Runnable task) {
        try {
            executor.execute(() -> work(task));
        } catch (RuntimeException e) {
            // the Micronaut executor no longer accepts tasks, for example as it shuts down
            List<Runnable> stranded = release(true);
            try {
                reject(task, e);
            } finally {
                stranded.forEach(this::rejectWaiting);
            }
        }
    }

    private void reject(Runnable task, @Nullable RuntimeException cause) {
        if (rejectedPolicy == ThreadPoolRejectedPolicy.CallerRuns) {
            task.run();
            return;
        }
        throw new RejectedExecutionException(cause != null
            ? "The Micronaut executor '" + executorName + "' of the Camel thread pool rejected the task: " + cause.getMessage()
            : "The Camel thread pool on the Micronaut executor '" + executorName + "' is full: " + maxPoolSize + " running and "
                + queueCapacity + " waiting tasks", cause);
    }

    /**
     * Rejects a waiting task that no worker can run, as the Micronaut executor rejected the worker: the task
     * runs in the current thread with the {@code CallerRuns} policy, and is cancelled otherwise, so that no
     * caller waits for it forever.
     */
    private void rejectWaiting(Runnable task) {
        if (rejectedPolicy == ThreadPoolRejectedPolicy.CallerRuns) {
            runTask(task);
        } else if (task instanceof Future<?> future) {
            future.cancel(false);
        } else {
            LOG.warn("Dropped a waiting task of the Camel thread pool on the Micronaut executor '{}', which rejects new tasks", executorName);
        }
    }

    /**
     * Runs a task, and then the waiting tasks, as a thread of a JDK thread pool does.
     */
    private void work(Runnable first) {
        Thread thread = Thread.currentThread();
        Runnable task = first;
        while (task != null) {
            lock.lock();
            try {
                threads.add(thread);
                if (stopped) {
                    // as a JDK thread pool, which interrupts the tasks it runs once stopped
                    thread.interrupt();
                }
            } finally {
                lock.unlock();
            }
            runTask(task);
            lock.lock();
            try {
                threads.remove(thread);
                if (stopped) {
                    // shutdownNow may have interrupted the thread, which belongs to the Micronaut executor
                    Thread.interrupted();
                }
                // the worker keeps its slot for the next waiting task, so the tasks of a pool of one run in order
                task = queue.poll();
                if (task != null) {
                    room.signal();
                } else {
                    release(false);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void runTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable e) {
            // the worker goes on with the waiting tasks, where a JDK thread pool replaces its thread
            LOG.warn("A task of the Camel thread pool on the Micronaut executor '{}' failed: {}", executorName, e.getMessage(), e);
        }
    }

    /**
     * Releases the slot of a worker.
     *
     * @param failed Whether the worker could not start
     * @return The waiting tasks that no worker is left to run
     */
    private List<Runnable> release(boolean failed) {
        lock.lock();
        try {
            running--;
            room.signalAll();
            List<Runnable> stranded = List.of();
            if (failed && running == 0 && !queue.isEmpty()) {
                stranded = new ArrayList<>(queue);
                queue.clear();
            }
            if (running == 0) {
                idle.signalAll();
            }
            return stranded;
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
        return "Camel thread pool on the Micronaut executor '" + executorName + "' (" + corePoolSize + " to " + maxPoolSize
            + " running and " + queueCapacity + " waiting tasks at most)";
    }
}
