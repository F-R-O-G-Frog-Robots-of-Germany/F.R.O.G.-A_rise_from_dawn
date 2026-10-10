# `taskScheduler.java` Error Report

**File reviewed:** `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/taskScheduler.java`

**Verification:** `./gradlew.bat :TeamCode:compileDebugJavaWithJavac` was run. The build currently fails with 18 errors. Some errors reported by the compiler are in `LastPositionStorage.java`; they are listed only where they are separate from this file's problems.

## Compile-blocking errors

### 1. Public class does not match the file name

- **Location:** line 17
- **Code:** `public abstract class TaskManager`
- **Problem:** The file is named `taskScheduler.java`, but Java requires a public top-level class to have the same name as the file. The compiler reports that `TaskManager` must be declared in `TaskManager.java`.
- **Fix:** Choose one consistent naming scheme:
  - Rename the file to `TaskManager.java`; or
  - Rename the class to `taskScheduler` (not recommended because Java class names should use PascalCase).

### 2. `Time` is imported from a package that does not exist

- **Location:** line 6
- **Code:** `import org.firstinspires.ftc.teamcode.core.units.Time;`
- **Problem:** The project defines `Time` as `Units.Time` in `org.firstinspires.ftc.teamcode.units.Units`, not in `org.firstinspires.ftc.teamcode.core.units`.
- **Fix:** Replace the import with:

```java
import org.firstinspires.ftc.teamcode.units.Units.Time;
```

### 3. `LogManager` does not exist in the referenced package

- **Location:** line 3
- **Code:** `import static org.firstinspires.ftc.teamcode.core.control.LogManager.log;`
- **Problem:** No `LogManager` class was found in `org.firstinspires.ftc.teamcode.core.control`, so the static import and every call to `log(...)` fail to compile.
- **Fix:** Either:
  1. Create `LogManager` in that package with a public static `log(String)` method; or
  2. Change the import and calls to the project's actual logging implementation, such as Android's `Log` API.

Do not add a replacement logger until the intended logging behavior and tag are confirmed.

## Runtime and concurrency errors

### 4. Replacing a task can cause the new task to disappear from the registry

- **Locations:** `cancelExisting`, `wrapSafeOnce`, and the `run_once`/`run_once_delayed` methods
- **Problem:** The old task is cancelled, then a new future is inserted under the same name. If the old task is already running, its `finally` block executes later and calls `runningTasks.remove(name)`. That can remove the **new** task's entry instead of the old one.
- **Fix:** Associate the cleanup with the specific future or generation. For example, remove conditionally with `runningTasks.remove(name, expectedFuture)`, or store a task record containing a unique generation token and remove only when the token still matches.

### 5. a one-shot task can finish before its future is registered

- **Locations:** `run_once` and `run_once_delayed`
- **Problem:** `submit`/`schedule` may execute a zero-delay task immediately. The wrapper can finish and remove the name before `runningTasks.put(name, future)` runs. The subsequent `put` then adds a completed future back into the map, leaving a stale task name in telemetry.
- **Fix:** Register task state atomically with submission, or use a task record/generation and have the wrapper remove only the matching record after the future has been registered. a dedicated synchronized scheduling path is another option.

### 6. `cancel` can remove a replacement task accidentally

- **Locations:** both `cancel(String ...)` overloads
- **Problem:** The method obtains a future, then cancels it, then calls `runningTasks.remove(name)`. Another thread can replace the task between those operations; the unconditional remove can delete the replacement entry.
- **Fix:** Use conditional removal:

```java
if (runningTasks.remove(name, future)) {
    future.cancel(true);
}
```

The exact ordering should be designed together with the scheduling/generation logic so a replacement cannot be cancelled unintentionally.

### 7. `stop_all` races with scheduling

- **Locations:** `stop_all`, `ensureInit`, and all scheduling methods
- **Problem:** `stop_all` sets `executor` to `null` and shuts it down while another thread may have passed `ensureInit()` but not yet submitted its task. The other thread can then submit to a shut-down executor, receive a rejection, or encounter a null executor. `cancelExisting` and registry updates are also outside the lifecycle lock.
- **Fix:** Serialize lifecycle changes and task submission using the same lock, or use a lifecycle generation/executor snapshot and handle `RejectedExecutionException`. Make sure a task is not inserted into `runningTasks` if submission fails.

### 8. a task that ignores interruption can continue after cancellation

- **Locations:** `cancel`, `cancelExisting`, and `stop_all`
- **Problem:** `Future.cancel(true)` only requests interruption. a task that blocks in non-interruptible code or ignores its interrupted status may continue running after cancellation or shutdown.
- **Fix:** Require task code to respond to interruption, check `Thread.currentThread().isInterrupted()`, and exit promptly. For robot lifecycle shutdown, also track/await executor termination when appropriate.

### 9. `shutdownNow()` is not followed by termination confirmation

- **Location:** `stop_all`
- **Problem:** `shutdownNow()` returns tasks that never started but does not guarantee already-running tasks have stopped. The method immediately marks the manager uninitialized and discards the executor reference.
- **Fix:** Call `awaitTermination` with a bounded timeout after `shutdownNow()`, and document the behavior if tasks still fail to stop. Do not claim all work has stopped until that policy is satisfied.

### 10. Thread priority is permanently changed on pooled worker threads

- **Location:** `wrapSafe`
- **Problem:** `Thread.currentThread().setPriority(Thread.MIN_PRIORITY)` changes the executor worker's priority. Because the worker is reused, all later tasks on that thread inherit the low priority. This can also throw a security-related exception on some platforms.
- **Fix:** Prefer configuring the executor's `ThreadFactory` once, if low priority is truly required. Otherwise remove the priority mutation. If it must be changed per task, save the old priority and restore it in `finally`.

### 11. Catching `Throwable` hides fatal JVM errors

- **Locations:** `wrapSafe` and `wrapSafeOnce`
- **Problem:** Catching `Throwable` also catches serious errors such as `OutOfMemoryError` and can leave the application in an unsafe state. It also catches `InterruptedException` without restoring the interrupted flag.
- **Fix:** Catch `Exception` for ordinary task failures. Handle interruption separately by restoring the flag:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

Only catch specific `Error` types if there is a documented reason.

### 12. Period and delay values are not validated

- **Locations:** all four public scheduling methods
- **Problem:** `null` names, tasks, or `Time` values cause unclear `NullPointerException`s. Zero or negative periods are rejected by the executor. Negative delays are invalid. a `Time` value containing `NaN` or infinity can also produce invalid conversion behavior. `ConcurrentHashMap` does not support a null task name.
- **Fix:** Validate arguments at the public API boundary and throw clear `IllegalArgumentException`s, for example:
  - name is non-null and non-blank;
  - task is non-null;
  - delay is non-negative and finite;
  - period is positive and finite.

### 13. Millisecond conversion silently truncates fractional values

- **Locations:** all calls using `(long) period.ms()` or `(long) delay.ms()`
- **Problem:** Fractional milliseconds are truncated. a positive value below one millisecond becomes zero, and timing behavior may differ from what the caller requested.
- **Fix:** Decide and document the supported precision. Use `Math.round` if nearest-millisecond behavior is intended, or reject values below one millisecond. Always validate the final converted value before submitting it.

### 14. Exceptions from task bodies are only logged and are otherwise invisible

- **Locations:** `wrapSafe` and `wrapSafeOnce`
- **Problem:** The wrappers swallow task failures. Callers cannot inspect the failure through the returned future because the public methods return `void`. a repeating task can therefore keep running while its body fails on every execution.
- **Fix:** Define an error policy: return `Future<?>` to callers, expose failure state/telemetry, cancel repeating tasks after an exception, or intentionally document that failures are logged and ignored.

### 15. Interrupted status is not preserved

- **Locations:** both wrappers' broad catch blocks
- **Problem:** If a task throws `InterruptedException`, catching it as `Throwable` clears the thread's interrupted status. The scheduler may then continue running the task or run later tasks without the cancellation signal.
- **Fix:** Catch `InterruptedException` separately, restore the interrupt flag, and normally return from the wrapper.

## API/design issues

### 16. The class is an abstract utility class with no abstract members

- **Location:** class declaration
- **Problem:** `TaskManager` has only static state and methods, so making it abstract does not provide useful behavior and still allows pointless subclassing.
- **Fix:** Make it `final` and add a private constructor:

```java
public final class TaskManager {
    private TaskManager() {
    }
}
```

### 17. Duplicate cancellation overloads make behavior harder to maintain

- **Locations:** `cancel(String)` and `cancel(String, boolean)`
- **Problem:** The two methods duplicate lookup, cancellation, and removal logic. Their exception logging behavior can drift, and the `boolean log` parameter has the same name as the imported `log` method, which reduces readability.
- **Fix:** Implement one method in terms of the other or use a private cancellation helper. Rename the parameter to `logFailure` or similar.

### 18. Task names are used as the only identity

- **Locations:** `runningTasks` and all public methods
- **Problem:** Scheduling the same name intentionally replaces the previous task. This is easy to misuse, and the current races make replacement unsafe.
- **Fix:** Explicitly document name uniqueness/replacement semantics and implement replacement atomically. Consider returning a task handle/token so callers can cancel exactly the task they scheduled.

### 19. Telemetry is nondeterministic and includes formatting noise

- **Location:** `get_telemetry`
- **Problem:** `HashSet` iteration order is unspecified, so telemetry order can change between calls. The result always ends with a trailing tab and may contain stale names because of the registration races above.
- **Fix:** Return a sorted collection or sort names before formatting, remove the trailing delimiter, and fix registry lifecycle first.

## Separate project errors exposed by the same build

These are not in `taskScheduler.java`, but they prevent the module from compiling and should be handled separately:

1. `LastPositionStorage.java` imports `com.pedropathing.geometry.Pose`, but that dependency/package is not available to the module.
2. All `Pose` references in `LastPositionStorage.java` consequently fail to resolve.

## Recommended repair order

1. Rename the source file/class consistently.
2. Correct the `Time` import.
3. Restore or replace the logging implementation.
4. Add argument validation.
5. Redesign task registration around an atomic task record/generation so replacement, completion, and cancellation cannot remove the wrong entry.
6. Serialize executor lifecycle with scheduling, and handle rejected submissions.
7. Define interruption and task-exception behavior.
8. Re-run the Gradle compile and add focused tests for zero-delay completion, replacement, cancellation races, and `stop_all` during submission.
