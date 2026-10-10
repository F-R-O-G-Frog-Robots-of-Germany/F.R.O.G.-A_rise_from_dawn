# Limelight and Butine — Java port

This folder brings the Limelight and Butine functionality from `StudioProjects/butine test` (formerly Ma Douce) into F.R.O.G. It is self-contained; no competition autonomous routine has been connected to it.

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

Pickups retain the old drive-over behavior: the robot faces along the pickup leg and drives forward over the pollen. The return leg rotates to the scoring pose's heading. Collection ends at that pose; the calling autonomous decides when to shoot using F.R.O.G.'s current Shooter and Intake logic. The source robot's turret, transfer and shooting protocols are not assumptions about F.R.O.G.'s hardware.

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
params.capacity = 5;              // Actual robot capacity; 0 means one unbounded tour.
params.lookaheadRuns = 2;         // Future runs shape the first run.
params.remainingS = 25.0;         // Caller supplies remaining match time.
params.scoreOverheadS = 2.0;      // Estimated caller-controlled scoring time.
VisionPose depot = new VisionPose(91.5, 81.0, Math.toRadians(270));
Butine.Plan plan = Butine.solve(map.snapshot(), here, depot, params);
```

Only execute the first fitting run, then solve again against the updated map. Starting from `here` avoids predicting or building the first leg from a depot the robot has not reached. The defaults favor fast execution: a 15 ms improvement budget, capped iterations and local-search passes, and an immediately available bounded initial plan. Those settings can be raised if testing shows a useful tradeoff. Search uses the source's acceleration/braking and heading-dependent time model, with small-run exact ordering, larger-run local improvement, capacity splitting, skipped candidates and bounded restarts. Its answer is approximate and its model needs chassis calibration.

The solve budget bounds improvement work; JVM warmup, initial construction and scheduling can add elapsed time. `solveUs`, `iterations` and `budgetExhausted` expose what actually happened. Robot measurements, rather than desktop timings, should determine the final budget.

## Later integration and checking

Use the runner in the same loop as camera/map and follower updates. It starts one run per call and returns control between ticks. Its drive adapter uses the existing Pedro 3 API. An unsuccessful pickup is excluded from replanning and reported; it is not counted as collected. The runner includes follower arming and per-leg timeout handling so an unaccepted path cannot be mistaken for immediate success.

```java
PedroButineDrive drive = new PedroButineDrive(follower);
ButineRunner collection = new ButineRunner(map, drive);
collection.run_butine(depot, params, 0.5); // One run, using half the nominal cruise speed.

// In each existing loop, alongside the camera/map updates above:
follower.update();
collection.update();

// When collection is finished, the caller decides whether to score or start another run.
// Call collection.stop() when the OpMode stops.
```

Failed targets remain in the map but are excluded by ID and location for this runner instance. Use `clear_unreachable()` to allow retrying them after clearing an obstruction or correcting localization. `stats()`, `last_plan()`, `legs()` and `last_report()` provide the diagnostics from the original runner without a separate control framework. A `LimelightRunner(camera, map, drive)` can perform camera/map updates and `relocalize()` between runs; call its `update()` every loop until `is_busy()` is false before starting collection again.

Check the return value of `run_butine()`. `false` can mean the drive is already occupied or there is no fitting collection run; consult `state()` and `last_report()`. An empty plan does not drive the robot to the depot. Successful return checks both position (within 6 inches) and heading (within 8 degrees); tune these tolerances against the scoring setup before integration. The speed scale reduces the Pedro Foresight velocity constraint; it is not a raw motor-power setting.

The three bench OpModes are disabled initially. Enable the one being tested deliberately after checking mounting, pipeline configuration, drive power and field coordinates. No competition autonomous, `Robot`, Shooter or Intake source was modified by this port.

The regression checks under `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/limelight` exercise map replacement, projection, planner constraints and runner state transitions without commanding robot hardware. The project should also compile with `gradlew.bat :TeamCode:compileDebugJavaWithJavac` before deployment.

The FTC SDK calls were checked against the installed 12.0.0 classes and [Limelight's FTC Java programming guide](https://docs.limelightvision.io/docs/docs-limelight/apis/ftc-programming), including asynchronous pipeline switching, result freshness, angular detector coordinates, camera initialization and snapshot capture.

Validation on 10 October 2026: the entire Java package and its bench OpModes compiled against the installed FTC 12.0.0 and Pedro 3.0.1 libraries with Java 8 source/target compatibility. The expanded audit passed 6,209 checks, including independent geometry calculations, 100 exhaustive small-field planner oracles and camera/runner fault traces. See [the bug audit](docs/BUG-AUDIT-2026-10-10.md) for corrected defects, calculated outputs, reproducible commands and coverage limits. The main checkout's offline Gradle build, including a no-daemon retry, could not start because Gradle reported `Unable to establish loopback connection`. No on-robot validation was performed.
