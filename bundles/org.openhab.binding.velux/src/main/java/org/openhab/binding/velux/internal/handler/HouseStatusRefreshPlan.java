/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.velux.internal.handler;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Schedules a short, bounded burst of House Status reads after an actuator command.
 */
@NonNullByDefault
final class HouseStatusRefreshPlan {
    private static final long[] DELAYS_MILLIS = { 250L, 1000L, 2500L };
    private final AtomicLong requestGeneration = new AtomicLong();

    @FunctionalInterface
    interface DelayedTaskScheduler {
        void schedule(Runnable task, long delayMillis);
    }

    void schedule(Runnable refresh, DelayedTaskScheduler scheduler) {
        schedule(refresh, Runnable::run, scheduler);
    }

    void schedule(Runnable refresh, Consumer<Runnable> submit, DelayedTaskScheduler scheduler) {
        long generation = requestGeneration.incrementAndGet();
        for (long delayMillis : DELAYS_MILLIS) {
            scheduler.schedule(() -> {
                if (requestGeneration.get() == generation) {
                    submit.accept(() -> {
                        if (requestGeneration.get() == generation) {
                            refresh.run();
                        }
                    });
                }
            }, delayMillis);
        }
    }

    void cancel() {
        requestGeneration.incrementAndGet();
    }
}
