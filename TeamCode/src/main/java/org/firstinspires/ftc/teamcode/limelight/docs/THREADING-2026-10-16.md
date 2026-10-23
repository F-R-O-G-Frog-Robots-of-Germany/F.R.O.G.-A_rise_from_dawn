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

All production code here is Java. The 17 October competition integration now connects optional vision/planning through `CompetitionWorkers` in both competition bases, while shooter/intake/Pedro remain in their one control loop. Vision defaults enabled, the planner starts only for explicit collection use, and control affinity defaults to `-1` pending hardware measurements. The existing synchronous constructors/calls remain available. CPU numbers are zero based; `preferredCore = -1` requests no affinity change and retains the thread's inherited eligible CPU mask.

### How assignment works

Inside the target Java thread, `android.os.Process.myTid()` identifies its native Linux thread. `ThreadAffinity` runs Android's built-in Toybox `taskset -p MASK TID`, reads the effective mask back with `taskset -p TID`, and reports `applied` only on an exact match. CPU 0, 1 and 2 correspond to hexadecimal masks `1`, `2` and `4`. The helper reads the original mask before changing anything, uses argument tokens without a shell, bounds command time/output, and supports restoring that original mask on the same thread. It never uses `-a`, never changes every Robot Controller thread, and never requests root, clocks or governor changes.

Failure to read/set/verify affinity is exposed in `Result.detail` and worker telemetry. The worker still runs, and the diagnostics show the real allowed CPUs so an unverified request cannot be mistaken for a successful assignment. Kernel cpusets, offline cores or vendor permissions may limit requests. Pinning restricts where a thread can run; it does not exclude Android or SDK threads from that CPU, guarantee a faster loop, or provide real-time scheduling.

**Startup order matters:** Java/native threads can inherit their creator's current affinity. Start the vision, planner and diagnostics workers before optionally pinning the OpMode loop to CPU 0. That leaves their fallback mask at the original Android placement rather than accidentally inheriting CPU 0. Stop workers and restore the original control-thread mask in cleanup; the SDK may reuse a thread. The test follows this order and preserves an existing interrupt flag while restoring affinity.

The vision owner also initializes the camera before binding its own preferred CPU: FTC 12's `Limelight3A.start()` lazily starts its scheduled polling thread, which would otherwise inherit the worker's restricted mask. This launch ordering avoids accidentally pinning SDK polling as a side effect. The competition facade retains ownership across OpMode instances while any previous owner thread is still finishing, including after a bounded shutdown wait expires. A new OpMode falls back to ordinary control rather than creating an overlapping camera owner.

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
3. Start the OpMode. **X** cycles the four placement modes below. Keep the same workload while comparing them. **Y** switches between idle planning and repeatable synthetic planner load, clearing retained comparisons when the workload changes. **A** requests a manual synthetic solve; **B** cancels it. Manual operation is labelled separately because arbitrary button timing cannot establish an equal-load comparison.
4. Use **dpad left/right** to page through all sampled application threads. Managed control/vision/planner/sampler rows and the highest CPU users are also shown.
5. Stop normally and check logs for any failed worker shutdown or affinity restoration. Restore `@Disabled` when finished.

| Placement mode | Main thread eligible CPUs | Vision worker | Planner worker |
|---|---|---|---|
| Android baseline | Original Android mask | Original inherited mask | Original inherited mask |
| One CPU | `{0}` | CPU 1 | CPU 2 |
| Two CPUs | `{0,1}` | CPU 1 | CPU 2 |
| Three CPUs | `{0,1,2}` | CPU 1 | CPU 2 |

The two/three-CPU settings allow migration among those CPUs. They do not execute the main Java thread simultaneously on several CPUs. Holding worker assignments fixed across the restricted modes isolates changes to the main thread's eligible set, including contention where the sets overlap. The Android baseline removes all affinity requests made by this test; its inherited eligible masks can still be constrained by Android. It does not change ART, SDK or other application's existing thread policies.

Mode changes use bounded worker shutdown and refuse replacement while an old owner is alive. The main thread's original mask is restored before replacement workers are created so the camera's SDK poller and other children inherit that mask. A restricted mode must verify its requested affinity before its measurements qualify for comparison. A fresh sampled native mask contradicting the request invalidates and clears that mode's measurements. Sampling is intermittent: an unavailable reading is disclosed, and a short-lived change between samples may go unobserved. Camera-worker presence is labelled separately from the camera's connection/frame diagnostics; a running worker alone does not establish a working vision workload.

The test samples `/proc` and `/sys` at 2 Hz on a separate sleeping diagnostics thread and displays cached data. That deliberately avoids filesystem scanning every control tick. Telemetry has its own cost; reported loop timing is this stationary test's loop, not a measurement of a competition autonomous or the Android UI thread. Startup, affinity subprocesses and mode transitions are excluded; each new mode also has a three-second warmup. This warmup does not guarantee that JIT compilation, garbage collection or thermal changes have settled. Retained summaries let the operator compare visited modes without remembering the previous display; changing the workload clears them.

The loop histogram uses fixed storage and 10-microsecond buckets through 100 ms. p95/p99 use the nearest-rank definition and report bucket upper bounds; overflow is displayed explicitly instead of assigning a false 100 ms upper limit to longer pauses. Mean and maximum retain actual observed periods. Tail summaries are marked as preliminary below 1,000 measured periods. Sampling and telemetry overhead remain part of the measured loop. Repeat the same workload across modes and inspect temperatures, CPU frequencies and solve counts before attributing a change to placement.

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
| Loop timing | Last, mean, maximum and histogram p95/p99 periods of the stationary test loop, with sample count and warmup/verification status |

First CPU percentages need a second sample. Missing, denied, reset or malformed counters are **unavailable**, not invented zero usage. Thread IDs can be reused, so the sampler matches start times before calculating deltas. CPU accounting uses `sysconf(_SC_CLK_TCK)` rather than assuming a tick rate or confusing it with processor frequency. Guest counters are already included in user/nice and are not added twice. The raw thermal field preserves visibility into vendor-specific drivers.

Counter references: [thread stat fields](https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html), [allowed CPU status fields](https://man7.org/linux/man-pages/man5/proc_pid_status.5.html), [system CPU counters](https://man7.org/linux/man-pages/man5/proc_stat.5.html), and [Android sysconf](https://developer.android.com/reference/android/system/Os#sysconf(int)).

## Verification and limits

Run `powershell -ExecutionPolicy Bypass -File tools\limelight-audit\run.ps1` for SDK-signature compilation and standalone regression checks. Tests live under `tests/java`; injected camera transports live outside Android source sets in `tools/limelight-audit/fixtures`. The new checks exercise affinity read-back/failures/restoration, CPU counter arithmetic and unavailable nodes, copied planner inputs, supersession/deadlines, caller-only drive access, vision ownership and shutdown under blocked I/O.

The 17 October competition implementation passes **14,283 distinct checks**: 14,014 assertions/checks in 21 standalone programs, plus 40 camera-frame, 73 camera-runner, 68 vision-worker and 88 competition-worker fixture checks. `tools/competition-audit/run.ps1` compiles all 64 TeamCode production sources and the standalone tests with Java 8 source/target against real Android 35, FTC 12.0.0, Pedro 3.0.1 and declared Panels signatures. `tools/limelight-audit/run.ps1` separately compiles and executes camera transport fixtures. Neither script packages an APK.

The 18 October extension passes **14,389 distinct checks**: 14,064 in 22 standalone programs, 56 in the successful collection integration fixture, and the same 269 camera transport/ownership checks. All 65 production and 22 standalone test sources compile against the real SDK signatures. The new integration fixture executes two complete actual autonomous lifecycles with real workers, planner/runner and Pedro/Foresight; only camera transport, mechanism hardware and localization feedback are simulated. It checks an actual nonempty completed return, mechanism cleanup, the next authored path and shot, and final worker termination. Affinity/percentile checks use fake native commands and synthetic distributions; no desktop result demonstrates Hub performance. The full APK build still fails at Gradle startup with `Unable to establish loopback connection`.

The robot has not been built, so physical assignment, sensor behavior and robot performance remain deferred. The full offline Gradle build still fails before compilation with `Unable to establish loopback connection`; standalone Java compilation against the installed SDK is checked separately. No APK was deployed.

Written by Codex, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.
