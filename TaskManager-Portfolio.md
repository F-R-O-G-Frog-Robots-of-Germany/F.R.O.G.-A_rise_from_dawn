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

## Engineering value

The TaskManager gives our robot software a reusable foundation for background work. It centralizes scheduling and cleanup, reduces duplicated lifecycle code, and makes task activity easier to inspect during development and operation.

## Operating boundaries

Timing is best-effort rather than hard real-time. A long-running action delays other work on the same worker, and cancellation relies on the action responding to interruption. Code on other threads still requires appropriate coordination when accessing shared state or hardware. Telemetry describes registered tasks, including tasks waiting for their next execution; it does not mean every listed task is executing at that instant.

## Implementation reference

[TaskManager.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java)
