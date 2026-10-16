# CPU placement, vision and planner workers

Goal: keep vision and planning off the robot control loop, verify requested CPU assignments, and measure their effect with a stationary test.

## Available components

| Component | Responsibility | Default CPU request |
|---|---|---:|
| `ThreadAffinity` | Bind the calling native thread, read back its effective mask, and restore its original mask | Caller selects |
| `LimelightWorker` | Camera initialization/cleanup, projection, map updates, status reads and queued pipeline/snapshot commands | CPU 1 |
| `ButineWorker` | Solve copied requests when asked, sleep between requests, publish results and timings | CPU 2 |
| `ButineRunner(map, drive, planner)` | Request a background plan, validate it, then execute paths on the caller's loop | Execution stays on caller |
| `LimelightRunner(vision, drive)` | Request background camera operations while applying correction gates and pose writes on caller | Correction stays on caller |
| `CpuStats` | Sample native thread/core counters, affinity, memory, frequency and thermal nodes | Call on a diagnostics worker |
| `opmodes/CpuThreadTest` | Stationary telemetry test of these components | Requests CPU 0 for its loop |

All new production code is Java. No competition autonomous, shooter, intake or motor subsystem is connected by this change. The existing synchronous constructors/calls remain available. CPU numbers are zero based; `preferredCore = -1` requests no affinity change and retains the thread's inherited eligible CPU mask.

### How assignment works

Inside the target Java thread, `android.os.Process.myTid()` identifies its native Linux thread. `ThreadAffinity` runs Android's built-in Toybox `taskset -p MASK TID`, reads the effective mask back with `taskset -p TID`, and reports `applied` only on an exact match. CPU 0, 1 and 2 correspond to hexadecimal masks `1`, `2` and `4`. The helper reads the original mask before changing anything, uses argument tokens without a shell, bounds command time/output, and supports restoring that original mask on the same thread. It never uses `-a`, never changes every Robot Controller thread, and never requests root, clocks or governor changes.

Failure to read/set/verify affinity is exposed in `Result.detail` and worker telemetry. The worker still runs, and the diagnostics show the real allowed CPUs so an unverified request cannot be mistaken for a successful assignment. Kernel cpusets, offline cores or vendor permissions may limit requests. Pinning restricts where a thread can run; it does not exclude Android or SDK threads from that CPU, guarantee a faster loop, or provide real-time scheduling.

**Startup order matters:** Java/native threads can inherit their creator's current affinity. Start the vision, planner and diagnostics workers before optionally pinning the OpMode loop to CPU 0. That leaves their fallback mask at the original Android placement rather than accidentally inheriting CPU 0. Stop workers and restore the original control-thread mask in cleanup; the SDK may reuse a thread. The test follows this order and preserves an existing interrupt flag while restoring affinity.

Primary references: [Android native thread IDs](https://developer.android.com/reference/android/os/Process#myTid()), [Android's affinity discussion](https://developer.android.com/reference/android/os/Process#getExclusiveCores()), [Android 7.1.2 Toybox implementation](https://android.googlesource.com/platform/external/toybox/+/refs/tags/android-7.1.2_r39/toys/other/taskset.c), and [Linux affinity semantics and permissions](https://man7.org/linux/man-pages/man2/sched_setaffinity.2.html).

## Using the workers later

Configure camera mounting/calibration and `PollenMap.Config` before starting vision. While the worker owns the camera, use worker commands and publications instead of calling `camera.update`, `set_pipeline`, `capture_snapshot` or `stop` from another thread.

```java
Limelight camera = new Limelight(hardwareMap);
PollenMap map = new PollenMap(camera);
LimelightWorker vision = new LimelightWorker(camera, map);
ButineWorker planner = new ButineWorker();
vision.start();                  // Asynchronous; inspect snapshot().status/error/connected.
planner.start();                 // Sleeps until a request arrives.

// Optional, after starting workers; save and restore this result in finally.
ThreadAffinity.Result loopBinding = ThreadAffinity.bind_current_thread(0);

PedroButineDrive drive = new PedroButineDrive(follower);
ButineRunner collection = new ButineRunner(map, drive, planner);
LimelightRunner corrections = new LimelightRunner(vision, drive);

// In the existing OpMode loop:
follower.update();
vision.submit_pose(drive.pose());
collection.update();
// When doing a stationary correction instead, corrections.update() submits odometry itself.

// One request between runs; with a planner, true means accepted into PLANNING.
collection.run_butine(depot, params, 1.0);

// finally/stop, after ending any active collection:
collection.stop();
boolean plannerStopped = planner.stop();
boolean visionStopped = vision.stop();
boolean restored = ThreadAffinity.restore_current_thread(loopBinding);
// Report any false cleanup result: a worker may still be finishing an SDK call/solve.
```

This is a usage sketch for later wiring, not a new autonomous routine. Create the runner objects once, call `run_butine` once per requested collection, and keep `update` in the loop. Do not submit a fresh collection request every tick. The OpMode remains the only owner of drive/follower/motor operations. Caller-controlled shooting is unchanged.

### Vision behavior

The vision thread targets a 10 ms period, with camera health reads about every 250 ms. SDK polling already has its own thread; the new worker primarily moves our Java projection/mapping and slower status/command operations away from control. It does not add a duplicate USB polling implementation or a backlog of catch-up tasks.

Odometry is submitted as an immutable pose with its original monotonic sample time. A bounded history with 10 ms buckets retains samples across slow status I/O; null/nonfinite or old odometry cannot supply fresh map-clearing evidence. Freshness in immutable camera publications continues to expire even when the worker stalls. Camera pipeline requests coalesce; snapshot commands have a bounded pending slot. Read-back/acknowledgement distinguishes a queued pipeline request from an applied one.

Map snapshot/removal/injection operations are synchronized, with no map lock held during SDK I/O. Public calibration/map configuration fields remain startup configuration. Pause mapping before injecting synthetic targets with `set_mapping_enabled(false)` and resume it when returning to live detection. The stationary diagnostics use a clearly labelled synthetic pose, so their mapped positions must not be used to drive.

### Planning behavior

The current planner has finite iteration/stagnation/local-search limits and **no wall-clock compute cap**; this change preserves that choice. Each request copies candidates, parameters, depot and start pose. A newer request replaces pending work and invalidates older results. The worker sleeps when idle and requests CPU 2 for its thread.

The optional collection runner introduces `PLANNING`, during which it holds/stops the drive and only polls for publication. Before accepting a result, it checks request identity, current pose, target IDs/positions and remaining match time. It rebuilds leg timing from the actual current start pose and checks travel plus scoring overhead against the elapsed match budget. A stale initial plan does not dispatch; failed replanning uses the existing return-home recovery. Cancellation cannot dispatch a later result.

`ButineWorker.Result.executable(...)` offers the same request/pose/version/time gates for direct callers; use a meaningful map revision when supplying your own version. The collection runner checks the selected targets directly instead of invalidating every solve whenever the camera publishes another frame. This avoids starving planning at a 100 Hz camera rate while still rejecting changed targets.

A finite in-progress solve may finish after cancellation because the planner does not check interrupts inside its search. Cancellation discards the result; `stop()` waits only a bounded interval and returns false if the thread is still finishing. A replacement thread cannot overlap that old owner. The same principle applies to uninterruptible camera I/O: only its original vision owner calls camera cleanup. Main-loop failures use nonblocking stop requests; bounded joins belong to cleanup.

## Running the stationary CPU test

File: `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java`.

1. Remove `@Disabled` deliberately, rebuild/install the Robot Controller app, and select **Limelight CPU thread test**.
2. The test creates no follower, motors, shooter or intake. A configured `limelight` is optional; camera failure is displayed while CPU/planner diagnostics continue.
3. Start the OpMode. **A** requests one synthetic Butine solve; **B** cancels it; **X** toggles the control loop's CPU 0 binding/restoration.
4. Use **dpad left/right** to page through all sampled application threads. Managed control/vision/planner/sampler rows and the highest CPU users are also shown.
5. Stop normally and check logs for any failed worker shutdown or affinity restoration. Restore `@Disabled` when finished.

The test samples `/proc` and `/sys` at 2 Hz on a separate sleeping diagnostics thread and displays cached data. That deliberately avoids filesystem scanning every control tick. Telemetry has its own cost; reported loop timing is this stationary test's loop, not a measurement of a competition autonomous or the Android UI thread. Changing affinity is a setup action; the test resets loop timing after the X transition to avoid treating subprocess setup cost as steady-state jitter.

| Reading | Meaning |
|---|---|
| Native TID/name | Kernel thread identity; includes FTC/SDK/ART workers in this application |
| Allowed CPU list/mask | Effective eligible CPUs from `/proc/self/task/TID/status`; this is assignment evidence |
| Last CPU | Processor from `/proc/.../stat`; last observed execution location, not an affinity mask |
| Thread CPU % | Delta user+kernel CPU ticks divided by actual clock-tick rate and monotonic elapsed time; 100% is one fully occupied core |
| Core CPU % | Delta busy/total counters across the whole system; includes other apps/Android, treats I/O wait as idle |
| State/priority/nice | Native thread scheduler metadata, independent of Java's cached thread priority |
| Core frequency/online | Available sysfs frequency/online values; a reported frequency can be a policy request rather than a physical-clock measurement |
| RSS/virtual memory | Process resident/virtual size from `/proc/self/status` |
| Java heap used/committed/max | Java heap values; excludes native SDK/camera allocations |
| Thermal zones | Available kernel thermal-zone type and temperature; raw readings remain in `CpuStats` for vendor interpretation |
| Worker timing | Vision wall/CPU time and overruns; planner completed/discarded counts and solve duration |
| Loop timing | Last, mean and maximum period of the stationary test loop |

First CPU percentages need a second sample. Missing, denied, reset or malformed counters are **unavailable**, not invented zero usage. Thread IDs can be reused, so the sampler matches start times before calculating deltas. CPU accounting uses `sysconf(_SC_CLK_TCK)` rather than assuming a tick rate or confusing it with processor frequency. Guest counters are already included in user/nice and are not added twice. The raw thermal field preserves visibility into vendor-specific drivers.

Counter references: [thread stat fields](https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html), [allowed CPU status fields](https://man7.org/linux/man-pages/man5/proc_pid_status.5.html), [system CPU counters](https://man7.org/linux/man-pages/man5/proc_stat.5.html), and [Android sysconf](https://developer.android.com/reference/android/system/Os#sysconf(int)).

## Verification and limits

Run `powershell -ExecutionPolicy Bypass -File tools\limelight-audit\run.ps1` for SDK-signature compilation and standalone regression checks. Tests live under `tests/java`; injected camera transports live outside Android source sets in `tools/limelight-audit/fixtures`. The new checks exercise affinity read-back/failures/restoration, CPU counter arithmetic and unavailable nodes, copied planner inputs, supersession/deadlines, caller-only drive access, vision ownership and shutdown under blocked I/O.

The combined audit passes **6,528 checks**: the existing 6,350, plus 62 CPU utility checks, 31 planner-worker checks, 17 asynchronous collection-runner checks and 68 vision-worker checks. All production classes and the four bench OpModes also compile with Java 8 source/target against the installed Android 35, FTC 12.0.0 and Pedro core 3.0.1 signatures, using the project's existing compiled constants for the legacy follower-based bench classes.

No Control Hub is connected through ADB, so physical core assignment, allowed CPU masks, sensor availability and performance gains are not established by desktop tests. The full offline Gradle build still fails before compilation with `Unable to establish loopback connection`; standalone Java compilation against the installed SDK is checked separately. No APK was deployed.

Written by Codex, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.
