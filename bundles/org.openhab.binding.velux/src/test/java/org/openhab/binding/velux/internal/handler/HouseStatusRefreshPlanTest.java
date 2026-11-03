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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests the bounded House Status refresh burst after actuator commands.
 */
@NonNullByDefault
class HouseStatusRefreshPlanTest {
    @Test
    void schedulesBoundedRefreshBurst() {
        HouseStatusRefreshPlan plan = new HouseStatusRefreshPlan();
        List<Long> delays = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();

        plan.schedule(refreshes::incrementAndGet, (task, delay) -> {
            tasks.add(task);
            delays.add(delay);
        });

        assertEquals(List.of(250L, 1000L, 2500L), delays);
        tasks.forEach(Runnable::run);
        assertEquals(3, refreshes.get());
    }

    @Test
    void newerBurstSupersedesPendingRefreshes() {
        HouseStatusRefreshPlan plan = new HouseStatusRefreshPlan();
        List<Runnable> oldTasks = new ArrayList<>();
        List<Runnable> newTasks = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();

        plan.schedule(refreshes::incrementAndGet, (task, delay) -> oldTasks.add(task));
        plan.schedule(refreshes::incrementAndGet, (task, delay) -> newTasks.add(task));

        oldTasks.forEach(Runnable::run);
        assertEquals(0, refreshes.get());
        newTasks.forEach(Runnable::run);
        assertEquals(3, refreshes.get());
    }

    @Test
    void cancelSuppressesPendingRefreshes() {
        HouseStatusRefreshPlan plan = new HouseStatusRefreshPlan();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();

        plan.schedule(refreshes::incrementAndGet, (task, delay) -> tasks.add(task));
        plan.cancel();
        tasks.forEach(Runnable::run);

        assertEquals(0, refreshes.get());
    }

    @Test
    void supersededRefreshAlreadyQueuedForCommunicationIsSuppressed() {
        HouseStatusRefreshPlan plan = new HouseStatusRefreshPlan();
        List<Runnable> scheduledTasks = new ArrayList<>();
        List<Runnable> communicationTasks = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();

        plan.schedule(refreshes::incrementAndGet, communicationTasks::add, (task, delay) -> scheduledTasks.add(task));
        scheduledTasks.getFirst().run();
        plan.schedule(refreshes::incrementAndGet, communicationTasks::add, (task, delay) -> {
        });
        communicationTasks.forEach(Runnable::run);

        assertEquals(0, refreshes.get());
    }
}
