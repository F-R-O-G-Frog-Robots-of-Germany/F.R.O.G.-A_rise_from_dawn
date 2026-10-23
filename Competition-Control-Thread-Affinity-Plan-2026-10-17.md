# Competition control-thread affinity plan

17 October 2026. **Software implementation completed; physical validation deferred.** Both competition bases use `ControlThreadAffinity`, with `CompConfig.CONTROL_CORE = -1` retaining Android placement by default. The robot has not been built; actual placement, loop timing and competition benefit remain unverified. The sections below preserve the approved plan.

Verification: 51 dedicated affinity lifecycle checks, 94 competition lifecycle checks, and 18 shutdown checks pass as part of the 14,014-check standalone suite. The full source compiles against the real SDK signatures; separate camera fixtures bring the combined distinct check count to 14,283. The full Gradle APK build fails before compilation with the existing loopback-connection error. These checks do not establish physical affinity or a performance improvement.

Related documents: [assessment](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Multithreading-Core-Pinning-and-Ma-Douce-Assessment-2026-10-17.md) and [worker integration plan](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Worker-Integration-Plan-2026-10-17.md).

## Intended behavior

Keep gamepads, Pedro, shooter, intake, sensor/cache ownership and OpMode decisions in one ordered control loop. Add optional affinity for that actual OpMode thread in both competition lifecycles: [AbstractTeleOp](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:48) and the `General.runOpMode()` lifecycle inside [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:47).

Use a shared configuration value such as `CONTROL_CORE`: `-1` means retain Android's inherited eligible mask; a nonnegative value requests that zero-based CPU. Keep `-1` as the initial default pending hardware evidence. CPU 0 is the tester's current example, not an established optimum. Read the setting once during INIT so an accidental configuration edit does not repeatedly run affinity commands during control.

This adds no mechanism threads, per-process frequencies, planner compute cap, replacement follower, root requirement, governor change, real-time scheduling policy or app-wide pinning.

## Integration sequence

1. Construct the follower, hardware wrappers and worker owners during INIT. Complete all deliberate thread launches first, including launches performed by runner initialization and lifecycle hooks. Child threads inherit their creator's eligible mask: pinning control before launching them can accidentally restrict an unpinned worker or SDK helper to the control CPU. Any later launch needs the same inheritance review.
2. At the final setup boundary before the INIT wait loop, call [ThreadAffinity.bind_current_thread](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ThreadAffinity.java:44) from `runOpMode()` itself. It obtains the native TID of the calling OpMode thread. Calling it on a setup helper would bind the wrong thread.
3. Save the complete `ThreadAffinity.Result` immediately, even when `applied` is false. A failed command or failed verification can follow a partially successful mask change; its restoration state still matters. Publish requested CPU, native TID, verified outcome and failure detail in INIT diagnostics.
4. If binding is unverified, attempt restoration through the saved result before describing operation as an ordinary scheduler fallback. If restoration also fails, report the placement as unknown and expose its kernel allowed mask. Continue functioning without claiming an unverified CPU assignment.
5. Run the existing important control loop unchanged. Camera/mapping and pure planning use their separate owners and snapshots as described in the worker plan. Do not execute taskset, mask reads or thermal enumeration on each control cycle.

## Cleanup and error handling

Extend the existing lifecycle cleanup with nested `try/finally` boundaries. Attempt safe drivetrain and mechanism outputs first, with independent cleanup attempts so one thrown exception cannot skip the remaining mechanisms. Request worker cancellation promptly; perform bounded joins only after safe motor commands have been attempted. Camera I/O or a planner solve must not hold up the initial motor shutdown.

Place control-mask restoration in the outermost cleanup `finally`, on the same OpMode thread. Restoration must execute when initialization fails, STOP arrives during INIT, a loop/hook throws, worker shutdown fails, or another cleanup step throws. Preserve interruption and surface restoration failure without replacing the original failure. Worker cleanup independently restores each worker's own original mask. Bounded joining does not guarantee an active solve or blocked I/O has already ended.

## Meaning of a verified assignment

A verified one-bit Linux affinity mask constrains where this native thread may execute. Ordinary JVM scheduling does not override that effective constraint. Android policy or another permitted affinity operation can subsequently change it, and other work may still share the selected CPU. Startup verification therefore needs later diagnostic observation; `lastCpu` alone does not prove affinity.

Pinning may improve cache locality but cannot guarantee an improvement. [REV specifies an RK3328 quad-core Cortex-A53 in its Control Hub manual, page 3](https://revrobotics.eu/content/docs/REV-31-1595-UM.pdf); [Arm describes per-core L1 and a shared L2 cache](https://www.arm.com/products/silicon-ip-cpu/cortex-a/cortex-a53). Migration can disturb L1 locality, while separate cores still share cache/memory resources. Neither architecture nor CPU frequency yields a universal control-loop Hz limit.

## Validation when hardware exists

The existing tester is **“Limelight CPU thread test”**, class [CpuThreadTest](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java:31), currently `@Disabled`. **A** submits a synthetic solve, **B** cancels it, **X** toggles control CPU-0 affinity and resets timings, and **dpad left/right** pages app threads. X leaves vision/planner requests on CPUs 1/2 unchanged. A fully unpinned comparison requires configuring worker preferences to `-1` before their startup, followed by a fresh run.

Its current last/mean/maximum periods and last/mean-period/slowest Hz describe the stationary tester. It has no p95/p99 distribution and does not exercise competition motors or the complete robot workload. Its displayed owner assumption of **4–6 ms = 167–250 Hz**, including headroom relative to a **5 ms / 200 Hz** cycle, is explicitly unmeasured. The separate provisional assessment estimate is **5–8 ms = 125–200 Hz**, nominal **6 ms ≈ 167 Hz**; this is also a hypothesis, not a cutoff or acceptance guarantee.

Compare full cycle periods under matched load and warmed runtime, including planning, camera activity, telemetry and thermal conditions. Full cycle time includes waits and scheduling gaps; measured wall work is not CPU utilization. Optional future tester instrumentation can record distributions before adopting any numerical acceptance threshold.

Before enabling a competition default, demonstrate correct TID/mask, restoration across every exit path, no inherited control-only worker masks, and repeatable timing results without worse slow cycles. Keep `-1` readily available when the measured benefit is absent. These are future acceptance checks; no physical results are claimed today.

Written by Codex (GPT-6), I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.
