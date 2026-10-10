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
package io.micronaut.camel.examples.healthchecks;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

import java.util.Map;

/**
 * A chaos monkey health check that reports UP or DOWN in a chaotic way.
 * <p>
 * This is a custom implementation of a Camel {@link org.apache.camel.health.HealthCheck}, which is
 * discovered because it is a bean in the Micronaut application context, Camel's registry.
 */
@Singleton
@Named("monkey")
@Requires(env = "example-health-checks")
public class MonkeyHealthCheck extends AbstractHealthCheck {

    private volatile boolean up = true;

    MonkeyHealthCheck() {
        super("custom", "monkey");
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.detail("monkey", "The chaos monkey was here");
        if (up) {
            builder.up();
        } else {
            builder.down();
        }
    }

    public String chaos() {
        up = !up;
        return up ? "All is okay" : "Chaos monkey was here";
    }
}
