# F.R.O.G. repository review — 11 October 2026

Goal: identify current bugs, practical efficiency improvements, useful missing features, and BIOBUZZ season ideas across the repository, excluding Limelight.

The most urgent findings are three reproduced bugs: the Pedro end tolerance can end autonomous paths almost immediately; an invalid heading can leave the drivetrain moving after the sticks return to neutral; and a rejected final pose can leave an older saved pose available for the next TeleOp. Fix these before comparing route speed or scoring consistency.

The current architecture has already addressed many earlier reliability problems. Competition actuator control runs on the OpMode thread, paths are built during INIT, waits are bounded, mechanism state belongs to instances, and the existing regression checks pass. The next improvements should concentrate on these three bugs, physical calibration, understandable driver feedback, and measured scoring-cycle time.

**1. Scope and evidence.** I reviewed the main checkout at `C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn`, starting from commit `91a2c02`. The source tree was clean at the start. This review produced documentation and temporary verification artifacts; robot source, device settings, and tuning constants were not changed.

The review covers Robot, every non-Limelight TeamCode subsystem and OpMode, all 18 autonomous registrations, Pedro configuration and tuning procedures, control/input/hardware/unit utilities, the four non-Limelight regression programs, Gradle configuration, manifests/resources, Control Hub tools and recorded measurements, and relevant project documentation. The excluded scope is the Limelight package, its tests/docs, its audit tooling, and the SDK Limelight sample. No Limelight findings or proposed integration changes are included.

I compared the stock FtcRobotController module with the official FTC v12.0 Git tree: all 85 tracked files match. This identifies the module as unchanged SDK code rather than a separate custom implementation needing a speculative rewrite. The SDK's internal Android behavior and every disabled sample were not independently exercised. Binary assets and bundled dependencies were identified, not reverse-engineered wholesale.

Online research used FIRST's current BIOBUZZ manual and team updates, FTC technical documentation, official SDK releases, and Pedro's documentation/releases. Recommendations below distinguish documented behavior, reproduced source defects, known configuration gaps, and proposals requiring hardware measurements.

**Verification performed**

| Check | Result | What it establishes |
| --- | --- | --- |
| Direct Java compilation | Passed for 126 tracked non-Limelight Java files plus generated BuildConfig | Current sources compile against cached SDK/Pedro/Android dependencies and previously generated resources |
| FiniteValueChecks | 201 assertions passed | Mechanism finite-value boundaries |
| ShooterHoodChecks | 112 assertions passed | Hood mapping, target validity, settling and related behavior |
| PollenFlowChecks | 5,009 assertions passed, including 864 state combinations and 36 sensor pairs | Current collection, shot latch and trigger behavior |
| TeleOpHiveAimChecks | 204 assertions passed | Current aim controls and ordinary finite-pose behavior |
| Total existing assertions | **5,526 passed** | Existing tested behavior remains consistent |
| Three targeted bug reproductions | All reproduced | Actual pinned Pedro behavior and stale saved-pose state |
| XML syntax | 11 files parsed successfully | XML is well formed |
| Inspect-ControlHub.ps1 | No PowerShell syntax errors | Script parses |
| Official SDK comparison | 85/85 module files identical to v12.0 | Stock SDK provenance |

A normal Gradle compilation and unit-test-source compilation were attempted. Gradle failed before source compilation with `java.io.IOException: Unable to establish loopback connection`. Retrying without a persistent daemon and with IPv4 preference failed the same way. This is an unresolved local build-environment problem, not evidence of a Java source defect. Direct compilation uses cached dependency classes and generated R files; it does not prove a clean APK build, manifest merge, resource generation, annotation processing or successful installation.

The existing checks are standalone JVM programs with `main()`, not automatically discovered JUnit tests. They were explicitly executed. A normal Gradle test command should not be assumed to run them.

No physical robot, actuator, field-clearance, thermal, current, shooting-accuracy, or match-length timing test was performed. Simulated hardware in the reproductions did not access a Control Hub.

**2. Confirmed bugs, ordered by priority.**

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| B01 | P1 | Pedro path-end tolerance is reversed | Pinned follower reproduction ends a 100-inch path with 99.7 inches still remaining |
| B02 | P1 | Invalid heading can retain prior drivetrain power | Actual Pedro motor caches ignore NaN wheel commands; neutral sticks retain 0.5 power |
| B03 | P2 | Rejected latest pose preserves an older valid handoff | Invalid NaN/BLUE save leaves earlier coordinates and RED alliance available |

P1 means a high-impact correction before normal robot operation or autonomous testing. P2 means a meaningful reliability correction with a more specific trigger.

**B01 — Autonomous can declare completion near the beginning of a path.** [Constants.java:80](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:80) sets:

```java
c.parametricTConstraint.set(0.998);
```

In the pinned Pedro core 3.0.1 implementation, the completion condition is:

```java
closestT >= 1 - parametricTConstraint
```

The parameter is a tolerance measured back from the end of the curve. The present setting therefore permits completion at `t >= 0.002`, approximately 0.2% of the curve parameter, rather than the likely intended `t >= 0.998`. Curve parameter percentage is not generally the same as distance percentage.

When this condition is met, the follower advances its path tracker and enters endpoint HOLD. The 150 ms endpoint timeout at [Constants.java:84](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:84) can then make `isBusy()` false while the robot remains far from the endpoint. [AbstractAuto.java:150](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:150) accepts that as completion, ends collection, raises the flower mechanism, and allows the next action to run. The separate eight-second watchdog does not catch a false success.

The reproduction used the actual pinned Foresight/Follower, the current configuration, a stationary simulated localizer at (0.3, 0), and a line from (0, 0) to (100, 0):

| Setting | Second update | After the endpoint timeout | Distance still remaining |
| --- | --- | --- | --- |
| Current 0.998 | HOLD | busy = false | 99.7 in |
| Corrected 0.002 | FOLLOW | busy = true | 99.7 in |

The robot does not teleport. It stops following the planned curve and begins direct endpoint correction, then can report completion after the short timeout. On real curves, this can cut across intended geometry, miss collection locations, skip one-second lowering events, and start shooting from the wrong place.

All 18 autonomous entries share this configuration, as do the registered follower test procedures. BLUE mirroring does not protect against it.

Recommended correction: use `0.002` if the desired parametric end is 0.998. Then test a line, a curved pickup path, a return path, and their BLUE equivalents before tuning speed. Preserve the team's existing choice to rely on Pedro completion; this correction does not require an additional arrival gate. Pedro's official example confirms that tolerance 0.025 corresponds to reaching t = 0.975. [Pedro end constraints](https://pedropathing.com/docs/pathing/reference/endconstraints).

Evidence: [Pedro reproduction source](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/PedroEndConstraintChecks.java) and [results](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/pedro-end-constraint-results.txt).

**B02 — Returning the sticks to neutral can fail to stop drive after a heading fault.** [Main.java:120](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:120) passes `currentPose.heading()` directly into `ManualDrive.fieldCentric()`. There is no finite-heading guard.

With NaN or an infinite heading, the coordinate rotation produces NaN translation values, including when the stick values are zero. Pedro's mecanum conversion produces NaN wheel commands. Its revhub motor cache rejects those commands by returning without writing a replacement power. The previous finite motor powers remain active.

This is Pedro's motor cache, not the project's mechanism wrapper. The project's finite checks on shooter/intake outputs do not cover this drivetrain behavior.

The targeted reproduction ran the real pinned `ManualDrive`, `Mecanum.applyDrive()` and four Pedro motor caches with proxy motors. Reflection bypassed hardware construction only:

```text
Before fault: powers=[0.5,0.5,0.5,0.5], writes=[1,1,1,1]
Neutral sticks + NaN heading: all wheel commands NaN
Retained powers=[0.5,0.5,0.5,0.5], writes=[1,1,1,1]
Explicit finite zero: powers=[0,0,0,0], writes=[2,2,2,2]
```

Positive and negative infinity reproduced the same result. This proves the command-retention behavior. It does not establish how often the real localizer would produce such a fault.

Recommended correction: validate pose/heading at the application drive boundary, clear aim requests, call `follower.stop()` to clear Pedro's HOLD/FOLLOW state, and explicitly stop the drivetrain with finite zero commands when invalid. Put the guard before the `aimingAtHive` early return at [Main.java:118](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:118) so an already active HOLD cannot bypass the application's fault handling. Clearing only the application flag or momentarily stopping motors can leave Pedro's hold controller active. Verify that outputs stay stopped after `follower.update()` and after localization recovers. Make the fault visible to the driver. Evaluate the same output-validation principle for autonomous drive.

SDK STOP, a finite zero command, or recovered finite heading can stop the motors. Neutral sticks alone cannot do so while the conversion continues producing NaN commands.

Evidence: [drive fault reproduction](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/DriveHeadingFaultRepro.java) and [results](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/drive-heading-results.txt).

**B03 — Invalid latest handoff can restore a pose from an earlier run.** [LastPositionStorage.java:19](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:19) returns immediately when the supplied pose is invalid or the alliance is null. It leaves the older position, alliance, timestamp and `dataStored` flag unchanged. That old position remains usable for up to 200 seconds.

Both [Robot.java:117](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:117) and [AbstractAuto.java:104](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:104) save final localization. [AbstractTeleOp.java:178](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:178) consumes whichever saved record remains valid.

Reproduction:

```java
LastPositionStorage.store_data(new Pose(10, 20, 0), Alliance.RED);
LastPositionStorage.store_data(new Pose(Double.NaN, 50, 0), Alliance.BLUE);
```

The current result is `valid_data_available() == true`, coordinates (10, 20), and RED alliance. The newer rejected record does not invalidate the old one.

In a real sequence, an earlier run can save A, a subsequent run can move elsewhere and finish with a localization fault, and the next TeleOp can silently restore A. Field-centric orientation and hive targeting then start from an unrelated prior run.

Recommended correction: clear position availability when the newest handoff is rejected. Alliance retention can remain a separate deliberate selection policy. Consider consuming or explicitly invalidating handoff records when appropriate. Use monotonic elapsed time for expiry so Android clock adjustments cannot extend or shorten the 200-second window.

Evidence: [saved-pose reproduction](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/SavedPoseReproduction.java) and [results](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build/repository-review-2026-10-11/savedpose-output.txt).

**3. Existing calibration blockers and design limits.** These are still important, but earlier documents show that many values are intentionally provisional. They should not be presented as newly introduced regressions.

| Area | Present behavior | Required measurement or decision |
| --- | --- | --- |
| Intake occupancy | Both empty-distance thresholds are 0 mm; normal nonnegative readings cannot satisfy the loaded test | Measure empty/full values, choose margins and verify full-load detection |
| Shooter model | Distance mode produces constant 3000 RPM and 45 degrees | Fit matched RPM/hood data and validate actual shot success |
| Shooter encoder | Configured conversion is 2048 ticks per output revolution | Verify motor encoder resolution, quadrature interpretation and gearing |
| Competition PIDF | Current feedforward term at 3000 RPM is 17.408 before other terms; output clamps to motor-power limits | Tune the deployed controller with actual encoder units and load |
| Hood mapping | 0–90 degrees maps to full servo range 0–1; settling is an estimated 250 ms | Measure safe travel, linkage shape, intermediate angles and settling |
| Flower mechanism | Both servos command endpoints 0 and 1 | Verify safe endpoints and the directions needed by the actual linkage |
| Localization | Pinpoint pod offsets are zero and directions/types are configured assumptions | Measure offsets and verify translation/rotation against known positions |
| Paths | Gains, speeds, deceleration and pickup delays are provisional | Calibrate after B01; validate robot footprint and realistic match interference |

Key references are [Intake.java:142](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:142), [Shooter.java:13](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:13), [Shooter.java:20](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:20), [FlowerIntake.java:10](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java:10) and [Constants.java:41](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:41).

The current loaded boolean is an occupancy signal, not a demonstrated element count or element classifier. Until calibrated, the full-load pause cannot protect ordinary collection. Even afterward, physical capacity and intake stopping distance must establish whether the signal prevents overfilling.

The shooter tuner also has an existing controller mismatch. [ShooterPidfTuner.java:100](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:100) uses the hub velocity controller and writes hub PIDF gains. [Shooter.java:105](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:105) uses RUN_WITHOUT_ENCODER and its own power controller. Their gains are not interchangeable. The project previously chose to retain this split.

At the configured conversion, 3000 RPM requires:

```text
3000 / 60 × 2048 = 102400 ticks/second
```

The SDK's Lynx velocity command clips to ±32767 ticks/second. At 2048 ticks/revolution, that corresponds to approximately 960 RPM. Consequently, a 3000 RPM request in the tuner cannot represent that requested speed through this command path. This is a command-range issue in addition to the untuned gains. Verify the conversion first, then choose a controller-compatible tuning workflow. The older performance review records the SDK source inspection supporting this bound.

A useful minimum is to establish successful shot RPM/hood pairs in the tuner, then verify actual spin-up, steady speed and loaded recovery under competition control before trusting those pairs. A future tuning mode using the deployed controller would avoid this mismatch, if the team revisits that earlier decision.

**Current shooting policy is intentional.** The latest code starts feeding when readiness is satisfied and latches the active shot until release/cancellation. Current regressions deliberately preserve an active shot through subsequent encoder, model and RPM faults. `shoot()` may start from the preceding cached readiness result before that iteration samples the encoder. This creates a one-loop freshness tradeoff, not a new regression. Old reports describing continuous speed-loss feed pausing do not describe today's implementation.

**4. Efficiency improvements with the best expected value.** No runtime speedup was measured during this audit. The following order prioritizes work that can produce useful evidence or shorten real scoring cycles.

| Order | Improvement | Expected benefit | How to evaluate it |
| --- | --- | --- | --- |
| 1 | Correct B01 and calibrate control/localization | Makes route timing and path comparisons meaningful | Measured line/curve trials and complete routes |
| 2 | Record full-cycle and per-action timing | Finds occasional slow loops and expensive actions | Cycle percentiles, action durations, same battery/load |
| 3 | Throttle construction of diagnostic telemetry to 5–10 Hz | Reduces unnecessary Java work every iteration | Compare telemetry on/off with identical route/control inputs |
| 4 | Prepare destination RPM/hood during return driving | Overlaps spin-up and servo settling with transit | Time arrival-to-feed delay and successful deliveries |
| 5 | Measure range-read latency and occupancy response | Reveals bus delays and slow full-load recognition | Time individual calls and full/empty transitions |
| 6 | Calibrate flower timing to the actual pickup segment | Avoids collecting too early/late when route speed changes | Repeat pickups across battery levels and slowdown |
| 7 | Reduce repeated shot-solution allocation | May reduce allocation/garbage-collection variability | Profile first; compare allocation rate and long loops |
| 8 | Cache repeated hub velocity commands if the tuner retains them | Reduces redundant commands in that tuner | Measure bus/loop impact and invalidate on configuration changes |

**Measure full cycles and action durations.** [LoopTiming.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LoopTiming.java) already separates work time from the interval between successive loop starts. It retains only the latest values. Add bounded aggregates for average, maximum and a small histogram or percentile estimate. The 95th and 99th percentiles describe the long-loop tail, which can affect control consistency even when average frequency is high.

Time follower updates, range reads, shooter updates and telemetry separately during practice. Publish summaries occasionally, avoiding a file write or object list on every loop. In autonomous, give actions a name/index and record measured durations so a failed or unexpectedly slow step is identifiable.

The existing Control Hub SHA-1 benchmark compares a particular compute workload. Its roughly 2.5% throughput difference between hubs does not establish a drivetrain, sensor, or scoring-cycle improvement. Use match-length robot workloads, comparable battery/load and warm temperatures to assess actual control performance. The existing benchmark documentation already acknowledges these limits.

**Throttle construction, not merely transmission.** [AbstractTeleOp.java:113](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:113) and [AbstractAuto.java:219](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:219) construct telemetry values and call update each iteration. A 100 ms transmission interval limits communication; it does not avoid preparing values and boxing numbers at control-loop frequency. Gate the whole diagnostic block. Keep follower, input and mechanism control updates at full rate.

[CompConfig.java:12](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:12) keeps loop telemetry enabled in competition mode. After profiling, decide whether Work/Cycle/Hz is useful to the drivers during a match. Minimal fault/state feedback is likely more actionable.

**Overlap physical preparation with transit.** [AbstractAuto.java:133](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:133) queues driving and then shooting. Loading can already trigger preparation, but the normal model follows the current robot position. Preparing the intended destination's matched RPM/hood pair during return driving can reduce settling after arrival. Preserve the existing shot-start readiness check and active-shot policy. This needs an explicit target ownership rule so normal current-position updates do not overwrite the destination target.

**Treat range polling as a latency/traffic tradeoff.** [Intake.java:135](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:135) refreshes both range sensors at most once every 300 ms. This saves transactions but permits almost 300 ms of recognition delay, plus driver/loop latency. Measure before increasing or decreasing the rate. Once thresholds are calibrated, test whether the robot can ingest too many elements before collection stops. A collection-specific refresh interval may be useful.

Hub bulk caching does not combine these I2C range reads into the motor-encoder bulk packet. If they cause loop spikes, consider staggering only after defining acceptable sample age; staggered readings are not simultaneous.

**Keep allocation changes small.** Nonfixed shooter updates still create shot-solution objects. Cache the paired result when distance/mode is unchanged or use primitive internal fields if profiling justifies it. Current nearest-hive selection uses one selected-hive distance calculation; the older suggestion to remove two competing hypotenuse calculations is stale. Do not spend time optimizing work the current code no longer performs.

**Flower scheduling should follow the collection segment.** Lowering uses elapsed seconds, then raising occurs one second later. Battery, interference and corrective driving can shift when the robot actually reaches a flower. After repairing B01, compare elapsed-time scheduling with a known path-progress/near-point event supported by the pinned Pedro API. Do not assume curve parameter increases uniformly with traveled distance.

**5. Useful missing features.** These proposals fit the existing architecture. They are not implementation changes made by this review.

**F01 — A route budget that preserves the intended finish.** Each action has its own watchdog, but the autonomous runner has no total elapsed-time decision about whether another optional cycle can finish. Individually successful slow actions can consume the time needed for parking.

Track elapsed AUTO time, use measured conservative estimates for the remaining cycle and finish, and decide at cycle boundaries whether to continue. For routes that already park, take an appropriate path from the current cycle boundary to their existing park target. This is distinct from the previously declined independent fallback autonomous. Carry-load routes should keep their deliberate finish policy.

The following matrix counts current actions. BLUE inherits the same counts. The deadline allowance is paths × 8 s plus shots × (3 s readiness + 1.25 s feed). It is a sum of local timeout allowances, not a predicted route duration, and Driver Station STOP still terminates operation.

| RED route and BLUE equivalent | Paths | Shot windows | Total feed-window time | Deadline allowance | Intended ending |
| --- | ---: | ---: | ---: | ---: | --- |
| BothFlowersNoGarden | 5 | 3 | 3.75 s | 52.75 s | Park |
| FarMidPark | 5 | 3 | 3.75 s | 52.75 s | Park |
| FarMidGardenPark | 7 | 4 | 5.00 s | 73.00 s | Park |
| GardenFarPark | 5 | 3 | 3.75 s | 52.75 s | Park |
| GardenFarMidPark | 7 | 4 | 5.00 s | 73.00 s | Park |
| OpponentFarGardenPark | 7 | 4 | 5.00 s | 73.00 s | Park |
| OpponentFarMidGardenPickup | 7 | 4 | 5.00 s | 73.00 s | Garden load for TeleOp |
| OpponentFarMidPickupPark | 6 | 3 | 3.75 s | 60.75 s | Park with MID load |
| SoloWithSteal | 9 | 5 | 6.25 s | 93.25 s | Park |

Reducing each of SoloWithSteal's five feed windows by a verified 0.15 s would save 0.75 s. That arithmetic illustrates the value of physical-cycle work; it is not a recommendation to shorten untested feed windows.

**F02 — Minimal driver state and fault feedback during competition.** Main's useful mechanism diagnostics are disabled by competition mode. Autonomous timeout explanations are also hidden behind diagnostic telemetry, so a stopped route may be unexplained while loop timing remains visible.

Expose one compact status line or distinct rumble indications for ready/waiting, full intake paused, invalid localization, invalid shooter sample and auto abort. Send event notices on transitions. This can remain independent of verbose Panels/pose diagnostics.

**F03 — A validated operating range for shot models.** Finite distances outside the sampled range are extrapolated and clamped into legal numerical RPM/hood limits. A finite, clamped result is not evidence that the shot works. Store the minimum/maximum validated distance and either reject out-of-domain model requests or clearly expose that they are unvalidated. Establish a team-selected fixed-shot option where appropriate.

The hood is an open-loop servo command with a timer. It cannot determine whether the servo is stalled or disconnected. Make that distinction clear in driver/tuning language. Physical feedback would be a separate hardware choice.

**F04 — Better experimental shot records.** The tuner retains only the last captured sample string, and edits/captures are session-only. An optional bounded CSV export of measured distance, actual RPM, applied hood command, voltage and manually confirmed shot outcome would reduce copying errors and improve repeatability. Record successes and misses, with repeated trials at each distance.

Fit RPM and hood as a matched pair from the same successful trials. Retest the pair between sample points, under feed load and competition control. The existing manual Desmos workflow can remain; an export does not require an in-robot regression framework.

**F05 — Revisit recovery controls only if the team wants them.** Recovery controls were previously declined. The operational consequence is still worth documenting: Main has no bound jam/eject or complete mechanism-disable control, and `fullLoadHeld` suppresses reverse as well as forward collection. Feeding or a whole-subsystem stop clears the latch.

If the team changes that preference, an explicit recovery state should own intake and transfer, cancel feeding, and deliberately clear/bypass the full latch for ejection. Adding a reverse button alone would not guarantee recovery under the current latch logic.

**F06 — Clearer calibration and setup records.** Maintain one current hardware/configuration sheet with names, ports, motor/encoder models, pod offsets, servo limits and fitted-distance domain. Record which settings have been measured rather than relying on scattered comments. A practice-only preflight utility can show configured devices and important measured constants without powering mechanisms automatically. The stock SDK already supplies hardware/gamepad utilities; use them before building duplicate diagnostic tools.

**6. Online research: BIOBUZZ and current tools.** FIRST's current season resources page lists manual TU04, updated 8 October 2026. TU04 adds an audio cue for the FLOWER ownership timing event; it is not a newly introduced change to the mechanism scoring rules. [Current season resources](https://ftc-resources.firstinspires.org/ftc/archive/2027/game), [Team Update 04](https://ftc-resources.firstinspires.org/ftc/archive/2027/game/tu-04).

The rule/strategy facts relevant here are: AUTO lasts 30 seconds; a hive tip scores 20 points; parking requires partial entry into the loading zone; control is limited to four scoring elements; opponent Nectar may not be controlled; Nectar flower placement opens in the final 60 seconds; pollen removal from flowers is through the bottom. Gardens are not protected, and flower ownership depends on the topmost qualifying alliance Nectar. Launching during a hive tip can interrupt that tip. [BIOBUZZ manual TU04, sections 10–11](https://ftc-resources.firstinspires.org/ftc/archive/2027/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20TU04.htm).

The following ideas are my engineering inferences from those facts and the reviewed code, rather than proven competitive advantages:

- **Optimize confirmed hive tips per cycle.** A timed feed window does not establish a successful delivery or tip. In practice, record delivered elements, successful tips, misses, recovery time and full cycle time. A shorter route with repeatable tips may contribute more than a longer route with extra planned pickups.
- **Coordinate the next hive and collection area with the partner.** Current shooter targeting chooses by robot location, not partner activity or which hive needs the next load. Before adding automation, test a simple driver-selectable target/route policy against the current nearest-hive default. Keep alliance-specific shot geometry consistent.
- **Reserve a final-minute plan.** If the mechanical robot later gains Nectar placement, add deliberate phase feedback and an intentional transition to that task. The current flower mechanism is a pickup device; its autonomous pollen pickup is not itself an early Nectar-scoring bug.
- **Validate mixed-element collection.** Existing ColorRangeSensor devices are used for distance, not current color/type classification. If practice shows mixed loads create a problem, evaluate calibrated local classification or mechanical rejection. The capacity/loaded signal alone cannot distinguish forbidden elements.
- **Use a measured pause around a observed hive tip.** A driver or future independently approved sensor cue can indicate when to pause feeding and retarget. This report does not propose or audit Limelight implementation.
- **Coordinate parking rather than treating it as an afterthought.** Define which robot parks where, retain enough time to get there, and verify actual partial entry with the robot's full footprint. A finite center pose is not proof of scored parking.
- **Be deliberate about opponent-side pickup routes.** Names containing Opponent or Steal do not prove illegality. Validate which elements the route collects and how it interacts with the partner/opponents. Do not substitute route names for a rule or field test.

The project is already on the current published FTC v12.0 release and Pedro 3.0.1 shown by their official release pages at review time. There is no evidence that a blanket dependency update would fix the three application/configuration bugs. [FTC v12.0 release](https://github.com/FIRST-Tech-Challenge/FtcRobotController/releases/tag/v12.0), [Pedro v3.0.1 release](https://github.com/Pedro-Pathing/PedroPathing/releases/tag/v3.0.1).

Pedro 3 brings Foresight, automatic tuning, revised pose/path APIs and built-in follower logging. The repository already registers drivetrain/localizer/Foresight tuners and uses the new API, so the useful opportunity is to tune and observe the installed features. Pedro's claimed line-test speed improvement is for its example robot; it is not a measured F.R.O.G. speedup. [Pedro 3 changes](https://pedropathing.com/docs/pathing/pedro3), [AutoTune workflow](https://pedropathing.com/docs/pathing/tuning).

Use Pedro's debug maps for drivetrain, localizer, algorithm and follow state when diagnosing B01 and field behavior. Log or publish sampled summaries during practice rather than formatting a full map on every competition iteration. [Pedro debugging](https://pedropathing.com/docs/pathing/guide/debugging), [logging API](https://pedropathing.com/docs/pathing/reference/logging).

Foresight also exposes correction-priority/deviation options. If field tests show the robot continues too aggressively while misaligned, evaluate the documented heading ratio, deviation tolerances and cosine scaling after ordinary calibration. Tightening them can slow cycles; measure successful pickups and shots, not just path speed. [Foresight options](https://pedropathing.com/docs/pathing/reference/foresightoptions).

FIRST's hardware tips provide practical non-software improvements: high-count encoders belong on hub ports 0 or 3; high-current servos share power within port pairs; two Control/Expansion Hub data cables can provide redundancy; and onboard-IMU bus sharing should be considered when assigning I2C devices. These are wiring/configuration ideas to check against the actual build, not findings of a current wiring error. [FTC Control and Expansion Hub tips](https://ftc-docs.firstinspires.org/en/latest/tech_tips/tech-tips/tech-tip-hub-tips/tech-tip-hub-tips.html).

**7. Repository maintenance and previously accepted tradeoffs.** Several documents describe an older implementation and can mislead someone trying to tune or debug the current robot.

[Autonomous-from-Visualizer.md:261](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Autonomous-from-Visualizer.md:261) claims autonomous forces fixed 3000 RPM, flywheel waits are unbounded, and initialization is outside cleanup. Current code has INIT shooter selection, a three-second readiness timeout, and an outer lifecycle try/finally covering initialization. Update that paragraph.

[Competition-Readiness-Changes.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Readiness-Changes.md) describes gamepad 2 B as the bypass control, hood control as deferred, and continuous speed-loss feed pausing. Main now uses X for bypass and a three-second B hold for alliance selection, Shooter implements hood control, and active shots deliberately retain feeding. Keep this file explicitly historical or reconcile its behavior descriptions with current source.

The readiness TODO also mixes current and historical names/claims. Its hardware work remains useful, but completed software checks should refer to today's controls and state policy. The existing hood tuning document is more current than several older readiness explanations.

Minor documentation errors: [LogManager.java:54](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java:54) mentions a different default tag than the actual DEFAULTLOG constant; [PeriodicRegistry.java:15](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java:15) references a removed Units.Timer. These do not affect runtime behavior.

[PathConstraints.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java) contains an unused autoPilot profile whose values differ sharply from the active competition configuration. There are no callers. Remove it if unnecessary or clearly label it incomplete, so a future author does not mistakenly tune an inactive profile.

Optional API hardening: CachedMotor/CachedServo constructors accept null devices and invalid cache tolerances. Current callers use valid constants, so this is not an active competition failure. Future NaN tolerance would suppress subsequent changed commands. Constructor validation would make such future errors fail clearly. CachedMotor.get_power() means the last cached power command, not an actual output reading after velocity mode; current Intake usage is consistent with that meaning.

Prior documented team choices remain choices: no added arrival-quality gate, no independent fallback autonomous, no cleanup exception aggregation, no permanent CI/test framework, no added recovery controls, deferred pickup/tip confirmation, retained shooter-controller split, and deferred older-device tuner compatibility. I have not treated their absence as newly discovered bugs.

Pedro bring-up procedures still carry known operational limits: powered tests lack local travel/no-progress watchdogs, some tuning inputs/results are not fully validated, some local cleanup is not protected against exceptions, and STOP during INIT can precede an immediate power command. The documented List.of compatibility limitation affects tuner result paths: Android's native List.of API starts at level 30, while the recorded Control Hubs run Android 7.1.2. No core-library desugaring configuration is present in this project's Gradle files. These paths were not exercised on a hub, and the desktop JVM checks do not validate that compatibility. Earlier-device support was previously deferred; treat it as a known limitation when deciding where/how to tune. [Android List API reference](https://developer.android.com/reference/java/util/List#of(java.lang.Object,java.lang.Object)).

The Gradle loopback failure should be resolved on the development machine and followed by a clean build. The wrapper/AGP settings match the official FTC v12.0 project, so downgrading versions without diagnosing the local failure is not a supported conclusion from this audit.

**8. What is already efficient and reliable.** These are reasons to preserve the current structure while making focused corrections:

- Competition mechanisms have one OpMode-thread owner. No non-Limelight application code currently schedules TaskManager hardware work.
- Paths and queued action order are prepared during INIT. Path and shooter waits have bounded deadlines and STOP checks.
- MANUAL hub bulk caching is cleared in active control/wait loops; encoder readings are reused within mechanism updates.
- Input Y normalization and independent trigger press/full-press/release edges are present. Partial right-trigger release cancels shooting.
- Subsystems are per instance and reset state during INIT. Ordinary partial-initialization cleanup has null checks.
- Mechanism wrappers reject or deliberately handle nonfinite values; exact zero motor stops bypass change tolerances.
- Paired intake range readings and reusable velocity samples reduce redundant hardware calls.
- BLUE routes inherit RED action sequences. Corrections are applied before one 180-degree transform about field center; no duplicate mirroring or missing BLUE registration was found.
- Pose resets and alliance changes cancel stale aim; aiming alone does not feed.
- Logging is not currently a per-loop bottleneck. The unused periodic registry call is negligible.
- Units conversions inspected in the core are consistent. No new conversion defect was found.

**9. Recommended work order and acceptance criteria.**

| Order | Work | Completion evidence |
| --- | --- | --- |
| 1 | Repair B01 | Pinned reproduction no longer ends early; marked-field line/curve/pickup trials pass |
| 2 | Repair B02 | Moving, neutral-stick and active-HOLD fault injections stay stopped after follower updates and localization recovery |
| 3 | Repair B03 | Rejected newest handoff is unavailable; valid handoff and alliance selection still behave intentionally |
| 4 | Establish a clean Gradle build | Fresh compilation, resources/manifest processing and APK build pass |
| 5 | Verify encoder units, safe servo limits and occupancy thresholds | Measured configuration sheet and repeatable full/empty detection |
| 6 | Tune deployed shooter and matched shot model | Repeated successful shots across validated distances, voltage/load and intermediate points |
| 7 | Measure routes and add time budgeting if desired | Full routes finish their selected objective within AUTO with tested finish margin |
| 8 | Improve feedback and throttle diagnostics | Drivers understand waits/faults; control-cycle timing remains stable |
| 9 | Evaluate overlap and lower-priority allocation changes | Measured shorter successful cycles without poorer repeatability |

For each route, record successful trials, failures, total duration, time spent waiting for readiness, actual pickup/shot outcomes and final physical position. Establish team acceptance targets before declaring competition readiness. A passing source check does not measure those outcomes.

The three newly confirmed bugs should be repaired with narrow changes. The most promising further gains are calibrated physical control and overlapping preparation with driving. A broad framework rewrite, extra scheduler use, or speculative arithmetic optimization has no demonstrated benefit from this review.

**Team notes and requested changes**

- Which findings should become implementation work:
- Confirmed hardware/encoder/servo measurements:
- Selected route and finish-time acceptance targets:
- Recovery/control preferences to revisit:
- Additional practice results or corrections to this report:

Written by GPT-6 (Codex), I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn

