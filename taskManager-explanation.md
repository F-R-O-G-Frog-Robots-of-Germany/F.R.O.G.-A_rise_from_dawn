# `TaskManager` Explanation

## Overview

`TaskManager` is a small, static utility used to run named background tasks for the FTC robot code. It provides four scheduling operations:

1. Run a task repeatedly at a fixed interval.
2. Run a task repeatedly after an initial delay.
3. Run a task once immediately.
4. Run a task once after a delay.

Every scheduled task has a string name. The name is used as the task's identity. If a new task is scheduled using a name that is already active, the previous task is cancelled and replaced by the new one.

The implementation is located at:

```text
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java
```

The class and source file both use PascalCase:

```java
public final class TaskManager {
```

The public class, constructor, and filename all use `TaskManager`. Project-defined methods use snake_case, while required SDK and Java API method names retain their original spelling.

---

## Package and imports

The scheduler belongs to the project's control package:

```java
package org.firstinspires.ftc.teamcode.core.control;
```

The time type is a nested class inside the project's `Units` class. Its complete type is:

```java
org.firstinspires.ftc.teamcode.core.units.Units.Time
```

The scheduler imports it as:

```java
import org.firstinspires.ftc.teamcode.core.units.Units.Time;
```

This import is important. An earlier version attempted to import `Time` from `org.firstinspires.ftc.teamcode.units`, but that package does not exist in this project. `Time` is nested inside `Units`, and `Units.java` is inside the `core.units` package.

The scheduler also uses Java's scheduling classes:

- `ScheduledExecutorService` to run delayed and repeating work.
- `ScheduledThreadPoolExecutor` to create the worker executor.
- `Future` to cancel scheduled tasks.
- `TimeUnit.MILLISECONDS` to tell the executor that all converted values are milliseconds.
- `ConcurrentHashMap` and `ConcurrentMap` to store active task entries safely.
- `TreeSet` to return task names in a deterministic sorted order.

---

## Why the scheduler uses a single worker thread

The scheduler creates one worker thread:

```java
ScheduledThreadPoolExecutor ex =
        new ScheduledThreadPoolExecutor(1, WorkerThread::new);
```

Using one worker means scheduled tasks do not execute simultaneously with one another inside this scheduler. a task that takes longer than expected can delay a later task, but two scheduler tasks will not overlap on separate scheduler threads.

This is useful for robot control code because it avoids some kinds of concurrent access to motors, sensors, and shared state. It does not make all robot code automatically thread-safe, however. Code outside this scheduler can still access the same hardware or state from other FTC threads, such as OpMode callbacks.

The worker thread is created with the following properties:

- It is a daemon thread, so it does not prevent the process from shutting down.
- It is named `TaskManager-worker` to make logs and debugging easier.
- Its priority is set once when the worker is created instead of changing the priority every time a task runs.
- Its priority is set to `Thread.MIN_PRIORITY`, matching the scheduler's intended low-priority background behavior.

The priority is configured in the thread factory rather than inside each task. This matters because executor worker threads are reused. Changing a reused worker's priority inside every task could accidentally affect later tasks.

---

## Task names and replacement behavior

Every task must have a non-null, non-blank name. These are invalid:

```java
null
""
"   "
```

The scheduler rejects them with `IllegalArgumentException`.

Task names are identities. For example:

```java
TaskManager.schedule("updateTelemetry", telemetryTask, Time.ms(100));
```

If this is called again with the same name:

```java
TaskManager.schedule("updateTelemetry", replacementTask, Time.ms(100));
```

then the original `updateTelemetry` task is cancelled before the replacement is registered.

This prevents accidental accumulation of multiple tasks doing the same job. Without replacement behavior, repeatedly calling a setup method could create multiple copies of a periodic task.

The implementation uses an `Entry` object for each registered task. The entry contains:

- The task name.
- Whether it is a one-shot task.
- The `Future` returned by the executor.

When a task finishes or is cancelled, cleanup uses conditional map removal:

```java
runningTasks.remove(entry.name, entry);
```

That second argument is important. It means the cleanup only removes the map entry if the map still contains that exact entry. If the task was replaced while an older task was finishing, the old task cannot accidentally remove the replacement task.

---

## The `Time` type

The scheduler accepts the project's `Units.Time` type instead of accepting raw `long` values. `Units.Time` stores seconds internally and provides factory methods such as:

```java
Time.ms(250)
Time.s(2)
Time.min(1)
```

Examples:

```java
Time.ms(250)   // 250 milliseconds
Time.s(2)      // 2 seconds
Time.min(1)    // 60 seconds
```

The scheduler converts the time to milliseconds with:

```java
time.ms()
```

The Java executor requires integer millisecond values, so the scheduler rounds the result to the nearest millisecond using `Math.round`.

For example:

```text
250.0 ms -> 250 ms
250.4 ms -> 250 ms
250.6 ms -> 251 ms
```

---

## Time validation

All delayed and repeating operations pass their time values through the private `to_millis` method.

The method rejects a null time:

```java
Time delay = null;
```

It rejects NaN and infinity. These values could otherwise produce undefined or unusable scheduling behavior:

```java
Time.s(Double.NaN)
Time.s(Double.POSITIVE_INFINITY)
```

It also checks the original millisecond value before rounding. This prevents a small negative value from being rounded to zero and accidentally accepted. For example, a delay of `-0.2 ms` must still be rejected even though `Math.round(-0.2)` produces zero.

The rules are:

- Delays must be at least `0 ms`.
- Repeating periods must be at least `1 ms`.
- Values must be finite.
- Values are rounded to the nearest whole millisecond after validation.

a repeating period of zero is not valid because a scheduled executor requires a positive period. a negative delay is also invalid because a delay represents the amount of time before execution and cannot be negative.

Invalid values throw `IllegalArgumentException` with a message describing the parameter and received value. Failing early is preferable to allowing the executor to fail later with a less useful exception.

---

## Scheduling methods

### `schedule`

```java
public static void schedule(String name, Runnable task, Time period)
```

Runs a task repeatedly. The first execution is scheduled with zero initial delay, so it is eligible to run immediately. Later executions use the supplied period.

Example:

```java
TaskManager.schedule(
        "sampleSensors",
        () -> {
            // Read sensors or update state.
        },
        Time.ms(50)
);
```

This schedules the task approximately every 50 milliseconds. Actual timing can be later if the worker is busy or the Android system delays the thread.

The period must be at least 1 millisecond.

---

### `schedule_delayed`

```java
public static void schedule_delayed(
        String name,
        Runnable task,
        Time delay,
        Time period
)
```

Runs a task repeatedly, but waits for the initial delay before its first execution.

Example:

```java
TaskManager.schedule_delayed(
        "delayedTelemetry",
        () -> {
            // Begin telemetry work after the initial delay.
        },
        Time.s(1),
        Time.ms(100)
);
```

This waits approximately one second, then attempts to run the task every 100 milliseconds.

The delay may be zero. The period must be at least 1 millisecond.

---

### `run_once`

```java
public static void run_once(String name, Runnable task)
```

Runs a task once as soon as the worker can execute it.

Example:

```java
TaskManager.run_once(
        "resetState",
        () -> {
            // Reset state once.
        }
);
```

The task is still placed on the scheduler's worker thread. It does not run on the caller's thread.

---

### `run_once_delayed`

```java
public static void run_once_delayed(String name, Runnable task, Time delay)
```

Runs a task once after the supplied delay.

Example:

```java
TaskManager.run_once_delayed(
        "releaseMechanism",
        () -> {
            // Release the mechanism after the delay.
        },
        Time.ms(500)
);
```

The delay must be finite and non-negative.

---

## Task validation

Every scheduling method eventually calls the private `start` method. Before an executor is touched, `start` checks:

1. The name is not null or blank.
2. The `Runnable` is not null.
3. The already-converted time values are valid.

Passing a null task is rejected with `IllegalArgumentException`. This gives a clear caller error instead of allowing a null pointer failure inside a worker thread later.

---

## Registration before submission

a subtle race can happen with zero-delay tasks. a task may finish almost immediately after it is submitted. If the scheduler submitted the task first and registered it afterward, the task could finish and remove nothing, then the later registration could leave a completed task incorrectly listed as active.

The scheduler avoids that problem by registering the `Entry` before submitting the task:

```java
runningTasks.put(name, entry);
```

Only after registration does it call `schedule`, `scheduleWithFixedDelay`, or `submit` through the executor.

The `Future` is then stored in the entry after the executor accepts the task. If executor submission fails, the map entry is removed in the exception handler so a failed submission cannot leave a phantom task.

---

## Exception behavior

The worker wrapper separates ordinary exceptions from serious `Error` objects.

### Ordinary exceptions

If a task throws an `Exception`, the scheduler logs it. Repeating tasks remain registered and are allowed to run again on their next scheduled execution.

This behavior is intentional: one ordinary failure does not automatically disable a repeating task.

For example, a temporary sensor or calculation problem will be logged but will not necessarily stop the periodic task completely.

If the exception or one of its causes is an `InterruptedException`, the scheduler restores the thread's interrupted flag:

```java
Thread.currentThread().interrupt();
```

This preserves the cancellation signal rather than silently swallowing it.

### Serious errors

If a task throws an `Error`, the scheduler logs it, unregisters the task, and rethrows it. Examples of serious JVM errors include `OutOfMemoryError`.

Catching every `Throwable` and silently continuing would hide serious process-level failures. The scheduler therefore only catches ordinary `Exception` values for normal task handling and treats `Error` separately.

### One-shot cleanup

One-shot tasks are unregistered in a `finally` block. This means they are removed whether they:

- Complete normally.
- Throw an ordinary exception.
- Throw an error.
- Observe cancellation while running.

Repeating tasks stay registered until they are explicitly cancelled, replaced, stopped, or removed after a fatal error.

---

## Cancellation

### Cancelling one task

```java
TaskManager.cancel("sampleSensors");
```

Cancellation removes the named entry and calls:

```java
future.cancel(true);
```

The `true` argument requests interruption of a running task. Interruption is cooperative. It does not forcibly terminate arbitrary Java code.

a task should therefore respond to interruption, particularly if it contains a loop:

```java
while (!Thread.currentThread().isInterrupted()) {
    // Do a small amount of work.
}
```

Blocking operations that support interruption should also be used where possible.

The overload:

```java
TaskManager.cancel("sampleSensors", false);
```

suppresses logging if cancellation itself throws a runtime exception. The default overload uses `true` and logs cancellation failures.

Cancellation is synchronized with scheduling and shutdown using the same internal lock. This prevents cancellation from removing a task while another thread is simultaneously replacing it or shutting down the executor.

---

## Replacing a task

Scheduling a task with an existing name performs replacement:

1. Acquire the scheduler lock.
2. Obtain or create the executor.
3. Remove the old entry for that name.
4. Cancel the old entry's future.
5. Create and register the new entry.
6. Submit the new task.
7. Store the new future.
8. Release the lock.

The operation is designed so that an old task's final cleanup cannot remove the replacement entry. This is why `Entry` objects are used instead of removing by name alone.

---

## `cancel_and_await`

```java
boolean bodyStopped = TaskManager.cancel_and_await("taskName", 100);
```

This cancels the currently registered entry and waits for its **Runnable body** to
exit, for up to the supplied milliseconds. A cancelled `Future` can become done
before the body stops; this method therefore observes `Entry.executingThread`
instead of using `Future.get()` as proof of completion.

`begin()`, `end()` and `await_stopped()` synchronize on that entry. Cancellation
marks it cancelled before future interruption, so a queued invocation cannot start.
The running invocation clears its thread in `finally` and calls `notifyAll()`.
The waiting caller releases the entry monitor inside `timedWait()`, allowing
the worker to signal exit. The wait occurs outside the scheduler's global lock.
Periodic tasks update this marker for every invocation; a one-time completion
latch would incorrectly describe later invocations.

The result is false if the body ignores interruption beyond the timeout, the wait
is interrupted, or a body tries to wait for its own exit. If no entry is registered
at call time, the result is true. Call this **instead of** `cancel()` when exit
confirmation is required: a name removed earlier by cancellation or replacement
cannot identify the earlier body. Autonomous no longer schedules motor control or
mechanism timers on this worker.

## `stop_all`

```java
TaskManager.stop_all();
```

This method cancels every currently registered task and shuts down the scheduler's executor.

The shutdown process is:

1. Acquire the internal lock.
2. Save the current executor reference.
3. Cancel every registered entry.
4. Clear the task map.
5. Set the shared executor reference to null.
6. Call `shutdownNow()` on the old executor.
7. Release the lock.
8. Wait outside the lock for up to 500 milliseconds.

Waiting outside the lock is important. a finishing worker task must be able to complete its cleanup without being blocked by the thread calling `stop_all`.

If a task calls `stop_all` from the worker itself, the scheduler does not wait for the worker to terminate itself. It returns immediately in that case.

If the executor does not terminate within 500 milliseconds, the scheduler logs a warning. This usually means a task is ignoring interruption or is blocked in code that does not respond to interruption.

After `stop_all`, scheduling a new task creates a fresh executor automatically.

---

## Initialization

The executor is created lazily. Calling any scheduling method automatically initializes it if necessary.

It is also possible to initialize it explicitly:

```java
TaskManager.init();
```

Calling `init` more than once is safe. If an executor already exists, it is reused.

The internal `ensure_executor` method must be called while holding the scheduler lock. This keeps executor creation, shutdown, and scheduling serialized.

When a new executor is created, the task map is cleared. This ensures that stale entries from a previous stopped executor are not carried into a new worker lifecycle.

---

## Telemetry methods

### `get_running_task_names`

```java
Set<String> names = TaskManager.get_running_task_names();
```

Returns the names currently registered in the scheduler.

The names are returned in a `TreeSet`, so iteration order is sorted and deterministic. The returned set is a new set and does not directly expose the internal concurrent map.

### `get_telemetry`

```java
String telemetry = TaskManager.get_telemetry();
```

Returns the active task names separated by tab characters.

For example, if the active task names are:

```text
armControl
sampleSensors
updateTelemetry
```

then the result is:

```text
armControl\tsampleSensors\tupdateTelemetry
```

The result does not include a trailing tab. If no tasks are active, it returns an empty string.

---

## Important timing limitations

The scheduler provides requested timing, not a hard real-time guarantee. Actual execution can be delayed by:

- Other work running on the scheduler's single worker.
- Android thread scheduling.
- Garbage collection.
- Control Hub or Robot Controller load.
- Long-running task bodies.
- Blocking I/O or hardware calls.

a repeating task should therefore perform a small amount of work and return promptly. a long task can delay every other task because the scheduler intentionally uses one worker thread.

The scheduler also uses fixed delay scheduling. The next period is measured after the previous execution has completed, rather than attempting to start executions at a fixed wall-clock rate regardless of task duration. This avoids overlapping executions but means a slow task reduces its effective frequency.

---

## Recommended task design

a good scheduled task should:

1. Have a descriptive unique name.
2. Do a limited amount of work.
3. Check interruption if it contains a loop.
4. Avoid blocking indefinitely.
5. Avoid directly conflicting with another thread's hardware access.
6. Use `Time.ms`, `Time.s`, or `Time.min` instead of raw conversion math.
7. Be safe to run repeatedly if it is a repeating task.
8. Log enough context to identify failures.

Example:

```java
TaskManager.schedule(
        "updateShooterControl",
        () -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            // Read current state, calculate output, and update the subsystem.
        },
        Time.ms(20)
);
```

To replace it later:

```java
TaskManager.schedule(
        "updateShooterControl",
        replacementTask,
        Time.ms(20)
);
```

To stop it:

```java
TaskManager.cancel("updateShooterControl");
```

To stop all scheduler work during an OpMode transition or shutdown:

```java
TaskManager.stop_all();
```

---

## Build and verification

The project was compiled after the time fixes and rename with:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew.bat :TeamCode:compileDebugJavaWithJavac
```

The result was:

```text
BUILD SUCCESSFUL
```

The build still reports existing warnings about Java 8 source/target compatibility and deprecated Gradle features, but there are no compilation errors from `TaskManager.java`.

---

## Git status of this documentation

This file is intentionally not staged or added to Git. It is only a local explanatory document, as requested.
