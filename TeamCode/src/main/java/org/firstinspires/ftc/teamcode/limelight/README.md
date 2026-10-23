# Limelight and Butine — Java port

This folder brings the Limelight and Butine functionality from `StudioProjects/butine test` (formerly Ma Douce) into F.R.O.G. Competition TeleOp and autonomous now service it through `CompetitionWorkers`; authored routes retain their existing behavior until an explicit collection action is added.

## Competition lifecycle integration

Both competition bases construct/configure the required workers during INIT, start them before optional control-thread affinity, and service their publications after each localization update. `CompConfig.VISION_ENABLED` enables the optional vision worker by default; missing camera initialization does not disable manual TeleOp or static autonomous. Override `vision_enabled()` for an OpMode that needs no vision. `CompConfig.CONTROL_CORE = -1` leaves the control thread's eligible mask unchanged; a selected zero-based CPU is applied once during INIT, verified, and its original mask is restored during cleanup.

An autonomous `queue_collection(depot, params, speedScale)` action opts that OpMode into the planner worker and its required vision worker. The depot is already in the actual field frame; `params.remainingS` counts the match allowance from autonomous START, with elapsed time deducted when the action begins. An accepted collection action runs intake and lowers the flower intake, continues servicing the common loop while planning/collecting, then stops intake and raises it. Scoring remains a separately authored action. Existing routes have not been given collection actions automatically.

TeleOp can override `planning_enabled()` and explicitly call `request_collection(...)` or `request_relocalization()`; no new driver buttons are assigned. Main preserves assistance ownership while sticks are neutral, and cancels it before manual input, aim, pose reset or alliance changes take over. Startup alone never moves the robot or requests a correction. Use `configure_workers(CompetitionWorkers workers)` to set camera/map calibration and worker core preferences before owners start. Caller code treats published planner arrays as read-only.

The important gamepad/Pedro/shooter/intake loop remains a single thread. No per-subsystem scheduler or compute-time budget was introduced. Cleanup cancels work, attempts independent safe hardware outputs, waits bounded intervals for original worker owners, and finally restores control affinity even when another cleanup step fails. The robot has not been built; camera calibration, physical assignment, pickup behavior and performance remain unverified.

The source was inspected through commit `84c0a01` and the working copy's Limelight-related files, including uncommitted Butine runner/header changes and the untracked relocalization runner. The latest implementations incorporate the earlier fixes rather than reproducing each old revision. The original explanations and review history are preserved in [docs/ma-douce](docs/ma-douce); those documents describe C++ and sometimes older defaults. This README and the Java classes describe the port.

## What is here

| Original | Java equivalent | Purpose |
| --- | --- | --- |
| `LimelightBridge.java`, `limelight.h/.cpp` | `Limelight`, `LimelightGeometry`, `LimelightCalibration` | FTC camera lifecycle, results, pipelines, snapshots, projection and AprilTag poses. Java calls the FTC SDK directly, so a JNI buffer is unnecessary. |
| `pollen_map.h/.cpp` | `PollenMap`, `Pollen`, `VisionPose` | Persistent field map, stable target IDs, visible-region replacement, injection, removal and statistics. |
| `butine.h/.cpp` | `Butine` | Time-based closed-run planning, capacity, lookahead, deadlines and the uncapacitated mode. |
| `butine_runner.h/.cpp` | `ButineRunner`, `PedroButineDrive` | Build and drive one planned run, then solve again when called again. |
| `limelight_runner.h/.cpp` | `LimelightRunner` | Camera/map updates and a gated AprilTag correction between runs. |
| `butine_test.cpp` | `opmodes/ButineTest` | Synthetic field, planner inspection, parameter changes and held-trigger driving. |
| `limelight_position_test.cpp` | `opmodes/LimelightPositionTest` | Range calibration and pipeline/pose diagnostics. |
| `limelight_neural_network_trainer.cpp` | `opmodes/LimelightNeuralNetworkTrainer` | Manual and timed snapshots with a distinct session name. |
| `butine_harvest.cpp`, `butine_showcase.cpp` | Runner calls for later integration | The reusable collection and relocalization behavior is ported. Autonomous wiring is deliberately deferred at the owner's request. |

## Behavior agreed for F.R.O.G.

Physical-field collection uses the swept front intake opening. One pass can collect neighboring pollen without visiting their centers separately. The robot holds a constant collection heading and can strafe; at the depot it may first turn in place to reach pollen near a wall. Intake-off return segments rotate toward the scoring pose. Collection ends at that pose; the calling autonomous decides when to shoot using F.R.O.G.'s current Shooter and Intake logic.

`VisionPose` uses field inches and heading radians. Observed targets are already in the actual field frame. Mirror authored start/depot coordinates once before passing them to these classes; do not mirror camera detections. AprilTag botpose uses FTC field-center coordinates in meters and is converted to the 144-inch field frame. Check the configured field map and camera mounting before using those fixes on the robot.

## Camera and map

Configure the USB device as `limelight`. The source uses **pipeline 0 for the pollen detector** and **pipeline 1 for AprilTags**; configure those pipelines on the camera. The detector's class label must be `pollen`. A pipeline switch is asynchronous, so the wrapper waits for the reported result pipeline to match before using it.

```java
Limelight camera = new Limelight(hardwareMap);
PollenMap map = new PollenMap();
camera.init();

// In the existing OpMode loop, after updating odometry:
VisionPose here = new VisionPose(follower.pose().x(), follower.pose().y(),
        follower.pose().heading());
camera.update(here);
map.update(camera);

// In the OpMode's finally/stop cleanup:
camera.stop();
```

Update camera results and the pose trail frequently, around the source's 10–20 ms loop period. Read the heavier status block with `update_full(here)` around every 250 ms. A detector frame is authoritative only over its capture-time visible wedge. Empty fresh detector frames clear that wedge, repeated/missing/stale frames supply no new evidence, and switching to AprilTags freezes detector publication. Detections and map clearing use the same latency-compensated pose.

Camera dimensions and mounting are in `LimelightGeometry`. The inherited quadratic range curve is a **placeholder**, and pinhole projection is initially disabled. Measure height, downward pitch, offsets and yaw, then calibrate the range on the bench before driving to real detections. Runtime adjustments do not survive an app restart; copy measured values into the geometry defaults. The position test shows both models for comparison.

## Planning

```java
Butine.Params params = new Butine.Params();
params.capacity = 4;              // Competition possession limit; physical-field routes are capped at four.
params.intakeWidthIn = 18.0;      // Directly adjustable opening width, inches.
params.intakeForwardOffsetIn = 9.0; // Opening position ahead of the localization reference.
params.intakeOvershootIn = 1.0;   // Continue in the same travel direction after the last contact.
params.lookaheadRuns = 2;         // Number of future runs offered; execute only the first.
params.remainingS = 25.0;         // Caller supplies remaining match time.
params.scoreOverheadS = 2.0;      // Estimated caller-controlled scoring time.
VisionPose depot = new VisionPose(72, 24, Math.toRadians(90)); // Example clear pose; choose your scoring setup.
Butine.Plan plan = Butine.solve(map.snapshot(), here, depot, params);
```

Only execute the first fitting run, then solve again against the updated map. Physical-field planning uses bounded greedy passes with front-mouth contact checks against every observed pollen, including candidates outside the seed limit. The last contact is followed by `intakeOvershootIn` inches along the same incoming direction. The extension is checked for obstacles and additional contacts; a fifth contact rejects that pass. The intake remains on through the extension. `Run.ids` and `Run.poses` identify actual pollen; `Run.route` contains the immutable robot-center waypoints, contact events, intake flags, and continuous speed/time estimates. The geometric corner estimate uses `kinematics.junctionDeviationIn`; it is not measured chassis performance.

The legacy point planner remains available with `sweepIntake = false` or `ButineField.unrestricted()` for mathematical calibration. Its `iterationCap`, `stagnationLimit`, and `localSearchPasses` bound the original small-run permutation/local-search optimization. These work limits do not impose a wall-clock solve cap.

`solveUs` records elapsed solve time and `iterations` records completed legacy search attempts. `remainingS` specifies remaining match time: continuous deadline fitting includes depot alignment, travel through the overshoot, return heading, and `scoreOverheadS`. Continuous collection adds no pickup dwell or planned stops at intermediate endpoints.

### Robot footprint and field obstacles

`new Butine.Params()` uses immutable `ButineField.biobuzz()` geometry: an 18 × 18-inch rectangle centered on the localization reference point, a maximum height of 18 inches, and 1 inch beyond the robot body. The all-heading center buffer is its half diagonal plus 1 inch, approximately 13.727922 inches. Physical obstacles and the field perimeter use the same buffer, including when the robot rotates.

The four FLOWERS and two low frame-support envelopes come from the official STEP model. The center passage is open for this robot: fixed supports were clipped to Z=0–19 inches, under-tile bars do not obstruct passage, and a conservative bound over the Hives' allowed tipping sweep remains above 19 inches. The all-heading center corridor runs from X≈67.63 to 76.37 through the frame. [The geometry record](docs/Butine-Center-Underpass-Geometry-2026-10-18.json) retains the source hash, bounds, vertical checks and official links.

To configure the model before submitting a request:

```java
params.field = ButineField.biobuzz(
        18, 18, 18, // Robot length, width and maximum height, inches.
        1,          // Body clearance, inches.
        0, 0);      // Additional field and tracking reserves, inches.
```

The reserves remain distinct from body clearance and increase both obstacle expansion and wall inset. If height + body clearance + field reserve exceeds the verified 19-inch support slice, this factory conservatively blocks the whole center until a suitable new slice is derived. Include every mechanism in its collection configuration and use actual reference-point offsets if the body is not centered. The defaults describe nominal CAD geometry; they do not establish tracking accuracy or a measured physical gap.

Routing uses a bounded visibility graph with corners 0.5 inch outside expanded obstacle boxes. Continuous collection follows the entire validated polyline in one Pedro compound path, disabling predictive corner skipping. Intermediate segments do not request end braking or holding; physically necessary slowing at corners and reversals remains possible. The runner removes pollen only after the actual front-mouth sweep makes contact, never from reaching a transit vertex or observing path progress alone. It checks live geometry and deadlines; invalid motion or a newly observed overflowing contact aborts with intake off. The legacy per-leg mode retains its routed recovery and 0.25-inch transit tolerance.

Existing scoring points only 9 inches from a wall fail the all-heading envelope and require a deliberately chosen collection depot. No authored scoring pose is moved automatically. `ButineField.unrestricted()` is an explicit free-plane model for mathematical calibration and legacy desktop oracles; it disables footprint and field checks and is not the competition default.

## Later integration and checking

Use the runner in the same loop as camera/map and follower updates. It starts one run per call and returns control between ticks. The Pedro 3 adapter submits the complete collection/return compound path once. Motor output follows `intake_required()` after each runner update; `queue_collection` already wires this into the autonomous mechanism loop. Intake stays off during planning and initial depot alignment, stays on through collection overshoot, then turns off for return.

The autonomous loop checks the existing REV color/distance `Intake.loaded()` result and its `full_load_held()` latch before advancing collection state. A full signal immediately stops the old collection path and disables intake, cancels pending planning, and installs an intake-off return from the live pose through the same field geometry. Repeated full signals leave that return untouched; an already-running depot return is preserved. A sensor-full return can complete with zero visually counted pickups: sensor evidence does not delete unvisited pollen or invent collected IDs. Invalid/blocked localization or an impossible deadline aborts rather than dispatching an unchecked shortcut. Standalone callers invoke `collection.return_to_depot_if_loaded(loaded)` before each runner update, with motor output disabled immediately when loaded is true.

```java
PedroButineDrive drive = new PedroButineDrive(follower);
ButineRunner collection = new ButineRunner(map, drive);
collection.run_butine(depot, params, 0.5); // One run, using half the nominal cruise speed.

// In each existing loop, alongside the camera/map updates above:
follower.update();
boolean loaded = intake.full_load_held() || intake.loaded();
if (loaded) intake.off();
collection.return_to_depot_if_loaded(loaded);
collection.update();
if (collection.intake_required()) intake.forward(); else intake.off();

// When collection is finished, the caller decides whether to score or start another run.
// Call collection.stop() when the OpMode stops.
```

Failed targets remain in the map but are excluded by ID and location for this runner instance. Use `clear_unreachable()` to allow retrying them after clearing an obstruction or correcting localization. `stats()`, `last_plan()`, `legs()` and `last_report()` provide the diagnostics from the original runner without a separate control framework. A `LimelightRunner(camera, map, drive)` can perform camera/map updates and `relocalize()` between runs; call its `update()` every loop until `is_busy()` is false before starting collection again.

Check the return value of `run_butine()`. `false` can mean the drive is already occupied or there is no fitting collection run; consult `state()` and `last_report()`. An empty plan does not drive the robot to the depot. Continuous return requires position within 0.5 inch, heading within 2 degrees, and measured translation speed at most 1 inch/s, in addition to Pedro completion. Pedro holds the depot while settling; its timeout alone cannot declare success. The legacy point mode retains its older 6-inch/8-degree arrival gates. Speed scaling changes the Foresight velocity constraint.

The three bench OpModes are disabled initially. Enable the one being tested deliberately after checking mounting, pipeline configuration, drive power and field coordinates. No competition autonomous, `Robot`, Shooter or Intake source was modified by this port.

The regression checks under `tests/java/org/firstinspires/ftc/teamcode/limelight` exercise map replacement, projection, planner constraints and runner state transitions without commanding robot hardware. The project should also compile with `gradlew.bat :TeamCode:compileDebugJavaWithJavac` before deployment.

The FTC SDK calls were checked against the installed 12.0.0 classes and [Limelight's FTC Java programming guide](https://docs.limelightvision.io/docs/docs-limelight/apis/ftc-programming), including asynchronous pipeline switching, result freshness, angular detector coordinates, camera initialization and snapshot capture.

Historical validation on 10 October 2026, before the compute-time cap was removed: the entire Java package and its bench OpModes compiled against the installed FTC 12.0.0 and Pedro 3.0.1 libraries with Java 8 source/target compatibility. The expanded audit passed 6,209 checks, including independent geometry calculations, 100 exhaustive small-field planner oracles and camera/runner fault traces. See [the bug audit](docs/BUG-AUDIT-2026-10-10.md) for corrected defects, calculated outputs, reproducible commands and coverage limits. The main checkout's offline Gradle build, including a no-daemon retry, could not start because Gradle reported `Unable to establish loopback connection`. No on-robot validation was performed.

Validation on 11 October 2026 after compute-time-cap removal: the updated standalone audit compiled the production package against real FTC 12.0.0 and Pedro core 3.0.1 signatures and passed 6,350 checks. The finite-work search retains match-time and scoring-feasibility checks and elapsed `solveUs` telemetry. The full `gradlew.bat --offline --no-daemon :TeamCode:compileDebugJavaWithJavac` command failed before compilation with `Unable to establish loopback connection`. No on-robot timing or hardware validation was performed.

## Background workers and CPU diagnostics

`LimelightWorker` owns camera processing on a dedicated Java thread that requests CPU 1; `ButineWorker` solves copied requests on demand on CPU 2. Both verify actual affinity and report failure instead of assuming the requested core was assigned. `preferredCore = -1` leaves the inherited mask alone. Start workers before optionally binding the control loop to CPU 0, because child threads can inherit their creator's affinity.

Use `ButineRunner(map, drive, planner)` for background collection planning and `LimelightRunner(vision, drive)` for background camera operations with main-loop pose corrections. Existing synchronous calls remain supported. The background collection runner stays in `PLANNING` until a current, feasible result is accepted; drive calls remain on the OpMode thread. Camera/map configuration belongs before worker start, and camera commands go through the worker while it owns the device.

The disabled **Limelight CPU thread test** (`opmodes/CpuThreadTest.java`) compares an original-Android-mask baseline with the main thread eligible on CPU `{0}`, `{0,1}` or `{0,1,2}`. The three restricted modes keep vision on CPU 1 and planning on CPU 2; the baseline removes both worker bindings too. One Java thread still runs on one CPU at a time. It displays verified assignments, allowed CPU lists/masks, utilization, frequency, temperatures, worker timings and retained per-mode loop summaries including p95/p99. X cycles modes; Y selects idle or repeatable synthetic planner load and clears old comparisons. A/B provide manual solve/cancel diagnostics, labelled separately from repeatable comparisons; dpad pages every app thread. The test creates no drive hardware and uses a labelled synthetic camera pose. See [controls, measurement details and limitations](docs/THREADING-2026-10-16.md) before enabling it. Competition routes explicitly opt into collection through `queue_collection(...)`.

## Hive pose from a moving tag

`HivePoseEstimator` implements the [hinge calculation](docs/Hive-Hinge-Full-Numerical-Example-2026-10-18.md). `HiveLimelightObservation` converts a full SDK per-tag pose to the estimator's camera coordinates. `HiveGeometry` supplies the 16 tag models and a clearly marked placeholder camera mount. This is an isolated calculation: it creates no threads, changes no pipelines, and applies no pose corrections.

```java
HivePoseEstimator estimator = new HivePoseEstimator(
        HiveGeometry.tag_models(), HiveGeometry.camera_mount());
HivePoseEstimator.Observation observation = HiveLimelightObservation.from_fiducial(
        fiducial, captureTimeMs, timingAssumed);
HivePoseEstimator.Result fix = estimator.estimate(
        observation, gravityAtCapture, gravityAssumed);
// fix == null: unusable input. Otherwise read x, y, heading, z, axisErrorDeg,
// captureTimeMs and usesAssumedValues. No robot pose is changed here.
```

`fiducial` comes from an already-owned camera's `LLResult.getFiducialResults()`. Enable full 3D AprilTag tracking with the correct 3.25-inch black-square size. These are relative tag measurements, not a static Hive field-map pose or MT2 yaw. Gravity is the downward direction in robot forward/left/up axes, aligned to image exposure; if a level robot is assumed, pass `{0,0,-1}` and `gravityAssumed=true`. The adapter also supports `from_result(...)` for copying all usable tags from one frame. Results remain per-tag; there is no multi-tag optimizer or averaging layer.

The caller supplies exposure time on its own monotonic millisecond clock and handles freshness, duplicate frames and gravity alignment. Set `timingAssumed=true` when estimating exposure time from receipt/latencies; SDK staleness alone is not image age. Position is in the report's centered field frame: +X audience-right, +Y away from the audience, +Z up; heading is counterclockwise radians. It is not automatically converted into `VisionPose` or Pedro coordinates. `z` reports inferred robot height and `axisErrorDeg` reports axis/gravity disagreement before leveling; neither is an accuracy score or an automatic acceptance threshold.

Geometry comes from the [official manual, §9.6/§9.9 and Fig. 9-15–17](https://ftc-resources.firstinspires.org/ftc/game/manual) and [official STEP](https://ftc-resources.firstinspires.org/ftc/field/field-cad-step). The inspected STEP SHA256 is `05b35961c7df847741031f00fda73ddd068537f809e92a11b1cd59a94bcc8331`. Shooting positions are not used.

| Value | Origin |
|---|---|
| Hinge points `(−12.75,0,43.95)` / `(+12.75,0,43.95)` inches | Nominal CAD spacing and manual height, in the centered frame |
| Red scoring IDs 30–33; red audience 34–37; blue audience 38–41; blue scoring 42–45 | Manual layout |
| Tag black-square side 3.25 in; strip offsets `6.5,2.75,−2.75,−6.5` for increasing IDs in the chosen native basis | Manual dimensions and artwork |
| Tag-to-hinge in-plane offset `14.269059480313` in | CAD hinge at sticker Y `16.519059480313`, minus tag center Y `5−2.75=2.25` |
| Tag-to-hinge normal component `1.488167842276+0.01` in | CAD slab offset plus **assumed** printed surface at sticker Z `+0.01` |
| Along-axis correction `−0.000944881890` in audience / `+0.000944881890` in scoring | CAD assembly placement |

The native tag-to-sticker axis/sign convention and code alignment remain explicitly **assumed**, because the CAD lacks tag textures and Limelight's target-axis prose is handedness-ambiguous. The adapter's numerical rotation order is independently verified against official Limelight code and paired inverse-pose data. The physical print-side assumption and unknown robot mounting are also commented in `HiveGeometry`. Consequently default results have `usesAssumedValues=true`; they are provisional geometry estimates. Custom measured tag models and mounting can replace these defaults without changing the estimator.

The desktop audit tests ±30° circular motion, the complete numerical example, 1,500 arbitrary full-pose cases with camera offsets and robot tilt, invalid inputs, immutable snapshots, SDK axis/unit conversion and one SDK-to-final-robot-pose case. Run `tools/limelight-audit/run.ps1`. This verifies software and SDK compatibility, not real-world accuracy or a 100-ms measurement deadline.
