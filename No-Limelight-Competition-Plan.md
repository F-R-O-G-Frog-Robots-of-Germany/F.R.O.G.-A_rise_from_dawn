# Competition plan without Limelight, Butine, or AprilTag corrections

Goal: prepare a reversible competition configuration that runs existing autonomous paths and TeleOp using Pinpoint odometry, without requiring a Limelight camera.

## Status and assumptions

This is an implementation plan only, prepared on 2026-11-11. No Java, Gradle, hardware configuration, control binding, or camera connection was changed while writing it. Every unchecked item describes future work or verification. Your upcoming patches take precedence over this snapshot; repeat the dependency inspection after those patches land.

I interpret “disconnect Butine” as preventing its competition activation and its vision dependencies while preserving its implementation for later use. Physical unplugging, deleting packages, and removing SDK dependencies are unnecessary for that objective. The planned Python importer and alliance-partner avoidance are future integration work, not requirements for getting this camera-free competition setup running.

The companion [Robot setup, tuning, and constants guide](Robot-Bringup-Tuning-and-Constants.md) covers the basic robot, Pedro, mechanisms, and the constants inventory. Keep exactly these two new documents; the constants reference is part of that second file.

## What the current repository actually does

Paths below are relative to `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`. Line numbers refer to this snapshot; search the symbol after patches shift a line.

| Area | Current source and line | Consequence |
| --- | --- | --- |
| Competition localization | `core/pedro/Constants.java:87`, especially construction at 89 | The follower uses `PinpointLocalizer`, `Mecanum`, and `Foresight`. AprilTags are not its required pose source. |
| Shared TeleOp lifecycle | `opmodes/teleop/AbstractTeleOp.java:73`, hook at 157 | Calls a video hook whose implementation is empty. It does not currently create a Limelight. |
| Competition button | `opmodes/teleop/comp/Main.java:57` | The right-bumper vision/Butine binding is only a comment. |
| Shared robot | `Robot.java:32` | Initializes intake, shooter, and flower intake. No vision worker or Butine runner is initialized here. |
| Autonomous base | `opmodes/auto/structure/AbstractAuto.java:53` | Builds the Pinpoint follower and mechanisms. Camera confirmation is future work, mentioned in comments. |
| Competition display flags | `core/control/CompConfig.java:9` | `COMP_MODE=true` suppresses Panels/video/ordinary diagnostics through helper methods. This is not a general vision/Butine feature gate. |
| Vision implementation | `limelight/` | Camera, vision worker, pollen map, planner, planner worker, and drive adapter remain available for later integration. |
| Camera/planner experiments | `limelight/opmodes/` | Separate selectable test OpModes can still initialize vision or exercise Butine. Competition isolation includes deciding how to present these. |

**Expected implementation size:** if upcoming patches leave this structure intact, the competition path may already need no disconnection code. Prove it with camera-absent initialization and route tests before adding switches. Missing competition integration is not the same as deleting a working feature.

## Intended competition behavior

- Init and Start succeed with no camera installed or configured.
- Pinpoint supplies pose throughout autonomous and TeleOp; no AprilTag correction writes to that pose.
- No competition action starts a Limelight poller, pollen scan, Butine solve, or Butine path execution.
- Existing fixed autonomous paths, field-centric driving, mechanisms, shooter selection, and pose-based hive aiming remain available after their normal tuning and validation.
- Drivers get a clear operating procedure: Pinpoint only, correct starting placement, no automatic tag recovery, no automated pollen collection.
- Stop cancels motion and mechanism activity through the existing lifecycle.
- Vision code, calibration work, and future importer development remain recoverable without a rewrite.

Pose-based aiming and distance-based shooter targeting do not inherently require AprilTags. They depend on trustworthy Pinpoint pose and calibrated field geometry. Retain them only to the extent they pass physical validation; fixed RPM/hood selection offers a simpler initial shot setup.

## Phase 1 — inspect the final patched source

- [ ] Save the revision identifier and a clean list of pre-existing local changes before implementation.
- [ ] Inspect `Robot`, `Main`, `AbstractTeleOp`, `AbstractAuto`, and `Constants.create_follower()` after your patches.
- [ ] Search competition and shared lifecycle code for camera hardware lookup, camera start/stop, `LimelightRunner`, `LimelightWorker`, `ButineRunner`, `ButineWorker`, `PollenMap`, and `run_butine`.
- [ ] Inspect all follower pose-write sites. Classify starting-pose initialization, deliberate driver reset, stored pose handoff, and camera correction separately.
- [ ] Trace every control binding and periodic callback that reaches the vision package; comments and imports alone do not activate anything.
- [ ] Inspect worker creation and shutdown. A disabled button is insufficient if Init still starts the worker.
- [ ] List every competition OpMode intended for selection and each camera-specific test OpMode to avoid.

Useful read-only searches from the repository root:

```powershell
rg -n -i 'limelight|apriltag|butine|pollenmap|visionpose' TeamCode/src/main/java/org/firstinspires/ftc/teamcode
rg -n 'setPose|submit_pose|run_butine|new Limelight|new Butine|\.start\(' TeamCode/src/main/java/org/firstinspires/ftc/teamcode
rg -n 'create_follower|add_.*listener|PeriodicRegistry|TaskManager' TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes
```

Acceptance: a documented call path from each selected competition OpMode to its required hardware, with no camera requirement and no planner execution.

## Phase 2 — choose the smallest reversible isolation

### Case A: competition remains unconnected to vision

Leave the current commented integration and empty hook alone. Do not introduce flags merely to disable code that is never reached. Record that camera-free operation is the current design and proceed to validation. Leave the vision implementation intact.

### Case B: your patches connect vision before the competition build

Add one explicit competition capability setting at the shared configuration boundary, with camera-free behavior as the competition default. Gate construction and worker startup, button activation, and AprilTag correction application. Do not equate “video off” with “vision off”: streaming, tag localization, detection, and autonomous collection are separate capabilities.

Illustrative future structure only; these names and calls are not existing APIs:

```java
// Pseudocode: apply only if later patches introduce these integrations.
if (cameraCapabilityEnabled) {
    // Construct the camera service and start its worker during Init.
}
if (tagCorrectionsEnabled && freshAcceptedFixAvailable) {
    // Apply the existing validated correction mechanism.
}
if (butineCapabilityEnabled && collectionRequested) {
    // Delegate to the existing runner only with valid pose/map inputs.
}
```

When the camera capability is false, dependent tag and detection capabilities must be false. Butine must not consume a previous camera session's map or a default synthetic target list. If a disabled command remains visible, report “unavailable in this competition setup” and issue no motion. Prefer an inert binding or no binding over silently substituting an unvalidated route.

If a future runtime switch can change while a runner is active, cancellation must stop that runner's drive command, invalidate pending worker results, and restore manual ownership through the runner's supported lifecycle. A compile-time competition setting evaluated before construction avoids that additional transition for this event.

Acceptance: no camera hardware lookup, network wait, detector work, solver work, or correction write occurs in the disabled competition path.

## Phase 3 — preserve basic autonomous and driver operation

- [ ] Keep `Constants.create_follower()` on Pinpoint; measure its hardware settings using the companion guide.
- [ ] Select a small set of existing routes that can be completed from known starts using fixed geometry and the existing mechanism sensors.
- [ ] Validate pickups using intake distance sensors and physical observation. Zero distance thresholds are active placeholders and must be measured.
- [ ] Verify field dimensions, robot clearance, start placement, and BLUE mirroring on the physical field.
- [ ] Validate shooter/hood calibration independently of camera availability. Start from a measured fixed shot if the distance model is not ready.
- [ ] Verify Auto-to-TeleOp pose handoff without moving the robot between modes. Stored pose has a time limit and can be invalidated.
- [ ] Practice the explicit TeleOp reset procedure only at its matching known position and orientation. A reset does not measure the robot's actual location.
- [ ] Keep driver-triggered hive aiming only after verifying Pinpoint heading, target coordinates, alliance selection, and stick cancellation.
- [ ] Agree with the alliance partner on routes, occupied areas, and timing. Existing fixed paths do not become collision-aware because Butine is unavailable.

Do not compensate for odometry scale or heading errors by editing route coordinates. Do not remove timeout protections to make a failing path look complete.

## Phase 4 — handle experimental OpModes deliberately

The Limelight position test, neural-network trainer, CPU-thread test, and Butine planner test are development tools. Decide during the future competition preparation whether to mark camera/planner experiments disabled in the selectable competition build or simply provide an explicit Driver Station selection checklist. Any annotation changes belong to that later implementation, not this documentation task.

Keep Pedro AutoTune and the shooter tuner available during preparation. They are needed to calibrate the basic robot and should not be treated as camera-dependent tools.

## Phase 5 — validate the actual camera-free build

| Check | Procedure | Required result |
| --- | --- | --- |
| Build | Compile the final code after your patches and any later isolation changes | No removed imports, missing types, or broken lifecycle references. |
| Camera absent | Use a robot configuration with no camera and initialize every selected competition mode | No missing-camera exception, retry loop, or delayed Init. |
| Stationary pose | Leave robot still, then hand-push known translations and rotations | Pinpoint pose follows the physical test; no tag correction jumps. |
| TeleOp | Exercise drive, mechanisms, fixed/model selection, alliance selection, and optional aiming | Predictable controls with no vision waits. |
| Reserved control | Press the right bumper previously reserved for Limelight | No camera request and no automated motion. |
| Auto | Run chosen routes from marked starts on both alliances | Repeated physical success with enough route clearance. |
| Handoff | Finish Auto, start TeleOp promptly without relocating robot | Stored pose and alliance match the finish; stale/fallback case is understood. |
| Stop | Stop during a path, during mechanism activity, and before Start | Drive and mechanism outputs stop via the existing shutdown path. |
| Diagnostics | Inspect logs/threads if future integration added workers | No camera or Butine worker running in competition mode. |

Hardware observations are required; desktop compilation cannot establish pose accuracy, servo clearance, shot reliability, or collision clearance.

## Phase 6 — reconnect after the event

1. Recheck the merged hardware and pose interfaces after your patches.
2. Commission the physical camera and validate AprilTag corrections separately from pollen detection.
3. Validate pollen mapping without automated driving.
4. Integrate the Python importer's exported data through a documented input boundary. Its format and transport are not yet specified; do not assume an on-robot Python runtime or a live partner feed.
5. Define coordinates, units, alliance transforms, robot footprints, clearance margin, and timestamps/timebase for partner information.
6. Define whether imported information describes a static exclusion zone, a timed route, or live partner state. Static path import alone cannot guarantee separation from a moving partner.
7. Apply clearance checks to complete driven legs, including the return to the depot, not just target points. Include the robot's swept footprint and any curved path used by the drive adapter.
8. Define behavior for stale, missing, invalid, or incompatible data before enabling avoidance. Do not silently treat missing partner data as proof of clear space.
9. Version planner inputs and invalidate in-flight results when obstacle/partner information changes.
10. Test avoidance offline, then on an empty field at reduced speed, then with a coordinated partner. Enable Butine only after those tests pass.

This preserves a place for your importer work without prematurely designing or implementing it.

## Completion checklist for the later implementation

- [ ] Final patched competition call paths reviewed.
- [ ] Camera-free configuration tested with no physical camera.
- [ ] Pinpoint and basic mechanisms calibrated.
- [ ] Selected autonomous routes repeated on both alliances.
- [ ] Driver selection/reset procedure recorded.
- [ ] Vision and Butine implementation retained for later reconnection.
- [ ] Remaining hardware limitations recorded honestly.

## Changes you would like made

- Preferred competition autonomous routes:
- Controls to reserve for later integration:
- Upcoming patch dependencies:
- Python importer format/transport once decided:
- Additional competition observations:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
