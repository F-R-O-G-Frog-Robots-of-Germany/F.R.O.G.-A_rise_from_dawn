
package org.firstinspires.ftc.teamcode.core.control;

import static org.firstinspires.ftc.teamcode.core.control.LogManager.log;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.firstinspires.ftc.teamcode.core.units.Units.Time;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Named background tasks on a single low-priority worker thread.
 *
 * <ul>
 *   <li><b>Names are identities.</b> Scheduling a name that is already in use cancels the old
 *       task and atomically replaces it. The old task's cleanup can never remove the new entry.</li>
 *   <li><b>Time values</b> are rounded to the nearest millisecond. Delays must be &gt;= 0 ms,
 *       periods &gt;= 1 ms, and neither may be null, NaN or infinite
 *       (otherwise {@link IllegalArgumentException}).</li>
 *   <li><b>Errors:</b> an {@link Exception} thrown by a task is logged and the task carries on
 *       (periodic tasks run again next period). An {@link Error} is logged, the task is
 *       unregistered, and the error is rethrown, so a periodic task stops.</li>
 *   <li><b>Cancellation is cooperative.</b> Tasks are interrupted, so they must check
 *       {@code Thread.currentThread().isInterrupted()} or use interruptible calls.</li>
 * </ul>
 */
public final class TaskManager {

/** How long {@link #stop_all()} waits for running tasks to react to the interrupt. */
    private static final long STOP_TIMEOUT_MS = 500;

    /** Guards {@link #executor}, {@link #currentWorker} and every put/cancel/clear on {@link #runningTasks}. */
    private static final Object lock = new Object();
    private static ScheduledExecutorService executor = null;

/** The worker thread currently backing {@link #executor}. Set by the thread factory in {@link #ensure_executor()}. */
    private static volatile WorkerThread currentWorker = null;

    // Task completion removes its own entry with remove(name, entry) WITHOUT taking the lock,
    // so that removal must stay conditional: it only removes if the entry is still its own.
    private static final ConcurrentMap<String, Entry> runningTasks = new ConcurrentHashMap<>();

    private TaskManager() {
    }

    public static void init() {
        synchronized (lock) {
            ensure_executor();
        }
    }

    // ---------------------------------------------------------------- scheduling

    public static void schedule(String name, Runnable task, Time PERIOD) {
        start(name, task, 0, to_millis(PERIOD, "period", 1), true);
    }

    public static void run_once(String name, Runnable task) {
        start(name, task, 0, 0, false);
    }

    public static void schedule_delayed(String name, Runnable task, Time DELAY, Time PERIOD) {
        start(name, task, to_millis(DELAY, "delay", 0), to_millis(PERIOD, "period", 1), true);
    }

    public static void run_once_delayed(String name, Runnable task, Time DELAY) {
        start(name, task, to_millis(DELAY, "delay", 0), 0, false);
    }

    private static void start(String name, Runnable task,
                              long delayMs, long periodMs, boolean repeating) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("task name must not be null or blank");
        }
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }

// Lifecycle, replacement and registration happen as one atomic step, so stop_all()
        // can't shut the executor down between "get executor" and "submit".
        synchronized (lock) {
            ScheduledExecutorService ex = ensure_executor();

            Entry old = runningTasks.remove(name);
            if (old != null) {
                old.cancel();
            }

            // Registered BEFORE submitting: a zero-delay one-shot may finish immediately, and
            // its cleanup must find (and remove) the entry instead of racing a later put().
            Entry entry = new Entry(name, !repeating);
            runningTasks.put(name, entry);
            try {
                Runnable safe = wrap(entry, task);
                entry.future = repeating
                        ? ex.scheduleWithFixedDelay(safe, delayMs, periodMs, MILLISECONDS)
                        : ex.schedule(safe, delayMs, MILLISECONDS);
            } catch (RuntimeException e) {
                // e.g. RejectedExecutionException: don't leave a phantom task behind.
                runningTasks.remove(name, entry);
                throw e;
            }
        }
    }

    private static long to_millis(Time TIME, String what, long minMs) {
        if (TIME == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        double ms = TIME.ms();
        if (!SensorReadings.is_valid(ms)) {
            throw new IllegalArgumentException(what + " must be finite, got " + ms + " ms");
        }
        if (ms < minMs) {
            throw new IllegalArgumentException(
                    what + " must be >= " + minMs + " ms, got " + ms + " ms");
        }
        long rounded = Math.round(ms);
        if (rounded < minMs) {
            throw new IllegalArgumentException(
                    what + " must be >= " + minMs + " ms (after rounding), got " + ms + " ms");
        }
        return rounded;
    }

    private static Runnable wrap(Entry entry, Runnable task) {
        return () -> {
            if (!entry.begin()) return;
            // Tracks whether the Error branch below already removed the entry, so the
            // finally block doesn't redundantly call unregister() a second time for a
            // one-shot task that throws an Error.
            boolean alreadyUnregistered = false;
            try {
                task.run();
            } catch (Exception e) {
                if (caused_by_interrupt(e)) {
                    // Keep the cancellation signal instead of swallowing it.
                    Thread.currentThread().interrupt();
                } else {
                    log_error(entry.name + " failed: " + e);
                }
            } catch (Error e) {
                // Fatal (OOM etc.): don't hide it. Drop the entry and let the executor record it.
                log_error(entry.name + " fatal error: " + e);
                unregister(entry);
                alreadyUnregistered = true;
                throw e;
            } finally {
                entry.end();
                if (entry.oneShot && !alreadyUnregistered) {
                    unregister(entry);
                }
            }
        };
    }

    private static boolean caused_by_interrupt(Throwable t) {
        // Runnable.run() can't throw InterruptedException directly, but tasks often wrap it.
        for (int depth = 0; t != null && depth < 16; depth++, t = t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /** Removes the entry only if it is still the registered one for its name. */
    private static void unregister(Entry entry) {
        runningTasks.remove(entry.name, entry);
    }

    // ---------------------------------------------------------------- cancellation

    public static void cancel(String name) {
        cancel(name, true);
    }

    public static void cancel(String name, boolean logFailure) {
        if (name == null) return;

        synchronized (lock) {
            Entry entry = runningTasks.remove(name);
            if (entry == null) return;

            try {
                entry.cancel();
            } catch (RuntimeException e) {
                if (logFailure) {
                    log_error("cancel(" + name + ") failed: " + e);
                }
            }
        }
    }

    /**
     * Cancels the named task and blocks the caller until it has actually stopped running (or
     * {@code timeoutMs} elapses), whichever comes first. Useful for shutdown sequencing where
     * proceeding while the old task is still mid-execution would be unsafe.
     *
     * <p>Unlike {@link #cancel(String)}, this waits outside the internal lock, so other
     * scheduling calls are not blocked while it waits.
     *
     * <p>This observes the entry registered when called. A name already removed by cancel(),
     * replacement, or stop_all() has no entry to await. Call this instead of cancel() when
     * the caller requires confirmation of that entry's body exiting.
     *
     * @return true if no entry was registered or the observed body has exited; false if it
     *         still runs at the deadline, the caller is that body, or the wait is interrupted.
     */
    public static boolean cancel_and_await(String name, long timeoutMs) {
        if (name == null) return true;
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must be >= 0, got " + timeoutMs);
        }

        Entry entry;
        synchronized (lock) {
            entry = runningTasks.remove(name);
            if (entry == null) {
                return true; // already gone
            }
            try {
                entry.cancel();
            } catch (RuntimeException e) {
                log_error("cancelAndAwait(" + name + ") failed: " + e);
            }
        }

        return entry.await_stopped(timeoutMs);
    }

    /**
     * Cancels everything and shuts the worker down. Waits up to {@value #STOP_TIMEOUT_MS} ms for
     * running tasks to react to the interrupt and logs a warning if one doesn't. Scheduling again
     * afterwards starts a fresh worker.
     */
    public static void stop_all() {
        ScheduledExecutorService stopped;
        WorkerThread worker;

        synchronized (lock) {
            stopped = executor;
            if (stopped == null) return;

            worker = currentWorker;

            for (Entry entry : runningTasks.values()) {
                try {
                    entry.cancel();
                } catch (RuntimeException ignored) {
                }
            }
            runningTasks.clear();
            executor = null;
            currentWorker = null;

            try {
                stopped.shutdownNow();
            } catch (RuntimeException e) {
                log_error("stopAll: shutdownNow failed: " + e);
            }
        }

        // Wait outside the lock so finishing tasks are never blocked by us, and never wait for
// ourselves if a task calls stop_all() from the worker thread we're currently stopping.
        // Compared by identity against the thread actually backing this executor (not just
        // "any WorkerThread"), so a straggler from a previous, already-shut-down executor can't
        // be mistaken for the one we just told to stop.
        if (worker != null && Thread.currentThread() == worker) return;
        try {
            if (!stopped.awaitTermination(STOP_TIMEOUT_MS, MILLISECONDS)) {
                log_error("stopAll: a task is still running after " + STOP_TIMEOUT_MS
                        + " ms (is it ignoring interruption?)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- telemetry

    /** Names of registered tasks, sorted. */
    public static Set<String> get_running_task_names() {
        return new TreeSet<>(runningTasks.keySet());
    }

    public static String get_telemetry() {
        StringBuilder tel = new StringBuilder();
        for (String name : get_running_task_names()) {
            if (tel.length() > 0) tel.append('\t');
            tel.append(name);
        }
        return tel.toString();
    }

/** Whether a task with this name is currently registered. Cheaper than checking {@link #get_running_task_names()}. */
    public static boolean is_running(String name) {
        return name != null && runningTasks.containsKey(name);
    }

/** Number of currently registered tasks. Cheaper than {@code get_running_task_names().size()}. */
    public static int task_count() {
        return runningTasks.size();
    }

    // ---------------------------------------------------------------- internals

    /** Caller must hold {@link #lock}. */
    private static ScheduledExecutorService ensure_executor() {
        if (executor == null) {
            if (!runningTasks.isEmpty()) {
// Should be unreachable: stop_all() clears runningTasks and nulls executor
                // together inside the same synchronized block. Logged rather than silently
                // swallowed in case that invariant is ever broken by a future change.
                log_error("ensureExecutor: runningTasks had " + runningTasks.size()
                        + " stale entries with no executor; clearing");
                runningTasks.clear();
            }
            // Single-threaded executor: tasks execute strictly one after another,
            // so overlap between scheduled runs is impossible by construction.
            // No semaphore needed on top of this.
            ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
                // The lion doesn't concern himself with the shitty control hub
                WorkerThread t = new WorkerThread(r);
                currentWorker = t;
                return t;
            });
            ex.setRemoveOnCancelPolicy(true); // replaced/cancelled tasks leave the queue immediately
            executor = ex;
        }
        return executor;
    }

    private static void log_error(String message) {
        log("TaskManager - " + message);
    }

    /** Priority is set once here instead of on every task run (pool threads are reused). */
    private static final class WorkerThread extends Thread {
        WorkerThread(Runnable r) {
            super(r, "TaskManager-worker");
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }
    }

    private static final class Entry {
        final String name;
        final boolean oneShot;
        Future<?> future; // written and read only while holding lock
        private boolean cancelled;
        private Thread executingThread;

        Entry(String name, boolean oneShot) {
            this.name = name;
            this.oneShot = oneShot;
        }

        void cancel() {
            synchronized (this) { cancelled = true; }
            if (future != null) {
                future.cancel(true);
            }
        }

        synchronized boolean begin() {
            if (cancelled) return false;
            executingThread = Thread.currentThread();
            return true;
        }

        synchronized void end() {
            executingThread = null;
            notifyAll();
        }

        synchronized boolean await_stopped(long timeoutMs) {
            if (executingThread == Thread.currentThread()) return false;
            long start = System.nanoTime();
            long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (executingThread != null) {
                long remaining = timeoutNanos - (System.nanoTime() - start);
                if (remaining <= 0) return false;
                try {
                    java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }
}
