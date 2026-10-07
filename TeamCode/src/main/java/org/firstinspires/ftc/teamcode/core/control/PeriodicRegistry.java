package org.firstinspires.ftc.teamcode.core.control;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Callbacks that need to run once per OpMode loop tick, on the loop's own thread.
 *
 * Subsystems register their periodic work here during init() instead of every
 * OpMode author having to remember to call it from op_mode_loop(). AbstractTeleOp
 * drains this list once per iteration on the same thread that drives motors,
 * so there's no cross-thread race with subsystem calls made from op_mode_loop().
 *
 * Unrelated to TaskManager: that one runs work on a background worker thread for
 * delayed/periodic jobs. This one is for per-tick bookkeeping that must run on
 * the loop thread itself — e.g. Units.Timer state other loop code reads.
 */
public final class PeriodicRegistry {
    private static final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();

    private PeriodicRegistry() {
    }

    public static void register(Runnable callback) {
        callbacks.add(callback);
    }

    public static void clear() {
        callbacks.clear();
    }

    public static void run_all() {
        for (Runnable callback : callbacks) {
            callback.run();
        }
    }
}
