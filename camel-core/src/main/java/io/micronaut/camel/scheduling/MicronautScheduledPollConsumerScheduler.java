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
package io.micronaut.camel.scheduling;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.inject.Named;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Schedules a Camel polling consumer, such as the {@code file} or {@code sql} consumer, on the
 * Micronaut task scheduler. Select it with {@code scheduler=micronaut}. With
 * {@code scheduler.cron=...} the consumer polls on a cron expression, optionally in
 * {@code scheduler.zoneId}; otherwise it polls with the consumer's initial delay and delay.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Prototype
@Named(MicronautScheduledPollConsumerScheduler.NAME)
public class MicronautScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

    /**
     * The name to select the scheduler with.
     */
    public static final String NAME = "micronaut";

    private final TaskScheduler taskScheduler;
    private @Nullable CamelContext camelContext;
    private @Nullable Consumer consumer;
    private @Nullable Runnable task;
    private @Nullable ScheduledFuture<?> future;
    private @Nullable String cron;
    private @Nullable String zoneId;

    /**
     * @param taskScheduler The task scheduler
     */
    public MicronautScheduledPollConsumerScheduler(@Named(TaskExecutors.SCHEDULED) TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * @return The cron expression, if any
     */
    public @Nullable String getCron() {
        return cron;
    }

    /**
     * @param cron A cron expression to poll on, such as {@code 0 0/5 * * * ?}
     */
    public void setCron(@Nullable String cron) {
        this.cron = cron;
    }

    /**
     * @return The time zone of the cron expression, if any
     */
    public @Nullable String getZoneId() {
        return zoneId;
    }

    /**
     * @param zoneId The time zone of the cron expression, such as {@code Europe/Paris}
     */
    public void setZoneId(@Nullable String zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public void onInit(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void scheduleTask(Runnable task) {
        this.task = task;
    }

    @Override
    public void unscheduleTask() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    public void startScheduler() {
        if (future != null || task == null) {
            return;
        }
        if (cron != null && !cron.isBlank()) {
            // Camel URIs use + for the spaces of a cron expression
            future = taskScheduler.schedule(cron.replace('+', ' '), zoneId, task);
        } else if (consumer instanceof ScheduledPollConsumer polling) {
            Duration initialDelay = Duration.ofMillis(polling.getTimeUnit().toMillis(polling.getInitialDelay()));
            Duration delay = Duration.ofMillis(polling.getTimeUnit().toMillis(polling.getDelay()));
            future = polling.isUseFixedDelay()
                ? taskScheduler.scheduleWithFixedDelay(initialDelay, delay, task)
                : taskScheduler.scheduleAtFixedRate(initialDelay, delay, task);
        } else {
            throw new IllegalStateException("Set scheduler.cron to schedule " + consumer + " with the Micronaut scheduler");
        }
    }

    @Override
    public boolean isSchedulerStarted() {
        return future != null;
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStop() {
        unscheduleTask();
    }
}
