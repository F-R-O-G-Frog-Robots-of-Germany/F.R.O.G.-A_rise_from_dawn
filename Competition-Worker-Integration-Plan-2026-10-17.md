# Competition worker integration plan

Date: 17 October 2026. Status: **software implementation completed; physical validation deferred** because the robot has not been built. The sections below preserve the approved plan.

Implemented through `CompetitionWorkers`, both competition bases, and Main's manual-override arbitration. Vision is optional and enabled through `CompConfig.VISION_ENABLED`. Static autonomous routes and ordinary manual TeleOp do not start an unused planner; an explicit `queue_collection(...)` action or TeleOp `planning_enabled()` override enables it. `configure_workers(...)` provides the pre-start calibration/core-selection hook. No driver button or existing collection route was invented. See the Limelight README for the resulting API and behavior.

Software verification: all 64 TeamCode production sources and 21 standalone checks compiled against the real Android/FTC/Pedro/Panels signatures. The standalone programs passed 14,014 checks; separate camera fixtures passed another 269, including 88 competition-worker checks. The full Gradle APK build remains blocked before compilation by `Unable to establish loopback connection`. No APK was deployed or physical test performed.

The implementation also keeps a competition ownership lease until previous worker threads have actually ended, initializes SDK camera polling before binding the vision owner, and invalidates old map evidence after odometry resets or accepted pose corrections.

## Purpose and preserved design

Connect the existing vision and planner workers to competition OpModes where their workloads are actually needed. Keep gamepad handling, localization, Pedro, shooter, intake, actuator commands and OpMode decisions together in one fast control loop. Camera I/O belongs to the vision worker; route computation belongs to the planner worker. Starting a worker must not automatically start collection, move the robot or request AprilTag relocalization.

Use simple per-OpMode ownership and existing worker/runner interfaces. This plan introduces neither a per-process frequency scheduler nor a planner computation deadline. Pedro remains the drive implementation. See the [assessment](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Multithreading-Core-Pinning-and-Ma-Douce-Assessment-2026-10-17.md) and separate [control-thread affinity plan](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Control-Thread-Affinity-Plan-2026-10-17.md).

## 1. Make optional ownership explicit

Add explicit competition configuration or small protected hooks selecting whether an OpMode uses vision and whether it needs dynamic collection planning. A static autonomous route need not start a planner; a TeleOp using only manual drive need not start either worker. Configure the actual required workloads, rather than starting unused threads merely to demonstrate multithreading.

Place ownership in the existing competition lifecycle, sharing only a small helper if that prevents duplicated startup/cleanup. Construct the camera, calibrated map, Pedro adapter and required workers during INIT. Keep one camera owner and one planner owner per OpMode. Configure camera, map and worker settings before launch; do not change their public configuration concurrently.

The principal seams are [AbstractTeleOp.runOpMode](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:49), [General.runOpMode inside AbstractAuto](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:47), and [CompConfig](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:4).

## 2. Centralize INIT startup

Initialize the map directly with `map.init()` before starting its vision worker exactly once. Construct the asynchronous `LimelightRunner` around that worker, but do not additionally call its current `init()`: [that method starts the worker itself](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightRunner.java:48). Centralized startup must have one clear owner rather than two competing start paths.

Start the optional planner once and pass it to the [asynchronous ButineRunner constructor](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineRunner.java:75). Inspect vision snapshots for initialization outcome: a successful `start()` means thread launch was accepted, not that camera initialization succeeded. INIT must still respond to STOP. Publish an INIT pose only after an actual localization update; repeatedly stamping an old pose as fresh would manufacture misleading evidence.

Launch required workers before any optional control-thread pinning, following the separate affinity plan.

## 3. Service workers through the existing control loop

In [TeleOp's loop](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:90), retain bulk-cache clearing, controls and Pedro update order. After fresh localization, service the asynchronous vision runner and collection runner before the rest of the iteration consumes the resulting pose. Refresh `currentPose` after an accepted correction, keeping pose validation and shooter targeting consistent.

Use one normal pose-publication source per iteration. [LimelightRunner.update already submits the drive pose](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightRunner.java:127); do not add another unconditional `submit_pose()` beside it. Its explicit corrected-pose submission remains necessary when relocalization changes the origin. Invalid localization must invalidate vision mapping rather than leave the last valid pose appearing current.

For autonomous, put the same service point in [update_follower_and_telemetry](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:205). This reaches every `run_until()` iteration, including shot and path waits. Keep mechanism updates running every important loop iteration; never wait for a new camera frame or planner publication.

## 4. Define drive arbitration before enabling assistance

An explicit command may request collection; worker startup alone cannot. In TeleOp, [Main.before_drive_update currently issues manual drive](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:118). While collection owns drive, suppress that competing command. Manual override must cancel collection before resuming manual control. Do not assign a new button or enable assistance implicitly through this infrastructure change.

Autonomous collection should become an explicit queued action serviced by the shared loop. Static path actions and collection cannot own Pedro simultaneously. Intake and scoring remain competition-loop decisions. Relocalization remains an explicit stationary operation with its existing motion checks.

The [existing asynchronous planning transition stops drive](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/ButineRunner.java:130). Preserve that stationary-start contract: `PLANNING` polls completion while controls, shooter, intake and the OpMode continue ticking. Do not describe it as unrestricted planning while driving.

## 5. Preserve freshness and failure behavior

Use copied planner inputs, request identities, current target checks and existing result acceptance checks. Existing match-time feasibility checks describe whether a route still fits; they are not a new solver execution budget. Superseded, stale, failed or invalid results must never dispatch motion. Missing vision or planner support should leave manual TeleOp or the selected static autonomous fallback usable, with a concise diagnostic. Never fall back to synchronous solving or camera SDK/HTTP work inside the active control loop.

The interfaces have short synchronized sections, so avoid claiming they are wait-free. Keep solver work and camera I/O outside loop-shared locks. `Butine.Plan.runs` and `Butine.Run.ids/poses` are public arrays despite final references: define a read-only consumer contract, or encapsulate/copy them where needed. The worker does not mutate its published plan, but the public API does not enforce deep immutability.

## 6. Stop independently and validate

Use cleanup that survives earlier cleanup failures: invalidate requests and request worker stops, stop hardware outputs, then perform bounded worker joins. Motor stopping must not wait for camera shutdown. Preserve original worker ownership if SDK I/O or a solve outlasts the bounded join; do not start a replacement over it. Apply affinity restoration in its own final cleanup path.

The implemented software checks cover optional startup, exactly-once ownership, INIT failure/STOP, invalid/stale publications, manual override, autonomous arbitration, continued mechanism ticks during `PLANNING`, and cleanup after exceptions, alongside the existing worker and async-runner checks. Physical frame freshness, pickup success, loop latency and sustained-load behavior await the built robot; no measured robot-performance gain or physical collection success is claimed.

Written by Codex (GPT-6), I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.
