# Multithreading, core pinning, and Ma-Douce optimization assessment

**Implementation update, 17 October 2026:** the two linked competition plans are now implemented in software. Both bases service optional vision/planning workers and own verified control-thread affinity/restoration. Vision defaults on, planning starts only for explicit collection use, and control affinity defaults to `-1` pending hardware evidence. No existing route or driver button automatically invokes collection. The remaining assessment records the source state before that implementation; its “Absent” rows and historical audit counts are not the current implementation status. Physical results remain deferred because the robot has not been built.

**Verification and benchmark update, 18 October 2026:** the successful collection integration fixture now executes two complete actual autonomous lifecycles: collection request, mechanism operation, pickup, completed depot return, mechanism cleanup, next queued path, shot and shutdown. The stationary CPU tester now compares fully unpinned application-owned workers with main-thread eligible sets of one, two or three CPUs and retains p95/p99 summaries. This adds no competition routes or driver bindings.

Current software validation: 65 production and 22 standalone test sources compiled against real SDK signatures; 14,064 standalone checks, 56 complete-collection integration checks and 269 camera-fixture checks passed (**14,389 distinct checks**). The full Gradle build remains blocked before compilation by its loopback-connection error; no APK was deployed.

Assessment date: **17 October 2026**, revised after the owner's architecture clarification. This document records the current state and comparison findings. The two requested implementation plans are separate documents linked below. Competition behavior, configuration, and Hub settings remain unchanged. The related stationary `CpuThreadTest` diagnostic was updated to show calculated Hz and the owner's explicitly assumed future-robot timing range.

## Initial assessment before competition integration

**F.R.O.G. has working background-worker infrastructure and a CPU-affinity implementation, but competition autonomous and TeleOp do not use them.** The meaningful progress is beyond a design sketch: the workers, publications, cancellation behavior, affinity verification, and diagnostics exist and pass desktop checks. The competition robot still performs its application control work on one OpMode thread.

**A successfully verified single-core affinity mask is a Linux scheduling constraint.** The JVM does not normally move that native thread outside its allowed mask. However, the mask can subsequently be changed by another affinity operation or Android system policy. Pinning provides neither exclusive ownership of a core nor a real-time timing guarantee.

Ma-Douce contains several concrete optimizations missing from the Java planner, especially cached route prefixes, partial scratch-array updates, and less allocation inside repeated searches. Their speed advantage on this robot has not been measured. Separate control frequencies, a compute-time budget, and foxd are intentional architectural differences, not missing optimizations. Ma-Douce's own competition control is not generally pinned or divided across dedicated cores either.

## Owner's decisions and current project stage

The robot has not been built. Physical placement and robot-performance measurements are therefore **deferred dependencies**, not overdue verification or evidence against the software design.

The intended architecture keeps shooter, intake, gamepads, OpMode decisions, and Pedro/follower control in one important loop. There is no requested per-subsystem scheduler or separate fixed frequency for each activity. Vision processing and route planning can execute independently around that shared loop.

Finite planner work limits without a compute-time budget are intentional. A generous compute-time guard may be considered separately in the future; it is outside the two plans. Remaining match time and result freshness still matter when deciding whether a computed route can be executed. Pedro remains the chosen follower. Reducing the maintenance burden of custom foxd code is a project advantage, not a gap to reverse.

Requested plans:

- [Competition worker integration](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Worker-Integration-Plan-2026-10-17.md)
- [Competition control-thread affinity](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Control-Thread-Affinity-Plan-2026-10-17.md)

## Scope and source versions

| Source | Assessed checkout | Version and qualification |
| --- | --- | --- |
| F.R.O.G. | `C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn` | Local `main`, HEAD `610f24ab6a0394665b49c7213f4f8f50212f19fd`, **including current uncommitted changes**. This is the user's main checkout, rather than the separate chat worktree. |
| Ma-Douce | `C:/Users/pmkoe_eque6/StudioProjects/butine test` | HEAD `84c0a016d99dfc6f6244377a599abbfd246768d1`, **including current modified and untracked source**. Its configured origin is `https://github.com/Robotic-Tech-Frox/Ma-Douce.git`. |

F.R.O.G.'s [Limelight README][frog-readme] identifies that second checkout as the source formerly named Ma-Douce. A read-only `git ls-remote origin HEAD` attempt against Ma-Douce returned **Repository not found**. Consequently, this is a comparison with the available local repository, not a claim that its checkout matches the latest remote state. Existing reports were treated as context; findings below were checked against source.

## Initial capability state, superseded by the implementation update

| Capability | Current status | Where it actually operates |
| --- | --- | --- |
| Separate vision/mapping worker | Implemented | `LimelightWorker`; exercised by tests and the disabled CPU bench OpMode. |
| Separate route-planning worker | Implemented | `ButineWorker`; optional asynchronous `ButineRunner` constructor exists. |
| Per-thread core selection | Implemented, with effective-mask read-back | Worker startup and the disabled CPU bench OpMode. |
| Competition use of those workers | **Absent** | Neither competition lifecycle starts the vision/planner workers. |
| Competition control-thread affinity | **Absent** | Neither `AbstractTeleOp` nor `AbstractAuto` binds its OpMode thread. |
| Thread/core/memory/thermal diagnostics | Implemented | `CpuStats` and a separate sampler in `CpuThreadTest`. |
| Verified assignment on a physical Control Hub | **Deferred: robot not built** | The stationary tester can assess placement on an available Hub; actual robot behavior awaits the future robot. |
| Measured improvement in competition-loop latency/jitter | **Deferred: robot not built** | Representative drive/sensor/mechanism workloads do not yet exist physically. |

There is no useful single completion percentage: the infrastructure is implemented, competition integration is a software task, and physical effectiveness is a later project stage.

### What runs on which thread today

The competition [TeleOp loop][frog-tele] clears bulk caches, reads controls, updates the follower/localization, runs mechanism logic, and prepares enabled telemetry on the same OpMode thread. [Main][frog-main] calls `Robot.update_robot(currentPose)`, and [Robot][frog-robot] calls the shooter update directly. The [autonomous lifecycle][frog-auto] executes queued actions sequentially; its waits repeatedly update the follower and shooter on that same calling thread.

Those actions can overlap mechanically: the flywheel, intake, and drivetrain can operate together. That does not mean their Java update functions execute concurrently. Similarly, `synchronized` methods in hardware wrappers and intake code do not create threads or establish that all subsystem state is safe for concurrent use.

The entire Robot Controller application is already multithreaded because Android, ART, and the FTC SDK have their own workers. The finding above concerns **F.R.O.G.'s application control work**. It does not mean the whole app occupies only one CPU.

### The implemented worker arrangement

| Component | Requested CPU, zero-based | Behavior |
| --- | --- | --- |
| Control loop in `CpuThreadTest` | CPU 0, mask `1` | Stationary diagnostic loop; binding can be toggled. |
| `LimelightWorker` | CPU 1, mask `2` | Targets a 10 ms period; health/status operations approximately every 250 ms. |
| `ButineWorker` | CPU 2, mask `4` | Sleeps until requested; solves one request at a time. |
| CPU sampler | Inherited eligible mask | Samples at approximately 2 Hz. |
| CPU 3 | No explicit assignment | No reservation for Android or other work is established. |

This arrangement is demonstrated in [CpuThreadTest][frog-cpu-test], which is currently annotated `@Disabled`. The workers start before the diagnostic control loop pins itself, avoiding accidental inheritance of its CPU-0-only mask. Competition code does not currently instantiate this arrangement.

The Driver Station name is **Limelight CPU thread test**, in group **Limelight**. Updated 18 October: X cycles an original-Android-mask baseline and main-thread eligible sets `{0}`, `{0,1}` and `{0,1,2}`. The restricted modes retain vision on CPU 1 and planning on CPU 2; the baseline removes those bindings too. A/B remain manual solve/cancel diagnostics, Y selects a repeatable idle/planner-load comparison, and dpad pages thread diagnostics. Wider eligible masks allow migration; they do not run one Java thread simultaneously on several CPUs. The test constructs no motors, follower, shooter, or intake and uses a synthetic camera pose. It can assess affinity/diagnostic overhead without the finished chassis, but cannot establish eventual robot-loop performance. Its loop also calls `idle()`, so its timing is not a prediction for the competition loop.

The [vision worker][frog-vision-worker] owns the Java camera-facing operations and mapping work, accepts bounded/coalesced commands, and publishes snapshots. The SDK already has its own Limelight poller; assigning the F.R.O.G. worker to CPU 1 does **not** assign that SDK poller, ART GC, or Limelight's onboard inference to CPU 1. Slow camera I/O can still delay this worker's next publication, although consumers retain freshness checks.

The [planner worker][frog-planner-worker] copies request inputs, replaces pending work with newer requests, and suppresses obsolete results. The optional [asynchronous collection runner][frog-runner] polls for publication and validates the result before dispatching motion. Drive/follower operations remain on the caller. The synchronous constructor remains available and still solves on the calling thread.

These are useful concurrency boundaries. They do not make existing shooter, intake, follower, or bulk-cache operations independently parallel.

## How strong is core pinning, and can the JVM override it?

### What the implementation actually does

[ThreadAffinity][frog-affinity] obtains `android.os.Process.myTid()` inside the target worker, reads its original affinity, runs `/system/bin/toybox taskset -p MASK TID`, and reads the effective mask back. `applied` becomes true only when the read-back exactly matches the requested one-bit mask. The original mask is retained for restoration. Commands have bounded output and a 750 ms timeout per invocation; they are setup/cleanup work rather than recurring control-loop work.

This targets the native Linux TID, not the unrelated Java `Thread.getId()` value. Android documents that managed and native threads are Linux threads scheduled by the kernel. Therefore a successful binding constrains the Java worker's execution too. **Calling the helper from Java does not make the binding weaker than issuing the same affinity operation from C++.** [Android JNI thread documentation](https://developer.android.com/ndk/guides/jni-tips#threads)

### Strength and limits

| Question | Assessment |
| --- | --- |
| Can normal scheduling choose another core despite a currently effective CPU-2-only mask? | No. Eligible CPU placement is constrained by the mask. |
| Does the JVM normally remap this `new Thread(...)` worker onto different native threads? | No; these Android workers are ordinary native-backed threads. Runtime/JIT activity is not an exemption from kernel affinity. |
| Is the assignment permanent or immutable? | No. Another permitted affinity operation or system policy change can alter it. |
| Is that core exclusively ours? | No. Other eligible app, SDK, Android, and kernel work can share it. |
| Can the thread still pause or wait? | Yes: preemption, locks, I/O, and runtime pauses still apply. |
| Does pinning guarantee a faster loop? | No. It can also constrain useful placement or concentrate contention. |

Linux affinity is per thread; the effective eligible set is limited by available CPUs and cpuset restrictions. Permission failures and unusable masks are possible. Root is not inherently necessary to change an owned thread's affinity, but the actual device must permit the operation. [Linux affinity semantics and permissions](https://man7.org/linux/man-pages/man2/sched_setaffinity.2.html)

Android explicitly describes a foreground-exclusive-core mechanism where affinity can be reset when a process leaves the foreground. That documents a possible OS policy intervention, **not evidence that this particular Hub does so**. [Android `getExclusiveCores`](https://developer.android.com/reference/android/os/Process#getExclusiveCores())

Affinity and priority are distinct. F.R.O.G. does not request `SCHED_FIFO` or raise worker priority. Android's documented possibility of the runtime resetting a Linux **nice priority** is a separate issue from CPU affinity. [Android `setThreadPriority`](https://developer.android.com/reference/android/os/Process#setThreadPriority(int,%20int))

GC/runtime coordination can still affect a pinned managed thread's latency. Even native code can encounter runtime suspension when it returns through JNI. This changes when work progresses, rather than granting permission to execute outside an unchanged mask. [Android JNI thread behavior](https://developer.android.com/ndk/guides/jni-tips#threads), [ART GC diagnostics](https://source.android.com/docs/core/runtime/gc-debug)

### Limits of F.R.O.G.'s current verification

The workers retain the result of their startup binding. Their `affinityApplied`/`cpuCore` statistics describe that verified startup operation; they are **not continuous proof** that the mask has remained unchanged. [CpuStats][frog-cpu-stats] independently reads `Cpus_allowed_list` and `Cpus_allowed`. Its `lastCpu` field only says where a thread last ran and cannot prove pinning.

A failed request is visible and the worker continues. That means the code guarantees an attempted and checked assignment, not successful pinning on every device. `preferredCore = -1` makes no affinity change and retains the inherited mask; it does not forcibly restore all CPUs.

Shutdown also has practical limits. Planner `stop()` joins for 250 ms, while an in-progress solve does not check cancellation inside the search. Its result is invalidated, but CPU work can continue until the solve returns. Vision shutdown similarly cannot guarantee immediate completion of blocked camera I/O. Same-owner restart guards prevent overlapping replacement workers. These properties are covered by desktop checks; they are not hard execution-time bounds.

## Optimizations in Ma-Douce that F.R.O.G. lacks or only partially matches

### Cached route prefixes and partial move evaluation: confirmed missing

Ma-Douce's [planner][ma-prefix] builds a `Prefix` containing accumulated time, heading, and last node for each route prefix. When 2-opt reverses a segment starting at index `i`, evaluation resumes at `i`. Or-opt resumes at the earlier splice point. Accepted changes rebuild the prefix; rejected trials reuse it.

F.R.O.G.'s [sequence evaluator][frog-sequence] starts at the beginning for every trial. Its [local search][frog-local-search] calls that full evaluator after each reversal or relocation.

**Implication:** F.R.O.G. repeats calculation for unchanged leading legs in a frequently repeated part of the planner. The missing reuse is confirmed in source. The saved runtime and its share of total solve time are unmeasured; larger tours offer more opportunity than very short runs.

Ma-Douce also rewrites only the changed suffix of its Or-opt scratch array. F.R.O.G. reconstructs the remainder and trial sequences from the front for each insertion. This is a second, related reduction in memory work in the C++ implementation. It is not a different route objective or an additional search algorithm.

### Less allocation inside repeated planner searches: confirmed structural difference

Ma-Douce uses fixed-size native scratch arrays and value structures in `optimize_run`, `optimize_sequence`, `split_tour`, and cross-run polishing. F.R.O.G. allocates Java arrays in the equivalent paths: `Arrays.copyOf` for short-run permutations, trial/remainder arrays for local search, several two-dimensional DP tables for splitting, and new `Solution` arrays. [C++ planner][ma-prefix], [Java ordering/search][frog-local-search], [Java split][frog-split]

**Implication:** the Java search creates more temporary managed objects, including in helpers called repeatedly. That is a plausible source of allocation and GC cost. It is not proof that GC currently dominates, nor that all C++ planner activity is allocation-free. Native input/output containers also allocate, and Java request copies/publication boundaries provide concurrency benefits that need to be distinguished from disposable search scratch.

## Intentional architecture differences

### One shared important control loop

Ma-Douce's [task registration][ma-protocols] requests, for example, gamepad/transfer updates at 20 ms, aimbot at 4 ms, shooter/turret at 5 ms, and general telemetry/Panels at 200 ms. Its foxd drive registration uses 10 ms. Those are requested intervals, not proof those rates are achieved: [scheduler execution][ma-scheduler] invokes each selected callback synchronously, so slow work can delay other tasks.

F.R.O.G. updates the follower and shooter on each competition control iteration. Pose-dependent targeting and ballistic solution work can also run each iteration. Telemetry transmission has a 100 ms interval, but enabled loop-timing values are prepared and `update()` is called every loop. `COMP_MODE` is true and general diagnostic telemetry/Panels are disabled, while `LOOP_TIME_TELEMETRY` remains true. [TeleOp loop][frog-tele], [Shooter update][frog-shooter], [competition flags][frog-comp-config]

**Assessment:** F.R.O.G.'s shared important loop is intentional and well matched to tightly coupled control. It gives explicit execution order, shared bulk-cache ownership, and a single place to coordinate driving and feeding. Ma-Douce's requested task periods are not inherently superior. Intake's existing 300 ms sensor cache and the vision worker's health polling are separate existing device/worker policies; these are not a request to schedule each important subsystem at its own rate.

On one core, multiple CPU-bound control threads do not add compute capacity. They can add scheduling, communication, cache, and lock costs. Multiple threads can still help on one core when one is blocked on independent I/O. Across multiple cores, the potential gain depends on independent work: splitting `read sensors -> regulate shooter -> command motor` across cores leaves the dependency chain intact. If stages wait for each other's current-cycle result, the end-to-end path is still sequential, with additional handoffs.

That does **not** mean multiple cores inherently force the shooter to wait. A producer can publish a coherent timestamped snapshot, and a consumer can read the latest completed publication without awaiting a new one. This trades freshest-possible data for overlap; acceptable age depends on the consumer. Vision and route planning are better candidates for this than the tightly coupled motor-control chain. In the existing asynchronous runner, drive deliberately stops during `PLANNING`, while the rest of the important loop can continue. Background computation does not currently mean driving while planning.

There are two distinct issues: a deliberately older coherent snapshot is a freshness policy; unsynchronized concurrent reads/writes are a data race. Data races are not generally harmless: they can expose stale values or inconsistent groups of fields. A final array reference is also not an immutable array. Publication, ownership, and bounded snapshot access remain necessary, even if all app threads are pinned to one core. [Android SMP guidance](https://developer.android.com/training/articles/smp), [Java memory-model ordering](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.5)

The current shared loop gives ordered updates, not a physically simultaneous measurement of every sensor: reads can occur at different times and some values are cached. Separate cores also do not remove serialization on a shared device, bus, or SDK lock. These are reasons to preserve clear ownership and measure age/latency, rather than assume either thread count automatically wins.

### Loop Hz, shared caches, and a provisional timing estimate

The premise that the Control Hub cores share no cache needs correcting. REV specifies an **RK3328 with four Cortex-A53 cores**; Arm describes per-core L1 memory systems and a **shared L2 cache**. The saved Hub fingerprints also identify `rk3328_box`. This identifies the relevant architecture; cache sizes and actual transfer costs were not measured here. [REV Control Hub manual, page 3](https://revrobotics.eu/content/docs/REV-31-1595-UM.pdf), [Arm Cortex-A53 specifications](https://www.arm.com/products/silicon-ip-cpu/cortex-a/cortex-a53)

A consumer on another core can still incur cache-line fills and coherence traffic. Repeatedly modifying the same cache lines can be expensive, including false sharing between unrelated fields. That is different from requiring a manual copy of all shared data into a separate memory space. Hardware coherence and Java's publication rules address different parts of the problem: cache coherence alone does not make a group of mutable fields an atomic snapshot. The engineering inference is that small, infrequent completed-result publications can be cheaper than repeatedly running a large solve on the control core; the actual crossover cost remains unmeasured. [Arm cache-coherence explanation](https://developer.arm.com/community/arm-community-blogs/b/architectures-and-processors-blog/posts/extended-system-coherency---part-1---cache-coherency-fundamentals)

There is **no universal Hz threshold** above which separate cores win. For a required rate `f`, the full-cycle time allowance is `1000 / f` milliseconds:

| Reference loop rate | Full period | Interpretation |
| --- | ---: | --- |
| 100 Hz | 10 ms | One important loop is reasonable if the full cycle fits with suitable margin. |
| About 167 Hz | About 6 ms | Nominal planning point used below, not a required fixed rate. |
| 200 Hz | 5 ms | A 6 ms cycle already exceeds this allowance. |
| 250 Hz | 4 ms | Requires a complete 4 ms cycle, with no margin at exactly 4 ms. |
| 500 Hz | 2 ms | Tight allowance; extra cores help only where work can overlap. |
| 1,000 Hz | 1 ms | A small dependent loop could still fit one core; this is not a F.R.O.G. performance claim. |

These are comparison points, not proposed per-process frequencies or a request to rate-limit the important loop. At any of them, retain one important ownership loop when it provides acceptable command latency and sample age. Independent work can justify other cores even at 100 Hz; tightly dependent work might remain together at 1,000 Hz if it fits. A long inline planner solve or blocking camera call can spoil either case.

For two genuinely independent computations, a simplified comparison is `serial time = S + A + B` versus `parallel time = S + max(A, B) + H`, where `S` is sequential work and `H` includes dispatch, publication, synchronization, and extra cache traffic. The potential saving is `min(A, B) - H`. When B requires A's new result first, the `max` simplification does not apply: splitting that dependency chain introduces handoffs without removing its sequential work. This is an explanatory model, not a measured timing equation for the Hub.

The owner's **4-6 ms** estimate translates to **166.7-250 Hz**, with 5 ms corresponding to 200 Hz. My separate provisional engineering estimate is **5-8 ms for a normal full competition cycle**, or **125-200 Hz**, using **6 ms / about 167 Hz** as a planning midpoint. This estimate comes from the existing bulk/localization/control structure and periodic sensor I/O; it is not a benchmark or a guarantee. The 300 ms distance-sensor refresh can create longer cycles, and camera, planner, and diagnostics configurations affect the load. There is no defensible numerical worst-case estimate before the hardware workload exists.

At 200 Hz, the owner's assumed cycle range gives **-1 to +1 ms** of period headroom; my range gives **-3 to 0 ms**. Negative headroom means exceeding the nominal 5 ms allowance. This supports treating 200 Hz as a useful comparison point, not assuming it is sustained or changing the design to hit it. At these estimated rates I would keep the important loop on one thread and put independent vision/planning work on other cores. That is an architectural judgment, not evidence that forced control-thread pinning will improve it over Android placement.

[LoopTiming][frog-loop-timing] currently reports wall-clock work and start-to-start cycle time, with instantaneous `Hz = 1000 / cycleMs`. Work includes I/O, lock waits, and descheduling; it is **not CPU utilization**. The usual outer telemetry lies after `end_work()` and enters the following full-cycle time; telemetry inside hooks remains inside work. Autonomous `idle()` and action-transition work can also enter cycle time. The stationary tester now includes p95/p99 distributions; competition `LoopTiming` still does not report percentiles or individual stage costs. Future robot assessment should consider full-cycle distributions, overruns, sensor age, and sensor-to-command delay, rather than only average Hz. Real competition measurements remain deferred until the robot exists.

The stationary tester calculates last-cycle, mean-period, and slowest-observed Hz from its own loop timings, with retained per-mode summaries and p95/p99 histogram estimates. Startup, placement transitions and an explicit warmup are excluded. It also shows the owner's 4-6 ms assumption and the implied 200 Hz headroom under a separate **not measured** label. Those assumptions do not replace real measurements, and no display claims a one-core/multicore crossover.

### Work-based planner limits without a compute-time budget

Ma-Douce has `compute_budget_ms`, currently 45 ms in its modified local [header][ma-budget-config]. Its [improvement loop][ma-budget-loop] checks elapsed time before another restart. F.R.O.G. uses iteration, stagnation, and local-search-pass limits and has **no wall-clock compute cap**.

This is a real difference in compute control, rather than a demonstrated speed optimization. Ma-Douce's check happens after initial construction and between whole improvement iterations; it does not interrupt long initialization, local search, or polishing. Its header's description of a hard cap is therefore stronger than the code guarantees. F.R.O.G.'s smaller default work limits are also different: 64 iterations, 8 stagnant attempts, and 4 local-search passes, versus Ma-Douce's current 20,000 iteration ceiling, 40 stagnant attempts, and separate fixed local-search guards. Default solve times cannot fairly isolate a language or optimization advantage.

F.R.O.G.'s existing threading documentation explicitly records the choice to retain finite work limits without a wall-clock cap. This assessment records that distinction and does not propose changing it. [Current threading documentation][frog-threading-doc]

The scope of factorial search matters. The current default capacity and exact-order limit are both 5, so one fixed five-target run has only `5! = 120` orderings. The full default candidate set is 25: `25!` is approximately `1.55 x 10^25`, before considering subset/run assignment. Small-run exact search is cheap in scale; globally exhaustive search is a different problem. The current planner combines small exact searches with bounded heuristics, rather than enumerating every full-field permutation. These counts establish growth, not a measured runtime. A future generous compute guard would serve as an exceptional containment policy, not a prerequisite for the chosen work-based search.

### Pedro as the deliberate maintained follower

Ma-Douce's [foxd paths][ma-paths] stores sampled points/tangents in a fixed-capacity store and provides a reusable transient slot. Its follower uses these precomputed tangents for curvature-related work. F.R.O.G. uses Pedro 3/Foresight; autonomous paths are already authored during INIT, and the optional [Butine drive adapter][frog-drive] builds a path when dispatching a leg.

The project deliberately uses Pedro rather than maintaining foxd. Its existing path authoring during INIT already keeps that construction outside the competition loop. Foxd's fixed storage is an implementation detail of the alternative stack, not a requirement F.R.O.G. lacks. For this project, avoiding a custom follower, custom numerical/control helpers, and associated bridge maintenance is a concrete advantage. It does not require a claim of measured faster physical tracking, and neither requested plan changes the follower stack.

## Where F.R.O.G. is superior to the inspected Ma-Douce code

These are specific correctness, lifecycle, observability, and maintenance advantages. They are not measured robot-speed claims.

| Area | F.R.O.G. advantage | Evidence and qualification |
| --- | --- | --- |
| Planner start position | Models the actual start separately from the depot, including first-run travel. | [Java solve][frog-solve] and [runner][frog-runner] use the live start. The inspected [Ma-Douce solver][ma-solve] takes a depot only; syncing its drive cursor does not change that cost model. |
| Candidate selection | Scans the full input while retaining a bounded best set; rejects duplicate IDs and nonfinite inputs. | [Java selection][frog-candidates] avoids the [C++ first-64-entry scan limit][ma-candidates]. This is a selection/correctness improvement; scanning more input is not automatically faster. |
| Numeric robustness | Explicit finite pose/settings/kinematics contracts, stable distance calculation, and overflow-aware motion arithmetic. | [Java validation][frog-validation] and the planner's `Math.hypot` use. This reduces invalid-output cases; no CPU speed claim. |
| Affinity handling | Saves original mask, verifies exact effective mask, and verifies restoration. | [Java helper][frog-affinity]; Ma-Douce's separate affinity getter does not check its read-call result. |
| Worker ownership and cancellation | Copied requests, latest-request publication checks, single-owner restart guards, and bounded cleanup waits. | [Planner worker][frog-planner-worker], [vision worker][frog-vision-worker]. An active solve or SDK call may still outlive the cleanup wait. |
| Asynchronous plan acceptance | Checks current pose, request identity, target presence, and elapsed match allowance before dispatch. | [Runner acceptance][frog-plan-acceptance]. This preserves execution validity without imposing a compute-time cap. |
| CPU diagnostics | Separates allowed CPU mask/list from last CPU; reports native TIDs, core load/frequency, memory, and thermal data. | [CpuStats][frog-cpu-stats]. Startup pin success is not misrepresented as continuous mask verification. |
| Maintenance scope | Direct Java SDK/Pedro integration with a small Butine drive adapter; the project does not own another follower stack. | [Pedro adapter][frog-drive]. This is a better fit for the owner's maintenance goals than the inspected custom foxd implementation. |

Plan/result arrays currently have public `final` references. The worker's producer does not mutate a result after publication, but consumers must also treat it as read-only; `final` alone does not prevent element mutation. The integration plan makes that ownership contract explicit.

## Optimizations that should not be counted as missing

| Ma-Douce mechanism | F.R.O.G. situation | Assessment |
| --- | --- | --- |
| Manual Hub bulk reads/caching | Competition loops use MANUAL caching and clear all discovered Hubs before sensor work. | Already present. |
| Avoid unchanged motor writes | `CachedMotor` filters power writes; `CachedServo` filters position writes. Exact motor stops bypass the change threshold. | Already present for the wrapped mechanisms. This does not establish the behavior of every Pedro drivetrain write. |
| Precompute planner pairwise travel data | Java `Context` has straight-time, direction, and degenerate-leg tables. | Already present; prefix reuse is the distinct missing optimization. |
| Candidate/run limits, exact small-run ordering, 2-opt/Or-opt, split DP, cross-run search, stagnation stopping | Implemented in the Java planner. | Already ported, though limits and some semantics differ. |
| Author competition paths before the active loop | F.R.O.G. calls `build_autonomous()` during INIT. | Already present. |
| Suppress expensive presentation work | Competition flags disable Panels/general diagnostics/video requests. The video hook currently creates no camera. | Already present, with loop-timing telemetry explicitly retained. |
| Cached battery voltage (500 ms in Ma-Douce) | The assessed F.R.O.G. application code does not perform equivalent repeated voltage reads. Third-party internals are outside this claim. | No demonstrated application-level voltage-read bottleneck to remove. |
| JNI method/buffer caching | Ma-Douce uses it for its C++/Java bridges; F.R.O.G. calls Java SDK/Pedro APIs directly. | Bridge-specific overhead reduction, not a missing bridge optimization in F.R.O.G. |
| Performance-governor script | F.R.O.G. already has Control Hub tuning tooling and saved measurements. | Not missing as project tooling; present device settings were not read during this assessment. |

Relevant source: [cached motor][frog-cached-motor], [cached servo][frog-cached-servo], [Java planner context][frog-context], [Ma-Douce cached battery][ma-battery], [existing Hub measurements][frog-hub-benchmark].

## What Ma-Douce does not demonstrate

Ma-Douce's [noyau helper][ma-affinity] uses `sched_setaffinity` with the native TID, giving the same category of placement constraint as F.R.O.G.'s Toybox approach. Its native call avoids spawning a helper process. That difference affects setup cost, not a stronger steady-state bond to the core.

The inspected calls to pinning and real-time scheduling are in `noyau_test` and `vue_stress_test`, rather than competition robot startup. The ordinary task scheduler is cooperative. A function named `register_task` is not evidence of parallel execution. Ma-Douce also has explicit thread stress tests, but their existence does not establish a deployed competition thread layout.

`SCHED_FIFO` is attempted by Ma-Douce's tests and can fail without suitable privileges. A request is not proof that real-time policy took effect. Its helper's failure path logs a fallback but does not itself perform a separate `SCHED_OTHER` assignment. Neither project establishes exclusive CPU isolation.

F.R.O.G. is stronger in some defensive details: its affinity helper saves/restores the original mask and checks exact read-back, while Ma-Douce's pin helper returns the set-call result and leaves verification to separate calls. Ma-Douce's `get_affinity_core` does not check the read syscall's return value. Its generic registered-thread shutdown uses `SIGTERM`/`pthread_exit` and blocking joins; this is not evidence of better bounded shutdown than F.R.O.G.'s cancellation and owner-lifetime checks. [Ma-Douce thread handling][ma-thread-shutdown]

The Ma-Douce temperature helper also contains a Snapdragon-oriented comment. F.R.O.G.'s stored Hub measurements identify Rockchip interfaces. That comment is not evidence about the actual Hub's chip or thermal limits.

## Verification and evidence limits

The existing `tools/limelight-audit/run.ps1` was run against the assessed F.R.O.G. working tree. It completed successfully:

| Existing check group | Passed checks/assertions |
| --- | ---: |
| Butine regression | 1,828 |
| Planner audit | 3,939 |
| Vision checks and audit | 438 |
| Runner checks and audit | 61 |
| CPU utilities | 62 |
| Planner worker | 31 |
| Asynchronous runner | 17 |
| Camera frame and runner fixtures | 113 |
| Limelight worker fixture | 68 |
| **Total** | **6,557** |

The script compiles the Limelight package against cached Android 35, FTC 12.0.0, and Pedro core 3.0.1 signatures, and separately runs camera transport fixtures. This verifies software contracts, not real Android command permissions, processor placement, motor timing, or physical camera behavior. The 62 CPU utility checks use simulated command/filesystem inputs. No new tests were authored, no APK was deployed, and no full Android Gradle build or Ma-Douce native build was performed.

The planner audit reported 100 small-field exhaustive oracles, 34,444 oracle states, and 8 accepted heuristic haul gaps. Its maximum observed desktop solve was approximately **589 ms** in this run. This is a host-side test observation, not a Control Hub prediction or a worst-case bound. It does reinforce why moving planning away from control and reducing total planner work are separate concerns.

F.R.O.G.'s saved [9 October Hub comparison][frog-hub-benchmark] measures a SHA-1 workload, not pinned robot loops, and does not measure the performance-governor improvement against the original governor. It cannot substantiate a multithreading speedup. No pinned/unpinned latency distribution, sustained placement trace, or matched Ma-Douce/F.R.O.G. robot benchmark was found in the inspected diagnostics and saved results.

The supported conclusion is that **the threading and affinity building blocks exist and passed substantial desktop checks; competition adoption is the next software scope, and physical measurements await the robot**. The missing planner optimizations above are established by source comparison. Shared-loop control, work-based search limits, and Pedro remain intentional choices. The two new documents are plans only; this revision did not rerun the earlier 6,557-check audit or implement those competition plans. The stationary tester's added calculations are diagnostic changes only.

## Source links

Links refer to the assessed local working files, which may change after this report. Ma-Douce links include a path containing a space.

[frog-readme]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/README.md:3>
[frog-tele]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:90>
[frog-main]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:94>
[frog-robot]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:78>
[frog-auto]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:47>
[frog-cpu-test]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java:31>
[frog-vision-worker]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightWorker.java:307>
[frog-planner-worker]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineWorker.java:219>
[frog-runner]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineRunner.java:74>
[frog-affinity]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ThreadAffinity.java:44>
[frog-cpu-stats]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/CpuStats.java:65>
[frog-sequence]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:409>
[frog-local-search]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:436>
[frog-split]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:623>
[frog-shooter]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:386>
[frog-comp-config]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:11>
[frog-threading-doc]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/THREADING-2026-10-16.md:75>
[frog-drive]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/PedroButineDrive.java:29>
[frog-cached-motor]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java:27>
[frog-cached-servo]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java:20>
[frog-context]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:342>
[frog-loop-timing]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LoopTiming.java:3>
[frog-solve]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:222>
[frog-candidates]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:381>
[frog-validation]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:304>
[frog-plan-acceptance]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineRunner.java:168>
[frog-hub-benchmark]: <C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Control-Hub-Benchmark-2026-10-09.md:7>
[ma-prefix]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/logic/butine.cpp:98>
[ma-solve]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/logic/butine.cpp:762>
[ma-candidates]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/logic/butine.cpp:779>
[ma-protocols]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/header/core/utils/protocols.h:202>
[ma-scheduler]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/control/task_scheduler.cpp:264>
[ma-budget-config]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/header/core/logic/butine.h:136>
[ma-budget-loop]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/logic/butine.cpp:884>
[ma-paths]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/foxd/paths.cpp:7>
[ma-battery]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/foxd/adapters/lynx_battery.cpp:21>
[ma-affinity]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/control/noyau.cpp:21>
[ma-thread-shutdown]: <C:/Users/pmkoe_eque6/StudioProjects/butine test/TeamCode/src/main/cpp/teamcode/sources/core/control/task_scheduler.cpp:425>
