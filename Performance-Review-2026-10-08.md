# F.R.O.G. performance review — 2026-10-08

Goal: improve control-loop consistency and scoring speed with small, measurable changes while keeping Pedro's normal behavior.

## Scope and evidence

Reviewed the main checkout at `C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn`: TeamCode competition loops, mechanisms, hardware wrappers, input/control utilities, autonomous routines, tuners, the stock FtcRobotController module, Android manifests, Gradle configuration, and existing test/documentation structure. This is a static review. No robot was connected, no CPU or memory measurements were taken, and no runtime code or device settings were changed.

The PIDF values are intentionally untuned, as confirmed by the project owner. Their present numerical behavior is a calibration task, not evidence of a software performance regression. The useful distinction is between faster Java execution, more consistent sensor/control updates, and shorter physical scoring cycles.

## Recommended order

| Order | Improvement | Expected value | Effort / tradeoff |
| --- | --- | --- | --- |
| 1 | Measure full-cycle timing and occasional long loops | Identifies which changes actually matter | Small bounded diagnostic addition; publish infrequently |
| 2 | Build and update diagnostic telemetry at 5–10 Hz | Removes unnecessary work in every loop | Small; driver diagnostics refresh less often |
| 3 | Time and, if necessary, stagger distance-sensor reads | May reduce periodic control delays | Small to medium; staggered samples have different ages |
| 4 | Remove temporary objects from Shooter's internal update | Reduces allocation rate and possible garbage-collection work | Small; keep public calculation APIs readable |
| 5 | Tune physical autonomous waits and overlap preparation with driving | Potentially greater scoring gain than faster arithmetic | Requires robot measurements; readiness still gates feeding |
| 6 | Make the tuner exercise the chosen competition controller | Makes tuning results transferable | Choose after checking encoder units and command limits |
| 7 | Measure and calibrate localization / path parameters | Can reduce corrections, overshoot and arrival delays | Use Pedro's existing configuration and tuners |
| 8 | Cache repeated velocity commands if retaining hub velocity control | Avoids unnecessary hub commands in the tuner | Small, with explicit invalidation after mode/config changes |

These are priorities for investigation, not measured speedup claims. The telemetry edit is the easiest immediate cleanup; sensor reads are a stronger candidate for occasional long loops, depending on the sensor driver and hardware.

## 1. Measure the right thing

[LoopTiming.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LoopTiming.java:3) already distinguishes work time from cycle time. Work ends before telemetry; cycle includes everything between consecutive starts. It currently retains only the latest values.

Record average, maximum and a small fixed histogram for the 95th/99th percentile of cycle time. The 99th percentile means that 99% of cycles completed within that duration. Occasional long cycles can matter more to feedback control than a high average loop frequency. During tuning, separately time `follower.update()`, the two distance reads, Shooter's update, and telemetry. Do not allocate a list or write a file on every iteration.

Compare telemetry enabled versus disabled, and compare iterations with versus without distance refreshes. Run both a cold test and a full match-length warm test under comparable battery/load conditions. Keep the measurement overhead small and check the result again without a profiler attached.

CPU profiling should inspect the OpMode thread and individual cores. Low aggregate CPU usage can hide one busy core in a mostly single-threaded control loop. Android Studio exposes thread/core activity, subject to device and profiler compatibility. [Android profiler documentation](https://developer.android.com/studio/profile/cpu-profiler).

## 2. Throttle telemetry construction

[CompConfig.java:12](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:12) enables loop-time telemetry even in competition mode. [AbstractTeleOp.java:113](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:113) and [AbstractAuto.java:219](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:219) construct Work/Cycle/Hz items and call `update()` every iteration.

The 100 ms transmission interval limits sending, but your Java calls and numeric boxing still occur at control-loop frequency. Gate the entire telemetry block at 100–200 ms, or turn the loop display off after profiling. Keep follower and mechanism updates at full rate. FTC documents the transmission interval as a communication-bandwidth control. [FTC telemetry settings](https://ftc-docs.firstinspires.org/en/latest/programming_resources/shared/myblocks/telem_example/telem-example.html).

## 3. Distance reads and loaded detection

[Intake.java:137](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:137) calls both distance sensors in the same iteration, at most once every 300 ms. Shooter can trigger that refresh through `loaded()`. Manual encoder bulk caching does not turn these I2C readings into the same bulk packet. Actual call latency depends on the configured sensor and its driver/cache; measure before changing ownership or cadence. REV documents the separate I2C buses and sensor protocol. [REV I2C documentation](https://docs.revrobotics.com/duo-control/sensors/i2c).

If these calls cause spikes, consider staggering them across iterations or reading only when occupancy detection is needed. A staggered pair is no longer a simultaneous sample, so define acceptable sample age. Increasing the interval reduces refresh work but delays recognition; the current interval already permits almost 300 ms of detection delay.

Both empty-distance thresholds are currently 0 mm at lines 134–135. Normal nonnegative readings cannot satisfy the `< 0` loaded test. These placeholders need calibration before increased polling speed is useful. If the mechanism only needs presence/absence, a suitable digital presence sensor is another option to evaluate; it adds hardware and does not solve color classification.

## 4. Keep Shooter's hot path small

[Shooter.java:324](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:324) constructs a `HiveDistance`, and line 330 constructs a `ShotSolution`, on normal updates. Reuse primitive internal fields or a reusable internal result for these calculations. The public utility results can remain readable immutable objects. Whether these allocations survive Android runtime optimization should be measured.

The nearest-hive helper calculates two hypotenuses. With ordinary bounded field coordinates, compare squared distances first and calculate only the chosen actual distance. In fixed-target mode, skip distance work if no active consumer needs it. These changes are modest; do not build another caching framework just to eliminate two tiny objects.

The existing fixed-arity finite checks and reusable sensor samples are already cheap. Removing them is lower priority than avoiding hardware calls and repeated allocations. Likewise, the current uncontended synchronization in the wrappers is a low-priority target.

## 5. Improve physical cycle time

[SoloWithSteal.java:13](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:13) uses 1.25 s per shot window, with five shots: 6.25 s of scheduled shot windows before driving and separate readiness waits. Saving a measured 0.15 s per window would save 0.75 s across that routine. This is an illustration, not a prediction. Shorten only after testing complete delivery because the readiness gate can pause feeding within the window.

[AbstractAuto.java:133](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:133) queues driving then shooting. The robot already spins at its FLOAT RPM and may enter PREPARE when loaded. Further improvement is to prepare the final destination's RPM/hood while approaching, so settling overlaps driving rather than following the changing current-position target. Feeding still waits for arrival and readiness. Use normal action logic and Pedro's public API.

The hood currently waits a full 250 ms after each accepted position change of at least 0.005. Once a varying hood model replaces the constant placeholder, moving-pose updates may keep restarting this timer. A measured travel-dependent delay, a target deadband, or committing the shot target during feeding could improve readiness. This is prospective; the current constant hood curve does not create continuous changes.

## 6. Tuner/controller alignment and encoder units

Competition uses the custom power controller; [ShooterPidfTuner.java:100](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:100) tunes the hub's velocity controller. Their gain numbers are not interchangeable. Either tune the custom controller through the tuner, or use hub velocity control in both after verifying compatibility. Choose based on spin-up, shot recovery and RPM variation, not the tiny cost of PID arithmetic.

The tuner sleeps 40 ms after its work, so its loop takes work time plus 40 ms, rather than exactly 40 ms. Hub regulation runs independently, but sampling is slower than the production loop. If tuning the custom controller, keep control updates representative of competition and publish telemetry separately at a slower rate.

The configured 2048 ticks/revolution and 3000 RPM imply `3000 / 60 * 2048 = 102400` ticks/second. Inspection of the locally installed FTC Hardware 12.0.0 source shows that `LynxDcMotorController.setMotorVelocity()` clips its target to ±32767 ticks/second. At 2048 ticks/revolution, that limit corresponds to about 960 RPM. This is a command-range compatibility check, independent of untuned PIDF gains. Verify actual quadrature ticks per motor revolution and gearing before adopting `setVelocity()` or interpreting tuning data. The physical motor/encoder model is not established by this repo.

[CachedMotor.java:30](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java:30) currently forwards every velocity command. The same FTC 12.0.0 source creates and sends a Lynx target command on each invocation. A minimal velocity-target cache can avoid identical repeated commands, especially in the tuner; invalidate it when motor mode or external configuration changes. Power and servo commands already have caches.

## 7. Calibrate Pedro through its normal tools

[Constants.java:43](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:43) sets both Pinpoint pod offsets to zero. Measure them unless zero matches the physical mounting. Also measure achievable velocities/deceleration instead of relying on estimates. Bad calibration can cause extra correction and longer travel despite a high loop rate.

The arrival constraints at lines 80–84 are fairly tight: 0.1 distance, 0.1 velocity and 0.007 radians. Evaluate endpoint error and wait time, then consider appropriate tolerances for transit/pickup versus shooting through Pedro's native configuration. Do not blindly increase speed or loosen precision at every destination. Preserve the standard follower, localizer and drivetrain.

## CPU and RAM overclocking

Assuming the robot uses a REV Control Hub, REV specifies an RK3328 quad-core Cortex-A53, a separate Cortex-M4 processor and 1 GB LPDDR3 memory. [REV Control Hub specifications](https://docs.revrobotics.com/duo-control/control-system-overview/control-hub-basics).

There is no CPU/RAM overclock control in this Java project, and the REV documentation reviewed does not provide a supported overclock workflow. Changing Android CPU clocks would be a device/kernel-level change. It would not automatically increase I2C/hub communication speed or speed up the separate motor-control processor. RAM capacity and RAM clock rate are also different concerns.

I would not spend project time on overclocking before demonstrating CPU saturation or memory-bandwidth pressure. More clock speed can increase heat/power demand and undermine sustained performance if thermal limits intervene. A warm test that records clock/temperature behavior can establish whether thermal throttling is present; the repo does not establish that it is. Correcting a demonstrated thermal or power problem has a clearer purpose than increasing clocks speculatively.

For perspective, a hypothetical 10 ms cycle consisting of 3 ms computation and 7 ms waiting would become 9.5 ms if computation sped up by 20% while waits stayed constant: only about 5% faster overall. This example is not a measurement of F.R.O.G.

The retrieved 2026–2027 competition manual restricts modifications to core control devices under R706. Its extracted text does not establish an approved overclock exception. Do not treat a custom kernel or clock modification as an established competition option. The same manual's R704 restricts match streaming to the official Driver Station route, so retain competition mode's disabled Panels behavior. [FIRST 2026–2027 manual, section 12.7](https://ftc-resources.firstinspires.org/ftc/archive/2027/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20V1.htm).

`org.gradle.jvmargs=-Xmx1024M` in [gradle.properties](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradle.properties:10) allocates memory to the development PC's Gradle process. Raising it can help memory-constrained builds but does not give the robot more RAM or accelerate its control loop. The Android manifests already request a large heap; that is not a RAM overclock either.

REV recommends 5 GHz Wi-Fi when the Driver Station supports it, and selecting an appropriate channel when the local environment is crowded. This can improve communication consistency; it does not raise autonomous control-loop frequency. Follow any event-assigned band/channel. Battery condition and connectors also deserve attention when problems correlate with mechanism load. [REV troubleshooting guidance](https://docs.revrobotics.com/duo-control/troubleshooting-the-control-system/control-hub-troubleshooting).

## What already works well / low-priority changes

- Competition teleop and auto use MANUAL hub bulk caching and clear each cache once per iteration. Preserve this ownership.
- All three mechanism motors and three servos use cached wrappers; the Pedro drivetrain keeps its own implementation.
- Paths are built during INIT, rather than repeatedly while driving.
- Panels is disabled in competition mode. Limelight/Butine and the video hook currently have no active vision workload to remove.
- TaskManager scheduling and PeriodicRegistry registration have no active main-code callers. Thread-priority changes there cannot accelerate current operation. Keep hardware ownership simple rather than adding motor/sensor worker threads.
- The stock FtcRobotController module and unused samples are not all running in the active loop. Deleting samples/tests is not a control-loop optimization.
- A nondebuggable APK is worth one controlled comparison if profiling points toward runtime overhead. Aggressive shrinking or deleting SDK components is a poor first step because OpModes and framework components rely on discovery/reflection.
- Desktop builds/tests establish correctness within their scope, not on-robot timing or thermal behavior. No new runtime tests were needed for this documentation-only review.

## Changes you would like

- Preferred first improvements:
- Measurements / observed loop times:
- Robot controller and shooter motor/encoder models:
- Other constraints or priorities:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
