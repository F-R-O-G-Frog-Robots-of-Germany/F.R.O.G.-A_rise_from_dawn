# Thread-to-core pinning on the F.R.O.G. Control Hub

The robot’s OpMode loop repeatedly reads inputs, updates the follower and mechanisms, and issues motor or servo commands. A long interval between updates can leave the previous command in effect longer. The engineering question is whether restricting where that loop runs reduces those long intervals under the same workload. The REV Control Hub (REV-31-1595) uses a Rockchip RK3328 with four Cortex-A53 cores. [REV Control Hub specifications][rev]

## What Linux pins

The Robot Controller app is a process containing multiple threads. Each of its native Linux threads has a thread ID (TID) and a **CPU affinity mask**, the set of logical CPUs on which it is eligible to execute. If that set contains exactly one CPU, the thread is **pinned**. When the thread is runnable, Linux can schedule it only on that CPU while the restriction is in force. It can still be preempted, block on I/O or a lock, or wait while other work runs on the same CPU. An affinity mask provides a placement constraint. The scheduler still decides when the thread runs. [Linux affinity and scheduling semantics][linux]

The rightmost bit represents CPU 0. To construct a mask, set bit `i` for each eligible CPU `i`, or bitwise-OR the individual values `1 << i`.

| CPU set | Binary mask | Hex mask |
| --- | --- | --- |
| CPU 0 | `0001` | `0x1` |
| CPU 1 | `0010` | `0x2` |
| CPU 2 | `0100` | `0x4` |
| CPU 3 | `1000` | `0x8` |
| CPUs 0 and 1 | `0011` | `0x3` |
| CPUs 0, 1 and 2 | `0111` | `0x7` |
| All four CPUs | `1111` | `0xf` |

With `0x3`, a single thread can run on CPU 0, pause, and resume on CPU 1. That move is a **CPU migration**. The thread still executes on one CPU at a time. Simultaneous execution on different CPUs requires distinct runnable threads. A multi-bit mask gives the scheduler more placement options. Linux can narrow a requested mask according to CPU availability and Android’s **cpuset** restrictions, which limit the CPUs available to a group of tasks. A request with no usable CPU can fail. The **effective mask** is the set returned by the kernel after the request. [Linux affinity semantics and errors][linux]

## How the robot applies affinity

1. **Identify the actual thread.** From inside the thread being configured, `ThreadAffinity` calls `android.os.Process.myTid()`. The native TID identifies the Linux scheduling target. A Java thread ID identifies the thread to the Java runtime, and a process ID would select the thread group leader in this interface. The call therefore runs inside `runOpMode()` for control and inside each worker for its own placement. [Android TID API][tid] · [Linux per-thread affinity][linux]
2. **Save, set and verify.** The Java helper launches Android’s `/system/bin/toybox taskset` with an argument list. `taskset -p TID` first reads the original mask. `taskset -p MASK TID` requests the new hexadecimal mask, for example `1` for CPU 0. A separate query reads the mask again. The helper reports success only if that returned mask exactly matches the request. It bounds subprocess waiting and performs these calls during setup or cleanup, outside the repeated control work. [ThreadAffinity.java][affinity] · [Toybox taskset source][toybox]
3. **Create threads before narrowing their creator’s mask.** A new native thread inherits a copy of its creator’s current affinity. If the OpMode pinned itself first, a worker it created could start out restricted to CPU 0. The competition bases launch any enabled workers before pinning the OpMode. The vision worker also initializes the camera and its SDK poller before applying its own CPU preference. [Linux thread inheritance][inheritance] · [AbstractAuto.java][auto] · [AbstractTeleOp.java][teleop] · [LimelightWorker.java][vision]
4. **Restore the saved mask on exit.** The control wrapper keeps the original result for the OpMode’s `finally` cleanup. An unverified set can still have changed the mask, so the wrapper attempts recovery immediately and records unknown placement if verification of restoration fails. The original mask may have been narrower than `0xf`, so restoration uses the saved value. The setting `-1` skips the affinity request and keeps the inherited mask. [ControlThreadAffinity.java][control] · [ThreadAffinity.java][affinity]

To inspect placement on the Hub, read the target thread’s `Cpus_allowed_list` in `/proc/<PID>/task/<TID>/status` or query that TID with `taskset -p`. For example, `Cpus_allowed_list: 0` confirms CPU 0 was the only eligible CPU at the time of the read. A `lastCpu` sample of 0 records where the thread most recently ran and is also possible with a wider mask. If placement matters across a trial, sample the allowed mask periodically. Android policy or a later affinity request can change it after the initial check. [CpuStats.java][cpustats] · [Linux status fields][procstatus] · [Linux stat fields][procstat]

## What runs where in F.R.O.G.

| Thread | Requested CPU | When the request applies |
| --- | --- | --- |
| Competition autonomous or TeleOp OpMode | CPU 0 | During INIT via `CompConfig.CONTROL_CORE = 0` |
| Limelight vision worker on the Hub | CPU 1 | When the optional vision worker starts |
| Butine route-planning worker | CPU 2 | When the optional planner starts |

The current competition configuration disables optional assistance and vision. It requests CPU 0 for the OpMode, and the two worker preferences describe a layout available when those features are enabled. CPU 3 is available to any thread whose mask includes it. CPU numbers in the table are requests. The effective masks establish which assignments took effect. [CompConfig.java][config] · [LimelightWorker.java][vision] · [ButineWorker.java][planner]

The OpMode still owns the follower and mechanism commands. When enabled, the workers produce observations or candidate routes for it to consume. Giving the planner its own thread makes it possible to compute a route concurrently with control. CPU affinity then constrains where that already-separate computation runs. Shared data still needs safe publication, such as immutable snapshots passed through synchronized or volatile references. CPU placement cannot establish Java’s happens-before relationship, even when both threads use the same core. [CompetitionWorkers.java][workers] · [Java memory model][jls]

## Why cycle timing could change

- **Cache locality.** On a Cortex-A53, each core has its own L1 caches. A thread that migrates can resume on a core whose L1 holds less of its working set. Staying on one CPU can reduce those migration-related misses. Other work running on that core can still evict cache lines. [Arm Cortex-A53 cache architecture][arm] · [Linux affinity rationale][linux]
- **Contention.** If control and a compute-heavy planner are runnable on different CPUs, the planner can use CPU time without directly taking a time slice on the control CPU. They still share resources, including memory bandwidth. If CPU 0 is busy with Android or SDK work, pinning can increase control’s wait because it cannot move to another idle CPU.
- **Other sources of delay.** Hardware-bus transactions, locks, garbage collection and OS preemption can all lengthen a cycle. Scheduling priority affects how runnable work competes for CPU time. The CPU frequency governor affects clock speed within device limits. Affinity sets eligible CPUs, so neither priority nor clock speed is controlled by this implementation. [Control Hub performance-mode notes][governor]

## How to measure the effect for the portfolio

Use **cycle period**, the wall-clock time between consecutive loop starts, to measure update cadence. Cycle jitter is variation in that period. For start times `t[i]`, the period is `t[i] - t[i-1]`. F.R.O.G.’s `LoopTiming` also records **work time**, from the start of the loop to the end of the measured control work before telemetry. Both are elapsed times, so either can include time spent blocked or preempted. A 5 ms period corresponds to 200 Hz. If 5 ms is the comparison threshold, count how many cycles exceed it and report that fraction alongside the median, p95, p99 and maximum. The nearest-rank p99 is the sample at position `ceil(0.99 × N)` after sorting `N` periods from shortest to longest, with positions starting at one. The stationary test estimates percentiles with histogram buckets. Report the sample count because tail estimates are sensitive to run length. [LoopTiming.java][timing] · [LoopPeriodStats.java][stats]

To test the effect of pinning the **control thread**, keep worker activity and worker masks fixed. Repeat the same route or inputs on the same Hub with the OpMode’s inherited mask and with verified CPU 0 affinity. Use comparable warmup and run lengths, alternate the order across repeats, and keep firmware, telemetry, clock policy and hardware configuration fixed. Record temperature and, when planning is enabled, the number of solves offered, skipped and completed. A lower p99 is meaningful only in the context of the load the robot actually processed.

The disabled-by-default **Limelight CPU thread test** provides a stationary bench comparison. It cycles through inherited masks and control sets `{0}`, `{0,1}` and `{0,1,2}`. The three restricted modes request CPU 1 for vision and CPU 2 for planning. All three can be compared with those worker preferences fixed. The inherited-mask mode changes the whole placement layout, including the workers, so a difference against that baseline can reflect both changes. The test can offer one synthetic solve per second and excludes setup and a three-second warmup from its timing. It exercises the stationary test loop. A portfolio result about competition control needs the full robot workload. [CpuThreadTest.java][test]

**Evidence available for the portfolio.** The source and desktop checks document mask verification, failure handling and restoration. A quantified gain in cycle time or jitter requires paired, verified Control Hub runs under the competition workload. The October comparison of two Hubs measured SHA-1 throughput. [Desktop checks][checks] · [Control Hub benchmark][bench] · [TaskManager portfolio draft][portfolio]

[rev]: https://docs.revrobotics.com/duo-control/control-system-overview/control-hub-basics
[linux]: https://man7.org/linux/man-pages/man2/sched_setaffinity.2.html
[tid]: https://developer.android.com/reference/android/os/Process#myTid()
[toybox]: https://github.com/landley/toybox/blob/master/toys/other/taskset.c
[inheritance]: https://man7.org/linux/man-pages/man3/pthread_setaffinity_np.3.html
[procstatus]: https://man7.org/linux/man-pages/man5/proc_pid_status.5.html
[procstat]: https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html
[jls]: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5
[arm]: https://developer.arm.com/-/media/Files/pdf/graphics-and-multimedia/ARMv8_Overview.pdf
[affinity]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ThreadAffinity.java
[control]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/ControlThreadAffinity.java
[auto]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java
[teleop]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java
[vision]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightWorker.java
[planner]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineWorker.java
[config]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java
[workers]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompetitionWorkers.java
[cpustats]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/CpuStats.java
[timing]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LoopTiming.java
[stats]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LoopPeriodStats.java
[test]: TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java
[governor]: Control-Hub-Performance-Mode.md
[bench]: Control-Hub-Benchmark-2026-10-09.md
[checks]: tests/README.md
[portfolio]: TaskManager-Portfolio.md
