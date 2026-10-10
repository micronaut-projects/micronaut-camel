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

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Singleton;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

/**
 * A Camel {@link ThreadPoolFactory} whose thread pools run their tasks on the executors of Micronaut,
 * such as {@code blocking} (virtual threads, when available), {@code virtual}, {@code io}, or an executor
 * configured with {@code micronaut.executors.<name>.*}.
 *
 * <p>Enabled with {@code micronaut.camel.executors.enabled}. The thread pools of Camel run on the
 * executor named by {@code micronaut.camel.executors.default-executor}, {@code blocking} by default, and
 * the thread pools of a Camel thread pool profile on the executor that
 * {@code micronaut.camel.executors.profiles.<profile id>} names. The name {@code camel} keeps Camel's own
 * thread pools. A thread pool keeps the limits of its profile with the semantics of a JDK thread pool: its pool
 * size, queue size, maximum pool size (once the queue is full) and rejected policy. Scheduled thread pools, such
 * as those of timers and polling consumers, stay Camel's.</p>
 *
 * <p>Camel runs long-running tasks on these pools, such as the consumer loops of {@code seda} and the waits of
 * the {@code Block} rejected policy, so the Micronaut executor should be unbounded or elastic.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Requires(property = MicronautThreadPoolFactory.ENABLED, value = StringUtils.TRUE)
public final class MicronautThreadPoolFactory implements ThreadPoolFactory {

    /**
     * The prefix of the properties.
     */
    public static final String PREFIX = "micronaut.camel.executors";

    /**
     * The property that runs the thread pools of Camel on the executors of Micronaut when {@code true}.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * The property naming the Micronaut executor of the thread pools without a profile mapping.
     */
    public static final String DEFAULT_EXECUTOR = PREFIX + ".default-executor";

    /**
     * The prefix of the properties mapping Camel thread pool profiles to Micronaut executors.
     */
    public static final String PROFILES = PREFIX + ".profiles";

    /**
     * The executor name that keeps Camel's own thread pools.
     */
    public static final String CAMEL = "camel";

    private static final Logger LOG = LoggerFactory.getLogger(MicronautThreadPoolFactory.class);

    private final BeanLocator beanLocator;
    private final String defaultExecutor;
    private final Map<String, String> profiles;
    private final DefaultThreadPoolFactory camelThreadPools = new DefaultThreadPoolFactory();

    /**
     * @param beanLocator The bean locator of the executors
     * @param environment The environment
     */
    MicronautThreadPoolFactory(BeanLocator beanLocator, Environment environment) {
        this.beanLocator = beanLocator;
        this.defaultExecutor = environment.getProperty(DEFAULT_EXECUTOR, String.class, TaskExecutors.BLOCKING);
        this.profiles = environment.getProperties(PROFILES, StringConvention.RAW).entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        if (CAMEL.equals(defaultExecutor)) {
            return camelThreadPools.newCachedThreadPool(threadFactory);
        }
        // as a JDK cached thread pool: every task runs at once
        return new ExecutorServiceView(defaultExecutor, executor(defaultExecutor), Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
            ThreadPoolRejectedPolicy.Abort);
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        String name = executorName(profile);
        if (CAMEL.equals(name)) {
            return camelThreadPools.newThreadPool(profile, threadFactory);
        }
        // the semantics of Camel's thread pools, which are JDK thread pools: poolSize tasks run at a time, the
        // others wait in the queue, and the pool grows up to maxPoolSize only when the queue is full
        int poolSize = positive(profile.getPoolSize(), 1);
        int maxPoolSize = Math.max(poolSize, positive(profile.getMaxPoolSize(), poolSize));
        Integer maxQueueSize = profile.getMaxQueueSize();
        // as Camel's thread pools: a queue of maxQueueSize tasks, and no queue (a SynchronousQueue) for 0 or less
        int queueCapacity = maxQueueSize == null ? Integer.MAX_VALUE : Math.max(0, maxQueueSize);
        // Camel's thread pools default to CallerRuns too
        ThreadPoolRejectedPolicy policy = profile.getRejectedPolicy() != null ? profile.getRejectedPolicy() : ThreadPoolRejectedPolicy.CallerRuns;
        LOG.debug("Camel thread pool {} runs on the Micronaut executor {}", profile.getId(), name);
        return new ExecutorServiceView(name, executor(name), poolSize, maxPoolSize, queueCapacity, policy);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return camelThreadPools.newScheduledThreadPool(profile, threadFactory);
    }

    private String executorName(ThreadPoolProfile profile) {
        String id = profile.getId();
        String name = id != null ? profiles.get(id) : null;
        return name != null ? name : defaultExecutor;
    }

    private ExecutorService executor(String name) {
        return beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name))
            .orElseThrow(() -> new ConfigurationException("No Micronaut executor named '" + name
                + "' for the Camel thread pools: configure it with micronaut.executors." + name + ".*, or use one of the "
                + TaskExecutors.BLOCKING + ", " + TaskExecutors.VIRTUAL + " and " + TaskExecutors.IO + " executors"));
    }

    private static int positive(@Nullable Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }
}
