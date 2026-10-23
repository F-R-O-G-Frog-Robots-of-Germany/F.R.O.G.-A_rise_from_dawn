# Repository overengineering review — 17 October 2026

Goal: identify custom complexity that can be removed or expressed more simply with the installed FTC SDK and Pedro Pathing, while preserving the intended robot behavior.

The strongest opportunities are the unused scheduler infrastructure, ordinary button handling, unused quantity types, and duplicate autonomous path builders. Most vision/planning complexity implements robot-specific behavior rather than duplicating a simple SDK call. The custom shooter controller and the separation between ideal autonomous poses and field hotfixes are intentional and should remain.

The initial review implemented only the separately requested dual-encoder shooter correction. The user subsequently approved items **1, 2, 4 and 6**, and explicitly retained **3 (unit types for future use)** and **5 (Pinpoint tuner)**. The findings and source locations below describe the original review snapshot; the implementation follow-up records the selected changes and validation.

## Scope and evidence

- Reviewed the current main checkout at `C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn`, including existing uncommitted changes.
- Covered all 72 TeamCode Java files: robot/subsystems, TeleOp and autonomous routines, core helpers, Pedro configuration/tuners, and the complete Limelight package and bench OpModes.
- Examined the 17 desktop regression sources, the camera audit fixtures and runner, Control Hub scripts, Gradle configuration, manifests/resources, and relevant architecture and behavior documentation. Tests and historical documents provide context; their length alone is not a finding.
- All 85 files in `FtcRobotController` still match upstream SDK v12.0 commit `7cf073d`: `git diff --quiet 7cf073d -- FtcRobotController` passed. These supplied Android/bootstrap/sample files do not need a team-specific simplification pass.
- Checked APIs against the actual installed dependencies: FTC SDK **12.0.0**, Pedro **core/revhub 3.0.1**, and **tuning 1.0.1**. Current online examples were corroboration, not a substitute for checking the pinned version.
- No robot measurements or field validation were possible: the robot has not been built yet. Zero hotfixes and provisional calibration values are expected at this stage, not evidence of overengineering.

## 1. Highest-value cleanup: unused scheduler and logging framework

Locations: [TaskManager.java:61](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:61), [PeriodicRegistry.java:18](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java:18), [LogManager.java:21](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java:21).

Together these contain about **511 lines** for named tasks, task replacement, cancellation, interruption, shutdown waiting, callback registration, logging and diagnostics. Repository-wide Java reference searches found no scheduling calls, no periodic callback registrations and no independent `LogManager` users. The remaining external calls only clear/run the empty registry or stop the unused scheduler in `AbstractTeleOp` and `AbstractAuto`.

Recommendation: remove the unused classes and their lifecycle calls. There is no replacement to implement for functionality that currently has no callers. Ordinary recurring competition work already runs in the OpMode update loop. Existing Limelight workers are separate and do not depend on this scheduler.

This would reduce concepts a new contributor needs to understand. It is a maintenance improvement, not a claim that an idle scheduler currently consumes substantial CPU. If actual background scheduling is introduced later, its cancellation and ownership rules may be needed; removing its locks while retaining its threaded API would be the wrong simplification.

## 2. Strong SDK replacement: ordinary button edges and listener indirection

Locations: [GamepadButton.java:38](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:38), [Main.java:46](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:46), [ShooterSelection.java:9](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/ShooterSelection.java:9), [ShooterPidfTuner.java:63](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:63).

There are seven input-framework files totaling about 322 lines, plus separate previous-button fields in the selector and tuner. SDK 12 already provides ordinary press/release methods. The supplied [ConceptGamepadEdgeDetection.java:74](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java:74) demonstrates the feature; it is also present in the [official v12 sample](https://github.com/FIRST-Tech-Challenge/FtcRobotController/blob/v12.0/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java).

For example, a direct per-loop control can be:

```java
if (gamepad1.aWasPressed()) aimRequested = true;
if (gamepad1.leftBumperWasPressed()) flowerIntakeSubsystem.toggle();
```

The same replacement applies to the separate trackers in [ButineTest.java:24](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/ButineTest.java:24), [LimelightPositionTest.java:22](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/LimelightPositionTest.java:22), [LimelightNeuralNetworkTrainer.java:22](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/LimelightNeuralNetworkTrainer.java:22), and [CpuThreadTest.java:65](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/opmodes/CpuThreadTest.java:65). Direct stick reads can also replace analog listener dispatch while keeping the existing Y inversion and deadband scaling.

Preserve these details during any conversion:

- SDK edge methods consume remembered events since their previous query. Query each relevant edge once each loop and share its result between consumers.
- Query/discard disabled configuration actions too. Otherwise a press can be replayed when bypass editing becomes enabled or a bench runner stops driving.
- Preserve the current protection against configuration buttons held across INIT/START. Choose/reset the SDK edge baseline explicitly.
- Keep the custom trigger behavior. Competition right-trigger press is above 0.05; full left-trigger press is at least 0.95 and rearms only after release to 0.05 or below. SDK `setTriggerThreshold()` sets one threshold shared by both triggers and cannot reproduce this two-threshold latch. A small analog helper remains justified.
- Keep `Main.apply_deadband()`. The installed SDK Gamepad source has no equivalent method implementing this deadband and rescaling.

This is a good simplification candidate, but the trigger/feed and input-order regression checks should guard the conversion. The whole 322-line input package is not automatically removable without a small replacement for the analog semantics.

## 3. High-confidence cleanup: unused quantity definitions

**User decision: retained unchanged for future use.** This original finding is not part of the implemented cleanup.

Location: [Units.java:15](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:15).

Roughly **324 lines** define currently unused types: `Acceleration`, `Angle`, `AngularVelocity`, `Current`, `Force`, `Position`, `Torque`, `Velocity`, `Weight`, `APInterpolation` and `ObeliskRead`. References inside those unused definitions do not make them active robot features.

Recommendation: remove the unused definitions. Keep `Alliance`, `RobotState`, and the actively used `Length` and `Time` types unless their callers are simplified separately. Preserving useful unit safety does not require maintaining a general physics quantity library before anything uses it.

The unused angle normalizer also overlaps SDK functionality, but deleting unused code is simpler than migrating it to another API.

## 4. Reuse the path builder already in AbstractAuto

Locations: [AbstractAuto.java:261](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:261) and its two nested path classes starting at lines 304 and 322.

`BothFlowersNoGardenPaths` and `SoloWithStealPaths` repeat calls that mirror each supplied pose, create a Pedro curve and select tangent heading. `tangent_path(Pose...)` at line 265 already does that work.

Recommendation: use the existing helper at those route callsites and remove the two wrapper objects/classes. Preserve the garden segment's explicit `Paths.line(...)` rather than silently changing its path representation. This can remove roughly 40 lines of pass-through code while keeping route sequencing, geometry, ideal/hotfix definitions and alliance mirroring.

The upper/lower hive forwarding overloads at lines 273–287 also delegate directly to the same varargs helper. They could be consolidated, though their names can help a reader follow the route. Removing those names is lower value than removing the duplicated implementations.

## 5. Use Pedro configuration mutation in the Pinpoint tuner

**User decision: leave PinpointTuner unchanged.** This original finding is not part of the implemented cleanup.

Location: [PinpointTuner.java:196](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:196).

The offsets procedure creates a config through `create_config(...)`, then repeats the config construction for custom pods solely to select a different reset mode. The installed `ConfigVar` supports changing just that setting:

```java
if (customPodScalar.isPresent()) {
    config.resetMode.set(PinpointLocalizer.ResetMode.RESET_AND_RECALIBRATE_IMU);
}
```

This avoids keeping two copies of the pod directions, units, scalar and offset settings synchronized. Pedro documents the same `.set(...)` pattern in its [configuration guide](https://pedropathing.com/docs/pathing/guide/modifiers).

The subsequent custom-pod `localizer.reset()` at line 214 also repeats the installed `PinpointLocalizer` constructor's `reset(); update();`. Its reset includes a 500 ms wait. Removing this second reset is a small tuner cleanup once the selected reset mode is preserved.

## 6. Small direct API improvements

These are optional cleanups, not reasons for a broad rewrite.

| Location | Current extra work | Simpler option and limits |
| --- | --- | --- |
| [PoseMirroring.java:9](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java:9) | Converts an existing radian heading to degrees so a degree factory can convert it back | Use `PoseFactory.radians().mirrorAroundPoint(72, 72)` and pass the heading directly. Keep alliance policy. The [PoseFactory reference](https://pedropathing.com/docs/pathing/reference/posefactory) documents both input units. |
| [OptimalPoses.java:28](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java:28) | Custom `Delta` stores offsets and implements componentwise pose addition | Pedro `Pose.plus(Pose)` already adds the components. Retain finite validation, degree conversion and `FIELD_FIX`; either delegate to Pedro or return a Pedro offset pose. Keeping `Delta` for the distinction between an offset and an absolute pose is reasonable. Do not delete the confirmed ideal/hotfix workflow. |
| [ForesightTuner.java:150](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:150) | Creates `Vector2D` objects only to read x or y | Read the installed `Twist`/`Velocity` `.vx` or `.vy` fields directly. Similar sites are at lines 190, 228, 439 and 531. Preserve which sites use `twist()` versus `velocity()`. |
| [VisionPose.java:30](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/VisionPose.java:30), [Butine.java:158](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java:158) | Two handwritten signed angle normalizers | Consider Pedro `Angle.normalizeSigned` or SDK `AngleUnit.normalizeRadians`. Preserve finite-input policy. These APIs map positive pi to negative pi, unlike the present helpers, so preserve/test the exact half-turn convention before replacement. |
| [LastPositionStorage.java:13](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:13) | Wraps wall-clock milliseconds in a quantity object and converts back to compare a duration | SDK `ElapsedTime`, reset after accepting a stored pose, can express the 200-second expiration directly with monotonic elapsed time. Keep `dataStored`, alliance handling and pose validation. The class is already small. |

`Pose.plus` may allocate a new equal pose for a zero offset where `Delta.plus` currently returns the original reference. No identity-dependent autonomous use was found, but preserving the existing zero fast path is easy if retaining `Delta`.

Additional unused code: [PathConstraints.java:5](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java:5) has no callers, and the estimated-duration intake overload in [AbstractAuto.java:126](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:126) is unused. These can be removed if no concrete use is planned.

## Complexity that is justified

- **Custom shooter control:** explicitly intentional. Its full-power spin-up, overspeed cutoff and near-target PIDF behavior should remain; the tuner continues to use a different hub controller. No recommendation to switch competition to SDK velocity control remains.
- **Ideal poses, hotfixes and `FIELD_FIX`:** explicitly intentional. The robot has not been built, so current zero corrections are expected.
- **Explicit motor stop:** Pedro 3.0.1 `Follower.stop()` clears follow state and selects IDLE; it does not immediately zero drivetrain output. Keep the accompanying `follower.drivetrain.stop()`.
- **Autonomous waits, deadlines, update loops and invalid-pose aborts:** these enforce mechanism/route behavior that the installed Pedro core does not replace. The [current autonomous guide](https://pedropathing.com/docs/pathing/guide/auto-usage) also describes Ivy, but it is an additional command dependency absent from this repository. Adding a new framework is not an automatic simplification.
- **Pedro tuning procedures:** the installed tuning artifact supplies tuning infrastructure, not equivalent ready-made replacements for all four adapted Quickstart procedures. Their length alone is insufficient evidence for removal.
- **Command caches and numeric guards:** SDK Lynx controllers already cache exact commands, but these wrappers also suppress changes below an epsilon, reject nonfinite values, preserve selected commands and guarantee an exact stop bypass. Replacing them with plain `setPower`, `setPosition` or `Range.clip` would change behavior.
- **Sensor and targeting snapshots:** readiness and telemetry reuse accepted samples, and pose validity is shared between drive guards and targeting. These avoid repeated hardware reads and preserve the established fault policy.
- **Vision projection, capture-time pose history, pipeline/freshness checks and pollen mapping:** these do more than reading `getLatestResult()` or frame staleness.
- **Butine ordering, capacity and deadline planning:** robot-specific collection decisions have no single SDK/Pedro equivalent.
- **CPU-affined workers and ownership/cancellation:** these implement the documented worker design; SDK USB polling does not replace all their geometry, mapping and planning work.
- **Signed heading interpolation:** do not replace `Limelight.pose_at()` with Pedro 3.0.1 `Pose.interpolate()` blindly. The installed implementation uses an unsigned angular difference; the local code preserves direction.
- **Blue autonomous entries:** small subclasses already reuse red geometry while allowing separate SDK OpMode registration.
- **Fixtures and Control Hub scripts:** fixtures separate fake camera transport from real-signature compilation; the diagnostic scripts have reasonable bounds and process handling. No useful SDK replacement was found.

## Requested shooter correction implemented

Location: [Shooter.java:406](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:406).

| Encoder samples this update | Feedback used | Motor/control action |
| --- | --- | --- |
| Both finite | Arithmetic mean of both velocities | Existing custom controller drives both motors with the same output |
| Only left finite | Left velocity | Existing custom controller continues |
| Only right finite | Right velocity | Existing custom controller continues |
| Neither finite | No new feedback; last accepted displayed RPM remains | No flywheel motor writes; integral, previous error and controller timestamp stay unchanged |

Both encoders are sampled exactly once per update. Halving each reading before adding avoids overflow from summing two large finite readings. Existing nonfinite-sample handling defines invalid as NaN or either infinity; zero remains a finite reading.

A rejected encoder pair clears current readiness; an already latched shot keeps feeding under the existing policy. As requested, a target edit does not change flywheel power on a skipped tick. Valid feedback resumes control, and explicit `stop()` still stops both motors immediately. Recovery testing also exposed a zero-target derivative transition that could command reverse power; a valid zero target now directly resets the controller and commands zero.

The shooter tuning document and regression fixtures were updated to describe/exercise the dual-encoder policy. Existing unrelated working-tree changes were preserved.

## Verification before the approved cleanup

All **89 TeamCode and desktop regression Java sources** compiled against cached FTC 12.0.0/Pedro 3.0.1 dependencies using Java 17 with Java 8 source/target settings. Six regression programs passed:

| Program | Result |
| --- | --- |
| `PollenFlowChecks` | 5,280 assertions, including 864 state combinations, dual-encoder averaging/fallback, all nine pairs of nonfinite faults, repeated skipped ticks, recovery and STOP |
| `ShooterHoodChecks` | 127 checks |
| `ShooterMotorTunerChecks` | 22 checks |
| `TeleOpHiveAimChecks` | 1,378 checks |
| `AutonomousPoseFaultChecks` | 83 checks |
| `FiniteValueChecks` | 201 checks |

`git diff --check` passed. The normal Android Gradle compile was attempted with `:TeamCode:compileDebugJavaWithJavac --offline --no-daemon`, but Gradle failed before source compilation with `java.io.IOException: Unable to establish loopback connection`. Direct compilation and desktop checks do not establish a clean APK build or robot performance. No new physical or Limelight runtime claims are made by this review.

## Approved implementation follow-up

| Review item | Final status |
| --- | --- |
| 1 — dormant scheduler/logging | Removed `TaskManager`, `PeriodicRegistry`, `LogManager` and their remaining lifecycle calls. Marked their old explanation/error documents as historical. |
| 2 — input framework and button trackers | Removed all seven custom input classes. Main, INIT shooter selection, shooter tuner and four vision bench OpModes use native SDK button edges. Sticks are read directly; Main retains two trigger latches for the original thresholds and rearming rules. |
| 3 — quantity types | Retained every definition in `Units.java`, as requested for future use. |
| 4 — duplicated autonomous builders | Removed the two nested path-builder classes. Routes use shared tangent/line/shooting helpers. All nine red route classes use the shared shooting helper; blue registrations continue to reuse them. Garden remains an explicit Pedro line. |
| 5 — Pinpoint tuner | Unchanged, as requested. |
| 6 — small API improvements | Applied radian pose factory, Pedro pose addition, direct motion-component reads, shared Pedro-backed angle normalization, SDK elapsed-time pose expiration, and removal of the unused path config/intake overload. |

For item 4, the robot still visits the same points with the same heading rules. The change removes repeated code that constructed those paths, so one shared implementation now serves all affected routes.

Important preserved behavior: custom shooter control and dual-encoder feedback; finite-value/STOP guards; ideal poses and per-point hotfixes; `FIELD_FIX`; degree input for corrections; zero-hotfix reference reuse; red/blue transformations; original action order; stick inversion/deadband; trigger thresholds; disabled-action event consumption; and INIT/START held-button protection. Native SDK edge histories are explicitly reset and seeded at START. Vision bench trigger edges use the next float above 0.5 because the SDK tests `>=`, preserving their former strict `> 0.5` condition. Angle helpers preserve the previous positive-pi convention and distinct nonfinite-input policies.

`Units.java`, `PinpointTuner.java` and `Shooter.java` have identical SHA-256 hashes before and after this cleanup. The previously accepted shooter correction remains in place.

Final validation: a fresh compilation of all **79 current production and desktop regression sources** passed against the pinned FTC/Pedro dependencies. All **18 desktop regression programs** and all **3 camera transport/worker fixture programs** passed. Selected results:

| Program | Final result |
| --- | --- |
| `PollenFlowChecks` | 5,285 assertions; 864 state combinations and 25 trigger-sequence assertions |
| `TeleOpHiveAimChecks` | 1,389 checks, including SDK button transitions and configuration/START behavior |
| `ShooterMotorTunerChecks` | 35 checks, including held/released INIT controls and disabled-action event consumption |
| `ShooterHoodChecks` | 127 checks |
| `PathSimplificationChecks` | 226 red/blue path, heading, hotfix and mirroring checks |
| `PathCompletionChecks` | 66 checks |
| `LastPositionStorageChecks` | 63 checks, including expiration and timer reset |
| `AutonomousPoseFaultChecks` | 83 checks |
| `FiniteValueChecks` | 201 checks |
| `ButineRegression` | 1,828 assertions, including the angle contract |
| Camera frame/runner/worker fixtures | 40 / 73 / 68 checks |

The other eight vision/planning/worker desktop programs also passed. An independent source review checked SDK event consumption and the actual SDK INIT/START lifecycle. No remaining correctness issues were found. Searches found no Java references to the removed framework or old path-builder API. `git diff --check` passed.

The Android Gradle compile was attempted again after implementation and still failed before Java compilation with `java.io.IOException: Unable to establish loopback connection`. No APK or physical robot validation is claimed.

## Requested adjustments

Changes you would like to make to these recommendations:

- Add requested adjustments here.

Exact API evidence came from the cached `RobotCore-12.0.0-sources.jar`, `Hardware-12.0.0-sources.jar`, `core-3.0.1.jar`, `revhub-3.0.1-sources.jar` and `tuning-1.0.1-sources.jar`. Source/bytecode checks covered gamepad edges and thresholds, Lynx command caching, pose arithmetic/factories, follower stopping, motion components, configuration mutation and localizer initialization.

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
