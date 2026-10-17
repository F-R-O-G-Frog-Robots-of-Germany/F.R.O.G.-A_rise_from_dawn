# Performance cleanup and comparison plan

Goal: remove proven duplication while preserving robot behavior, then measure each useful optimization in one isolated test OpMode.

## Scope and status

The cleanup was requested on 16 October 2026 after discovering that the FTC SDK already caches repeated motor and servo commands. P1-P10 were subsequently approved and implemented in the single `PerformanceComparison` test OpMode. The optional O1-O8 workloads remain proposals. No production benchmark switches or instrumentation were added outside the test OpMode.

The assessment covered TeamCode's hardware wrappers, robot lifecycle, shooter and intake updates, competition loops, telemetry, and the optional Limelight/planner workers. The pinned dependencies are FTC SDK 12.0.0, Pedro core/revhub 3.0.1, and Panels/telemetry 1.0.5. Their local dependency sources were inspected where application code alone did not establish behavior.

These cleanup changes are intended to remain in production. They are not temporary benchmark instrumentation for later deletion. The pre-existing edit to `opmodes/auto/structure/AbstractAuto.java` was preserved.

## What the SDK finding actually means

The installed SDK's `LynxDcMotorController.internalSetMotorPower()` and `LynxServoController.setServoPosition()` call `LastKnown.updateValue()` to suppress many repeated identical values. `LastKnown` defaults to a 500 ms validity window, so the SDK can refresh an unchanged command after that window expires.

An application cache can still do something different: suppress small nonzero changes, avoid SDK method calls altogether, or suppress identical calls indefinitely. Its existence alone is therefore not proof of redundant behavior. The comparison must distinguish application method calls from actual hub packets.

The SDK's velocity-target setter follows a different path and does not have the same repeated-target suppression. It must be tested separately from motor power. Pedro's drivetrain also has its own power-threshold wrapper; it is independent of TeamCode's motor wrapper.

For bulk reads, the public SDK defines [OFF, AUTO and MANUAL modes](https://javadoc.io/doc/org.firstinspires.ftc/Hardware/12.0.0/com/qualcomm/hardware/lynx/LynxModule.BulkCachingMode.html). The repository's stock `ConceptMotorBulkRead.java` documents and demonstrates the three cases. MANUAL requires clearing the cache before each measured iteration's sensor reads. I2C range sensors are a separate workload.

## Cleanup implemented

| Area | Change | Behavior retained or intentional difference |
| --- | --- | --- |
| Shared servo wrapper | Deleted `CachedServo` and migrated every production caller | Hood validation and its 0.005 position threshold remain with the mechanism that owns settling time |
| Shooter hood | Uses SDK `Servo` directly inside its existing position-change guard | Initial command, accumulated small changes, finite-position validation, clamping, and settling timestamps remain |
| Shooter tuner hood | Uses SDK `Servo` directly inside its existing applied-position guard | Rejected inputs hold the previous command; skipped changes do not restart settling |
| Flower intake | Uses SDK `Servo` for the two constant endpoint commands | Endpoint values remain 0 and 1; repeated calls may now trigger the SDK's periodic refresh instead of being suppressed indefinitely |
| Flower initialization | Removed the extra left-only UP command | The existing `up()` call initializes both servos |
| Robot lifecycle | Removed empty `start_robot()` and its caller | All mechanism initialization already occurs before START |
| Robot update API | Removed unused zero-argument `update_robot()` | Main continues supplying its already-sampled pose to `update_robot(Pose)` |
| Robot fields | Removed the stored, unread `HardwareMap` field | Initialization still passes the supplied hardware map directly to each subsystem |
| Shooter API | Removed unused `calculate_flywheel_velocity(distance)`, instance `calculate_hood_angle(distance)`, `distance_valid()`, and `set_flywheel_velocity(...)` | Static model helpers, cached hive access, and the active flywheel controller remain |
| Motor-wrapper documentation | Corrected claims that forwarding a call necessarily writes to the hub | SDK caching still applies after TeamCode forwards a command |
| Regression checks | Replaced obsolete servo-wrapper fixtures and extended real hood-command checks | Existing motor fault, active-shot, targeting, and feed checks remain |

The removed APIs had no remaining repository callers. Removing an uncalled method improves maintainability; it does not establish a runtime speedup.

## Deliberately retained after assessment

- `CachedMotor`: it adds a 0.002 shooter power threshold or 0.01 intake/transfer threshold, clipping, invalid-command handling, exact zero transitions, explicit invalidation, and reusable velocity samples. Every remaining method has a production caller. These functions are not replaced by SDK identical-value caching.
- Hood command state: the applied position and timestamp determine when feeding may begin. SDK servo caching does not provide mechanism settling state.
- Shared encoder samples and the intake's paired range-sensor cache: these avoid repeated reads and retain validity alongside the last finite value. Cached numeric values alone must not hide a new invalid sample.
- Pose identity and coordinate caches: a heading-only pose change and a changed target position are different events. Their guards avoid different work and preserve invalid-pose handling.
- Finite, readiness, stale-data, STOP, and active-shot guards: removing them would change robot behavior.
- Limelight/planner APIs and scheduling utilities: they are independent features with existing diagnostic and regression coverage. Absence from the current competition loop does not make their entire implementation redundant.

## Optimization comparison list

This is the complete practical comparison list from the reviewed code and prior performance notes. Existing optimizations and new candidates are identified separately. None has a claimed speedup before robot measurements.

### Main comparisons

| ID | Optimization | Switches / settings | Current status and measurement caveat |
| --- | --- | --- | --- |
| P1 | Hub bulk caching | OFF / AUTO / MANUAL | Competition already uses MANUAL after START. Clear once per iteration; never compare against stale data |
| P2 | Motor power-call suppression | Raw SDK calls / exact changes / epsilon changes | TeamCode's epsilon policy and Pedro's drivetrain threshold already exist. Count forwarded/skipped SDK calls; report command deviation |
| P3 | Servo command suppression | Raw SDK calls / exact changes / epsilon changes | After cleanup, hood deadbands remain local and flower endpoints use SDK caching. Test-only helpers can reproduce the removed generic wrapper without restoring it to production |
| P4 | Share encoder and velocity reads | Re-read for each consumer / sample once and reuse | Current shooter already samples once. Keep consumer computations and values otherwise equivalent |
| P5 | Limit range-sensor refreshes | Every iteration / selectable timed interval, including the current 300 ms | Current intake cache already does this. Record fresh reads per second and sample age; this exchanges freshness for work |
| P6 | Stagger the two range reads | Paired / alternating, with the same per-sensor refresh interval | Proposed. Compare worst-loop latency and age of each sensor, not just average Hz |
| P7 | Throttle telemetry construction | Every iteration / 100 or 200 ms / off | Current send interval does not prevent per-loop `addData()` and `update()` work. Gate construction and sending together |
| P8 | Reuse target calculations | Recompute / reuse unchanged pose coordinates and alliance | Existing Robot/Shooter guards. Compare stationary, heading-only, and changing-position inputs; preserve validity handling |
| P9 | Reuse shot calculation results | Immutable result allocation / reusable result or unchanged-input cache | Proposed. Current valid `ShotSolution` calculations allocate. Use identical model inputs and verify numeric outputs |
| P10 | Cache repeated velocity targets | Every call / only changed targets | Proposed; relevant to the tuner, separate from competition's custom power controller. Include mode-transition cases |

P2 and P3 must use three input profiles: constant commands, small gradual changes, and larger step changes. A deadband is most useful when values vary slightly; that also makes its output-resolution tradeoff visible. Live movement must use known safe mechanism ranges. Stationary reads and computation-only profiles can run without actuating the robot.

### Optional workloads and device-dependent experiments

| ID | Experiment | Settings | Why it is separate |
| --- | --- | --- | --- |
| O1 | Panels diagnostics | Driver Station only / joined Panels output | Competition config already disables Panels; keep the same telemetry cadence in both cases |
| O2 | Compatible sensor I2C speed | 100 / 400 kHz | Requires confirming the actual sensor/client model and compatibility of every device sharing the bus; record successful/failed reads and restore the setting |
| O3 | Camera work off the control loop | Synchronous / `LimelightWorker` | Optional current feature, not integrated into competition Main; match cadence and report processed frames plus frame age |
| O4 | Camera health polling | Every camera update / 250 ms | Existing worker already throttles health. Detection cadence must remain unchanged in this comparison |
| O5 | Planner work off the control loop | Synchronous solve / `ButineWorker` | Match input sets, request schedule, and solver settings; report completion latency and superseded results |
| O6 | CPU affinity | Normal Android scheduling / specified control and worker cores | Existing utility. Verify actual placement and restore masks; exclude setup subprocesses from timing |
| O7 | Native thread priority | Original / modestly more favorable priority | Proposed experiment. Verify application and restore original priority; measure under a fixed contention load |
| O8 | Planner search budget | Selected candidate/iteration/local-search limits | Changes planning quality as well as work. Report solution quality and solve time together |

All main comparisons and optional O1-O8 can be implemented using local helpers and existing public APIs inside one new `PerformanceComparison.java` OpMode. Any unavailable device or rejected setting must appear as unavailable, not as a zero-cost measurement.

### Areas needing separate physical or external experiments

- CPU governor and clock policy: these need verified device access outside an ordinary OpMode switch. October 9 records describe a past state, not the currently connected Hub. Record actual settings for each run; do not silently change them.
- CPU/RAM overclocking: no supported ordinary OpMode control was established.
- Autonomous waits, overlapping preparation, path tolerances, localization calibration, and shooter tuning: these change physical behavior and need travel/error/scoring measurements. A faster stationary loop does not establish a faster scoring cycle.
- Camera video traffic: the competition video hook is currently empty. An on/off switch there would measure nothing; a real supported stream must exist before it can be compared.
- Removing finite/freshness/readiness checks: excluded because this changes correctness.
- Logging and TaskManager work: there is no normal per-loop logging or active scheduled task workload in Main to remove. Adding artificial work should be labeled a synthetic workload.
- Precomputed constant validation and Horner-form polynomial evaluation are already present. They are small computational optimizations, below hardware calls, telemetry, and result allocation in priority; a calculation-only profile could examine them if needed.

## Benchmark implementation

The new `opmodes/teleop/test/PerformanceComparison.java` extends `LinearOpMode` directly. Extending `AbstractTeleOp` would force production caching and telemetry into the baseline. Test-only cache policies, statistics, hardware workloads, and input generation are nested helpers in this file.

Use gamepad navigation during configuration and between trials. Freeze settings for each measured block. Supply a custom configuration, a baseline plus each individual optimization, and an all-enabled configuration. An all-enabled-minus-one suite can expose interactions. Mutually exclusive choices such as AUTO and MANUAL remain one setting, not two independent booleans.

Keep SDK internal caches enabled in every configuration. A raw-call baseline is a baseline without TeamCode's extra suppression; it is not a claim that SDK caches were disabled.

Separate setup, an equal warm-up, the measured block, and result display/export. Repeat trials in alternating or shuffled order. Keep workload, hardware, instrumentation, power profile, and duration fixed within an A/B comparison. Save the complete setting combination and trial identifier.

Measure full start-to-start cycle time as well as work time and hardware/telemetry stages. Record mean, median, 95th and 99th percentiles, maximum, actual Hz from count divided by elapsed time, and counts exceeding selected deadlines. The 99th percentile is the duration within which 99 percent of recorded cycles completed.

Also record API read/write attempts, skipped calls, invalid samples, sensor/frame ages, and deadband command differences. Optional slow voltage/CPU/temperature sampling must use the same cadence in every compared configuration. Threaded workloads need completed-work counters so fast control loops cannot hide a backlog or discarded work.

Preallocate bounded timing buffers or histograms before measurement. Build CSV output and write files between measured blocks, not in each control iteration. Report any buffer overflow or histogram approximation. Export summary rows, timing distributions, and, if capacity permits, a bounded time series for portfolio plots.

Restore hub cache modes and optional thread/device settings in `finally`, stop any workers, and stop any enabled actuators. No competition classes, global config switches, or library internals need modification for the proposed initial implementation. If an approved later experiment cannot fit inside the file, label every external edit `TEMP_PERFORMANCE_BENCHMARK` and list its exact rollback in this document.

## Normal controls and one-button case selection

The default **Performance comparison** OpMode now operates the real robot with the usual controls. The benchmark has isolated, test-only copies of the mechanism state machines and a drivetrain adapter inside this one OpMode, so its cases can actually switch caches off without changing competition classes. Public shooter tuning constants refer to the production constants. Private controller/hive logic is a dated snapshot checked against production behavior by the desktop regression checks.

| Gamepad 1 control | Action |
| --- | --- |
| Sticks | Normal field-centric driving |
| A | Aim at the hive; driving cancels hold |
| Right trigger | Shoot; releasing stops the shot and resumes collecting |
| Left trigger fully pressed | Toggle collecting/scoring mode |
| Left bumper | Toggle flower intake |
| **Y** | Select the next optimization case |
| **X** | Start recording the current case, with a three-second warm-up |
| **B** | Stop the robot, calculate statistics, and save the recording |
| START | Retry a failed CSV export |

Gamepad 2 retains the normal configuration controls: hold B for three seconds to change alliance, X for fixed/distance target mode, bumpers to adjust fixed RPM, and D-pad left/right to reset pose. During INIT, D-pad up/down adjusts the fixed hood angle.

Pressing Y while recording saves the old case before changing settings. Case changes and statistics/export pause the mechanisms; release both sticks and triggers to resume, so a held control cannot immediately restart motion or a shot after the pause. A case remains selected until the next Y press. Holding Y advances only once until it is released.

The selector uses this **fixed estimate of likely mean loop-time benefit**, as requested. Recorded values never change the order:

| Position | Case | Estimated reason |
| --- | --- | --- |
| 1, enabled initially | P5: range polling every 300 ms | Avoids frequent I2C reads |
| 2 | P1: manual hub bulk caching | Shares eligible hardware read results per iteration |
| 3 | P4: shared velocity readings | Avoids repeated hardware calls by consumers |
| 4 | P2: motor power caching | Suppresses repeated/small-changing output calls |
| 5 | P7: telemetry every 100 ms | Avoids constructing diagnostics every iteration |
| 6 | P3: servo caching | Reduces hood output calls; preserves settling through identical commands |
| 7 | P8: target reuse | Avoids recomputing an unchanged hive target |
| 8 | P9: reusable shot result | Reduces result allocations |
| 9 | P6: staggered range polling | Primarily reduces latency spikes, rather than total read work; includes P5's interval prerequisite |
| 10 | P10: velocity-target caching | Not applicable to normal custom-power shooting; available in the separate stationary velocity workload |
| 11 | BASELINE | All optional benchmark optimizations disabled; SDK caches and safety checks remain |

Then the cycle returns to P5. Starting at P5, ten Y presses reach BASELINE. These are estimates for this code and workload, not measured speedup claims. In particular, P6 can improve jitter more than average throughput, and P10 cannot improve the normal shooter because it does not use `setVelocity()`.

In normal mode, recording continues until B, Y, or buffer capacity. X does not run a synthetic motion pattern or automatic suite: drive and shoot the same sequence yourself for each case. Whole control-update work and full start-to-start cycles are measured; individual hardware-stage columns are unavailable in this mode. Every raw CSV identifies `mode=REGULAR_TELEOP`, the active case, the fixed estimated order, exact flags, and hardware counters. Statistics and export run after the robot pauses, never in a recorded iteration. Driver Station STOP prioritizes cleanup; use B to save the current recording first.

## Where statistics are saved

CSV files are saved **on the Control Hub**, not automatically on the laptop:

```text
/sdcard/FIRST/performance-comparison/<session timestamp>/
```

The exact folder is displayed on the Driver Station. Each recording has a raw timing CSV and a matching `_summary.csv` containing mean, median, p95/p99, maximum, elapsed time and frequency. Normal recordings are named `REGULAR_P5`, `REGULAR_P1`, etc., or `REGULAR_BASELINE`.

After connecting the Hub through ADB, copy all sessions to the laptop from PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pull /sdcard/FIRST/performance-comparison "$env:USERPROFILE\Downloads\FROG-performance"
```

The same folder can be copied through Android Studio's Device Explorer. No downloads or exports run in the measured loop.

## Stationary microbenchmark mode

Hold gamepad 1 **BACK while pressing INIT** to enter the previous stationary microbenchmark menu instead of normal controls. This separate mode is useful for P10's actual velocity-target workload and controlled synthetic tests. Its controls differ from normal mode and are described below.

Choose **Performance comparison** in the Driver Station's Test group. Configure before or after Driver Station START; A begins trials only after START.

| Control | Action |
| --- | --- |
| D-pad up/down | Select a setting |
| D-pad left/right | Change that setting |
| X / Y | Load baseline / optimized optimization settings; actuation choices remain as selected |
| A | Run the selected suite |
| B | End the current warm-up/recording, stop live motors, calculate statistics, and export the partial result |
| Gamepad START | Retry a failed export; another trial is blocked until the retained data is exported |
| Both bumpers held | Required throughout any selected live motor or hood workload; releasing either ends the trial and stops motors |

The settings expose P1-P10 individually, three command profiles, three synthetic-pose profiles, optional stage timing, 5/15/30/60-second trial limits, 1/3/5-second warm-up, and 1/3/5 repeats. `CUSTOM` uses exactly the chosen settings. `BASELINE_AND_ALL` compares both presets. `SINGLE_CHANGES` includes each independent variant, both AUTO/MANUAL, exact/epsilon policies, three telemetry alternatives, and both reusable/cached shot results. `ALL_MINUS_ONE` removes each optimization from the optimized preset. Non-custom trial order is deterministically shuffled within each repeat and recorded in metadata.

P6's single-change reference is the paired P5 300 ms variant. An ablation that removes P5 also removes P6 and is explicitly named `WITHOUT_P5_AND_P6`; staggering an every-loop pair would reduce per-sensor refresh frequency. Paired and staggered timed reads use fixed-origin deadlines with the same nominal per-sensor interval and no catch-up backlog.

### What is measured

In the stationary microbenchmark menu, the default **SHADOW** output mode performs real reads on the available drive/range devices and simulates output policies. It makes no motor or servo SDK write calls in the measured workload. This mode measures cache/computation overhead, not live write transport. Every CSV states the selected mode, unavailable devices, simulated channels, and actual SDK-call counts. Normal mode uses real robot outputs and has no SHADOW setting.

To measure live motor writes, select **POWER** or **VELOCITY**, with the robot positioned for drive-wheel movement, and hold both bumpers. Only the four named drive motors (`leftFront`, `leftRear`, `rightFront`, `rightRear`) are used. They are capped at 0.10 power or 100 encoder ticks/second. The shooter, intake, transfer, and follower are never initialized. P2 is live in POWER mode and P10 in VELOCITY mode; the other command path remains simulated, so both controllers never fight over the same motor.

Live hood writes are a separate selection. They stay within +/-0.01 of the SDK command captured during INIT and are unavailable if that command is nonfinite or outside [0.05, 0.95]. That command is not physical angle feedback. The fixed hood baseline is restored on final cleanup. Original motor run modes and hub bulk modes are restored; a prior nonzero motor command is never restored. Velocity cleanup explicitly sends a zero velocity target before power zero because the SDK's power cache does not track velocity commands.

P8/P9 use synthetic field coordinates and the production shot model, not Pinpoint localization or a driven path. Before timing, the reusable model is checked against the production API on nine valid/invalid inputs. A mismatch refuses the trial. Range caches expose current validity separately from retained finite values, fresh-read attempts, sample ages, and refresh gaps. SDK-call counts are never labeled as hub-packet counts.

### Statistics and recording overhead

No mean, median, percentile, sort, CSV generation, or result-screen formatting runs during recording. The recorder performs six primitive-array stores and updates its index. Its six arrays and sort scratch are allocated before trials, holding 65,536 paired samples (approximately 3.5 MiB total). A full buffer ends the trial as `BUFFER_FULL`; it never wraps or silently drops rows. The requested and actual durations are exported, so early capacity stops remain visible.

Statistics and CSV files are produced once after the trial ends or B is pressed, after live motors are stopped. Driver Station STOP prioritizes prompt cleanup and skips analysis/export of the current unfinished trial; use B to retain a partial run. There is no every-three-second statistics interruption inside a measured block.

Each stored work/stage sample is paired with its own next start-to-start interval. Actual Hz is sample count divided by the span of those intervals. Work includes reads, calculations, and command writes; cycle additionally includes telemetry, buffer recording, cancellation checks, and the SDK loop's idle/yield behavior. If STOP interrupts before a work sample obtains its next boundary, that last unpaired work is excluded from timing statistics and identified in metadata. Workload counters can include that final work.

Stage timing probes can be disabled to reduce clock-call overhead; disabled stages have no summary rows and raw zero columns are labeled unavailable in metadata. Keep this setting the same within an A/B comparison. The SDK telemetry send interval stays at 100 ms in all P7 variants; the whole telemetry-construction block changes cadence. That block is the intentional telemetry workload, and never calculates statistics.

Recording, timers, validity checks, and workload counters still have finite cost. No overhead is subtracted or claimed to be zero on the Hub. Their settings remain fixed across comparisons. A desktop allocation check measured zero allocations in one million warmed recorder calls; robot timing still needs measurement.

### Portfolio files

After each completed or B-ended trial, files are written to `/sdcard/FIRST/performance-comparison/<session timestamp>/` (the exact directory appears in telemetry):

- `<run id>.csv`: complete settings/availability/counters as comment metadata followed by chronologically ordered paired raw samples in nanoseconds.
- `<run id>_summary.csv`: cycle/work and enabled-stage statistics in milliseconds, count, mean, median, nearest-rank p95/p99, maximum, counts above 10/20 ms, actual elapsed time and Hz, and reference/repeat identifiers.

CSV generation occurs between trials. Export retries overwrite that trial's files rather than appending duplicate summary rows. An export failure retains the buffer and blocks another run until the operator retries successfully. Empty or warm-up-cancelled runs are labeled and have unavailable statistics, not apparent zero-cost results.

All new runtime benchmark code is contained in the one OpMode. The two new desktop test files are regression checks, and this document is the operating guide; neither adds hooks to competition code or needs deletion for normal operation.

## Validation

- `:TeamCode:assembleDebug` passed with Java 17, producing the debug APK. The initial offline attempt lacked cached AAPT2; the normal build resolved that dependency and completed.
- Direct compilation of the five affected standalone checks against the compiled production classes and pinned dependency signatures passed.
- `FiniteValueChecks`: 156 checks passed.
- `ShooterHoodChecks`: 131 checks passed, including retained settling through skipped changes and accumulated changes crossing the position threshold.
- `PollenFlowChecks`: 5,009 assertions passed, including 864 matrix cases, 36 distance-pair cases, trigger behavior, invalid active-shot inputs, and STOP behavior.
- `ShooterMotorTunerChecks`: 85 checks passed, including 63 hood assertions covering finite commands, invalid commands, applied positions, accumulated deadband changes, settling timestamps, and endpoint clamping.
- `TeleOpHiveAimChecks`: 1,377 checks passed. Its pre-existing null-pose fixture was corrected to inject a missing sample through `Localizer.pose()` instead of passing null into Pedro's `MotionState.withPose()`, which rejects null before production code runs.
- Total: 6,758 passing assertions/checks across these five programs. No permanent test framework was added.
- Repository diff whitespace validation passed, and searches found no remaining Java references to the deleted class/methods.

Benchmark implementation verification:

- `:TeamCode:assembleDebug --offline --no-daemon` passed with Java 17, including the new registered OpMode.
- `PerformanceComparisonChecks`: 523 checks passed for statistics, nearest-rank percentiles, deadlines, buffer limits, sample pairing/order, CSV output, isolated/frozen trial settings, repeat order, and P5/P6 dependencies. The warmed recorder allocation check reported zero allocated bytes over one million calls.
- `PerformanceWorkloadChecks`: 593 checks passed for cache policies, cumulative thresholds, zero transitions, shared reads, missing devices, invalid range samples, range deadlines, model equivalence, no SDK commands in shadow mode, distinct power/velocity paths, velocity stop order, and cleanup after injected device failures.
- Total new benchmark checks: 1,116. Both programs compile against the real pinned SDK/dependency signatures; hardware is replaced only by desktop test doubles.
- Runtime changes for this implementation are confined to `PerformanceComparison.java`; prior approved cleanup edits were preserved, including the user's pre-existing `AbstractAuto.java` change.

Normal-control revision verification:

- `PerformanceCaseChecks`: 268 checks passed for the exact fixed estimated order, wraparound to baseline, ten presses from the initial case to baseline, independent snapshots, and no recording-based reordering.
- `PerformanceRegularMechanismChecks`: 853 checks passed, including raw/cached behavior, settling under repeated commands, invalid feedback, active-shot latching, sample schedules, model parity, and a deterministic 60-step comparison with production mechanisms.
- `PerformanceRegularDriveChecks`: 2,928 checks passed against Pedro's wheel math and captured production threshold, including small sign reversals, exact stops, brake/coast, configuration isolation and cleanup failures.
- `PerformanceRegularControlChecks`: 90 checks passed for ordinary drive axes, aim, trigger edges/order, settings, INIT/START handling, neutral-control rearming, and invalid-pose stopping.
- The earlier 523 recorder/configuration checks and 593 stationary-workload checks still pass against the combined OpMode. Total benchmark checks: 5,255.
- Java 8 source compilation against the real SDK passed. The normal mode uses real controls and odometry; stationary microbenchmarks remain a separate BACK-at-INIT option. No runtime hooks were added to competition classes.

Hardware timing and physical behavior remain to be measured on the robot. These results establish build and regression compatibility, not a measured performance gain.

## Changes you would like

- Main comparison IDs to include or exclude:
- Optional experiment IDs to include:
- Available hardware and safe test ranges:
- Preferred trial duration and number of repeats:
- Portfolio metrics or CSV columns to add:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
