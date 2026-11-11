# Custom Task Manager

## Abstract

Our custom TaskManager is a Java background scheduling utility developed for our FTC robot software. It provides a central way to organize recurring updates and timed actions without making the calling code wait for each action to execute. Built on Java's scheduling framework, it adds named task management, validated timing, coordinated lifecycle control, failure handling, and telemetry tailored to our project.

## Features

### Flexible scheduling

- **Immediate one-time tasks:** Queue an action for execution as soon as the background worker is available.
- **Delayed one-time tasks:** Execute an action after a configurable delay.
- **Repeating tasks:** Run an action repeatedly, beginning immediately or after an initial delay.
- **Fixed-delay execution:** Measure the interval from the end of one execution to the start of the next, avoiding overlapping scheduled executions and catch-up bursts.
- **Unit-aware timing:** Accept the project's time objects, allowing readable durations in milliseconds, seconds, or minutes, with rounding to whole milliseconds.

### Named task management

- **Task identification:** Give every task a descriptive name for management and inspection.
- **Automatic replacement:** Scheduling an existing name cancels the previous task and registers its replacement, preventing duplicate registrations for the same job.
- **Replacement-aware cleanup:** A finishing older task cannot accidentally remove the registration of its replacement.
- **Automatic completion cleanup:** One-time tasks leave the registry after completion, including when their execution fails.
- **Submission cleanup:** Failed scheduling attempts do not leave phantom tasks in the registry.

### Controlled background execution

- **Dedicated worker:** Run scheduled actions on a single background worker, separately from the caller.
- **Sequential execution:** Tasks on the same worker execute one at a time, simplifying coordination between background jobs.
- **Low-priority operation:** Configure the worker for low-priority background work.
- **Identifiable daemon thread:** Use a named worker for debugging that does not independently keep the process alive.
- **Coordinated lifecycle operations:** Synchronize scheduling, replacement, cancellation, and executor shutdown to protect shared scheduler state.

### Cancellation and lifecycle control

- **Individual cancellation:** Stop future executions of a task by name and request interruption if it is running.
- **Cancellation with exit confirmation:** Optionally wait, with a configurable timeout, for the observed task body to exit and report whether it stopped.
- **Queued-task cancellation:** Prevent an invocation from entering its task body once its entry has been cancelled.
- **Global shutdown:** Cancel registered tasks, clear the registry, and shut down the executor.
- **Bounded shutdown wait:** Wait up to 500 milliseconds for the stopped executor to terminate and log if it remains active.
- **Worker-aware shutdown:** Avoid waiting for the worker to terminate itself when shutdown is initiated inside a task.
- **Automatic initialization and restart:** Create the executor when needed, reuse it while available, and create a fresh executor when scheduling resumes after shutdown.
- **Cancelled queue cleanup:** Remove cancelled scheduled entries from the executor queue promptly.

### Validation and failure handling

- **Input validation:** Reject missing or blank names, missing task bodies, and invalid time values before scheduling.
- **Timing safeguards:** Require finite, non-negative delays and repeating periods of at least one millisecond.
- **Recoverable exception handling:** Log ordinary task exceptions while allowing repeating tasks to run again.
- **Fatal error handling:** Log serious Java errors, unregister the affected task, and rethrow the error so that its repeating execution stops.
- **Interruption preservation:** Restore the thread's interruption flag when a caught exception contains an interruption signal.
- **Diagnostic logging:** Identify task failures, cancellation failures, and shutdown problems through the project's logging system, with optional suppression of cancellation-failure logging.

### Monitoring and telemetry

- **Registered task overview:** Retrieve a separate, alphabetically sorted set of task names.
- **Compact telemetry:** Display registered task names in a consistent text format.
- **Name lookup:** Check whether a particular task is registered.
- **Task count:** Inspect the number of registered tasks without building a full name list.

## Performance optimizations and thread layout

Our approach is to keep control-loop work predictable, move slower independent work to dedicated workers, and avoid spending CPU time on unnecessary polling or queued work. The repository includes both optimizations used by the OpMode base classes and optional vision/planning workers available for integration. The CPU layout below is a configurable test arrangement, not a claim that every competition OpMode already uses it.

### How and why we divide the work

| Thread or worker | Responsibility | Placement and reasoning |
|---|---|---|
| OpMode control loop | Update the drive/follower, apply accepted vision corrections, and issue motor commands | The stationary CPU test requests CPU 0. Keeping hardware decisions on the caller gives control a clear owner and avoids competing motor writes. |
| `LimelightWorker` | Own camera operations, process vision samples, and update the target map | Requests CPU 1 by default. Separating vision work keeps camera/status delays and projection work out of the control loop. |
| `ButineWorker` | Solve copied planning requests and publish results | Requests CPU 2 by default. Planning can consume substantial compute time without making the control loop execute the search. Path execution remains on the caller. |
| `TaskManager-worker` | Execute short scheduled background actions sequentially | One reused, low-priority worker with no explicit core pinning. This limits thread creation and coordination overhead while preventing simultaneous execution of its scheduled actions. |
| Diagnostics sampler | Collect CPU/thread statistics for the stationary test | A separate sleeping worker samples at 2 Hz, keeping filesystem scans out of each control tick. |

We choose threads by responsibility and blocking behavior, rather than creating one thread per task. Vision and planning have independent workloads and owners, while short scheduled jobs share TaskManager's worker. Putting a long camera call or planner solve on that shared worker would delay every other scheduled job. Adding more threads would also introduce more shared-state coordination; it would not automatically make hardware communication faster.

Workers exchange copied requests and published results instead of directly driving the robot. Planning results are checked for request identity, pose, target changes, and remaining time before execution. This lets computation run in the background while the caller retains authority over whether a result is still usable.

### Core pinning: mechanism and tradeoffs

Core pinning restricts the CPUs on which a particular native thread may run. Our `ThreadAffinity` helper obtains the calling thread's Linux TID with `android.os.Process.myTid()`, saves its original affinity mask, requests a mask through Android's Toybox `taskset`, and reads the mask back. It reports success only when the effective mask matches the request. CPU indices are zero based: CPUs 0, 1, and 2 use hexadecimal masks `1`, `2`, and `4`.

The proposed layout separates control, vision, and planning onto different eligible CPUs to test whether reduced competition between our workers improves control-loop consistency. These numbers are configurable placement choices, not evidence that a particular core is faster. Affinity does not reserve a core: Android and SDK threads may still execute there. Restricting placement can also hurt performance when a selected core is busy or unavailable, so each worker supports `preferredCore = -1` to retain its inherited scheduler placement.

Affinity is requested once at worker startup, outside the recurring processing loop. Unsupported or rejected requests are reported and the worker continues without a verified binding. Workers must start before optionally pinning the control thread, because newly created threads can inherit the creator's affinity. Cleanup attempts to restore the original mask on the same thread, which also matters when the SDK reuses an OpMode thread.

### Other optimizations in the software

- **Bulk hardware reads:** The TeleOp and autonomous base classes switch hubs to manual bulk caching for active operation and clear each hub's cache before the loop's sensor reads. Reusing the cache within that cycle avoids redundant bulk transactions while clearing it each cycle prevents stale readings.
- **Different rates for different work:** The vision worker targets a 10 ms processing period but reads camera health about every 250 ms. Slower status operations therefore do not run on every vision iteration.
- **Sleeping instead of busy waiting:** The planner waits for a request when idle. Vision parks for the remaining portion of its target period, and the diagnostic sampler sleeps between samples. These workers avoid continuously polling when no work is due.
- **Bounded pending work:** New planning requests replace pending work and invalidate older results; camera pipeline requests coalesce, and snapshot requests use a bounded pending slot. This prevents an accumulating queue of obsolete work, although an already running planner solve may still finish before its result is discarded.
- **No scheduled catch-up bursts:** TaskManager uses fixed delay between the end of one invocation and the next start. Vision also avoids accumulating missed polls. An overrun therefore does not create a backlog of scheduled catch-up executions.
- **Less diagnostic overhead:** TeleOp sets a 100 ms telemetry transmission interval and gates optional telemetry through configuration. TaskManager provides direct name lookup and task count methods so callers do not need to construct a sorted task-name list for simple checks.
- **Shorter shared-state operations:** Vision avoids holding the map lock during SDK I/O. TaskManager waits for shutdown outside its lifecycle lock, reuses its worker, and promptly removes cancelled entries from the executor queue.

### How we evaluate the layout

The objective is consistent control response under realistic vision and planning load, rather than maximum thread count or a high average loop rate alone. Compare scheduler-managed placement with verified pinning using the same workload and telemetry settings. Record loop work/cycle time, upper-percentile and maximum cycle times, vision overruns, planner solve duration, per-thread CPU use, and available frequency/thermal readings. Repeat after warm-up and during sustained load to expose occasional stalls and thermal effects.

`CpuThreadTest` provides stationary diagnostics, including affinity read-back and a control-thread pinning toggle. Its timings describe that test, so the selected layout must also be validated in the actual robot routine before claiming a competition performance improvement. Desktop checks can validate scheduling and result-handling logic; they cannot establish Control Hub affinity support or measured speedups.

## Engineering value

The TaskManager gives our robot software a reusable foundation for background work. It centralizes scheduling and cleanup, reduces duplicated lifecycle code, and makes task activity easier to inspect during development and operation.

## Operating boundaries

Timing is best-effort rather than hard real-time. A long-running action delays other work on the same worker, and cancellation relies on the action responding to interruption. Code on other threads still requires appropriate coordination when accessing shared state or hardware. Telemetry describes registered tasks, including tasks waiting for their next execution; it does not mean every listed task is executing at that instant.

## Implementation reference

For the separate device-level batch script found in the sibling project, see [Control Hub performance mode](Control-Hub-Performance-Mode.md). It requests a performance governor rather than raising the CPU clock limit, and has separate thermal-service behavior.

[TaskManager.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java)

[Thread placement and worker integration notes](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/THREADING-2026-10-16.md), [ThreadAffinity.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ThreadAffinity.java), [LimelightWorker.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightWorker.java), [ButineWorker.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineWorker.java), and [CpuThreadTest.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java).
