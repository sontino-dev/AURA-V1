package com.brody.aura.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny tick scheduler for deferred actions (post-attack sequences).
 */
public class TickScheduler {

    private static final List<Task> tasks = new ArrayList<>();

    public static void schedule(int delayTicks, Runnable action) {
        tasks.add(new Task(delayTicks, action));
    }

    public static void tick() {
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            t.ticksLeft--;
            if (t.ticksLeft <= 0) {
                try {
                    t.action.run();
                } catch (Exception ignored) {
                }
                it.remove();
            }
        }
    }

    private static final class Task {
        int ticksLeft;
        final Runnable action;

        Task(int ticksLeft, Runnable action) {
            this.ticksLeft = ticksLeft;
            this.action = action;
        }
    }
}
