# Creating an autonomous from Pedro Pathing Visualizer Java

This guide describes this repository's autonomous system, using the dependencies in `build.dependencies.gradle`: Pedro `core` and `revhub` 3.0.1. The input can be just the Java exported by the Visualizer; a `.pp` file or screenshot is optional.

The export supplies path geometry and heading interpolation. It may not describe which path collects pollen, which endpoint shoots, or when a mechanism should move. Use meaningful exported names to identify those actions. If the names are only `path1`, `path2`, etc., decide their actions before connecting the mechanisms. Coordinates alone cannot establish that a pickup succeeded or that a hive tilted.

## Where the code goes

All Java paths below are relative to `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.

| File | Responsibility |
| --- | --- |
| `opmodes/auto/structure/OptimalPoses.java` | Ideal RED start, pickup, endpoint and control-point geometry. Add a nested route class here. |
| `opmodes/auto/comp/YourAuto.java` | Registered autonomous, per-point field corrections, path factories and action order. |
| `opmodes/auto/structure/AbstractAuto.java` | Shared follower/hardware initialization, action helpers, shooting path helpers, stopping and TeleOp pose handoff. |
| `core/pedro/PoseMirroring.java` | BLUE transformation of RED geometry. |
| `core/control/LastPositionStorage.java` | Last measured field pose, alliance and expiration time. |

The original modes are `BothFlowersNoGarden` and `SoloWithSteal`; seven imported routes are listed in `Visualizer-Autonomous-Routes.md`. For a new route, extend `AbstractAuto`, implement `starting_pose()` and `build_autonomous()`, and give the class an `@Autonomous` annotation. Do not replace the base class's final `runOpMode()` with the Visualizer's exported lifecycle.

Path factories can be private methods in the new autonomous, as in the example below. Existing modes put their pickup/park factories in nested classes inside `AbstractAuto`. Both arrangements work; adding a new route does not require changing the shared lifecycle.

## 1. Read the export into a route plan

For each exported path, record its start, controls in order, endpoint, heading mode, and robot action. Preserve the order of all Bézier controls: swapping controls changes the curve. A control point shapes the curve; the robot does not necessarily drive through it.

For example, a route using both flowers can have these actions:

| Step | Motion | Action |
| --- | --- | --- |
| 1 | Start at lower shooting pose | Shoot the preload. |
| 2 | Lower hive to far flower | Collect with the flower intake. |
| 3 | Far flower to upper hive | Shoot the first upper load. |
| 4 | Upper hive to mid flower | Collect with the flower intake. |
| 5 | Mid flower to upper hive | Shoot the second upper load. |
| 6 | Upper hive to park | Drive without collecting or shooting. |

The team's current scoring assumption is that the starting hive is half filled. The preload completes that hive's first tilt attempt. After switching hives, send **at least two loads to the same hive before switching again**. A third load is also allowed for redundancy; exactly two is not a limit:

- `BothFlowersNoGarden`: lower preload → upper → upper.
- `SoloWithSteal`: lower preload → upper → upper → lower → lower.
- `FarMidGardenPark`: lower preload → upper → upper → upper (the third load adds redundancy).

Choose the shooting destination from this scoring sequence. A nearby pickup's Y coordinate does **not** override the sequence. The shared RED shooting poses are lower `(59.25, 9, 90°)` and upper `(59.25, 135, -90°)`. Nearby exported shooting endpoints, such as Y = 8 or Y = 133, can use the corresponding shared pose. Keep actual flower pickups separate even when they are close to a hive.

## 2. Normalize coordinates and heading units

Ideal route coordinates in this repository are RED field coordinates, in inches. Store headings through the existing `DEGREES` factory in `OptimalPoses`:

```java
public static final Pose PICK_UP = DEGREES.of(48, 132, 90.9767);
```

`DEGREES.of(...)` accepts degrees. `new Pose(...)`, `Pose.heading()` and numeric heading arguments to Pedro path interpolation use radians. For an export containing `new Pose(x, y, Math.toRadians(90))`, the ideal declaration becomes `DEGREES.of(x, y, 90)`. For an export with a numeric radian heading, convert that value to degrees before storing it through `DEGREES`.

If the supplied export was drawn for BLUE, normalize its positions **and headings** back to RED first. Do not put BLUE coordinates into `OptimalPoses` and mirror them a second time. The repository's BLUE transform mirrors around the field center `(72, 72)` inches: X becomes `144 - X`, Y becomes `144 - Y`, and heading rotates by 180 degrees through `PoseFactory.mirrorAroundPoint`. Use `PoseMirroring.mirror_across_field_center` instead of hand-writing coordinate or heading arithmetic.

Retain the repository's coordinate convention when importing exports. If an export uses a different field origin or scale, reconcile it before copying points; do not silently mix coordinate systems.

## 3. Put ideal poses in `OptimalPoses`

Insert this nested class inside `OptimalPoses`, alongside its existing route classes. These example points use the current both-flower route; substitute your exported points for a new route.

```java
public static final class VisualizerRoute {
    private VisualizerRoute() {
    }

    public static final Pose FAR_PICK_UP = DEGREES.of(48, 132, 90.9767);
    public static final Pose FAR_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 25, 0);
    public static final Pose FAR_PICK_UP_CONTROL_2 = DEGREES.of(85, 70, 0);
    public static final Pose FAR_PICK_UP_CONTROL_3 = DEGREES.of(48, 100, 0);
    public static final Pose FAR_SHOOT_CONTROL_1 = DEGREES.of(47, 115, 0);
    public static final Pose FAR_SHOOT_CONTROL_2 = DEGREES.of(59, 115, 0);

    public static final Pose MID_PICK_UP = DEGREES.of(8, 47, -179.0231);
    public static final Pose MID_PICK_UP_CONTROL_1 = DEGREES.of(34, 113, 0);
    public static final Pose MID_PICK_UP_CONTROL_2 = DEGREES.of(47, 47, 0);
    public static final Pose MID_SHOOT_CONTROL_1 = DEGREES.of(47, 47, 0);
    public static final Pose MID_SHOOT_CONTROL_2 = DEGREES.of(34, 113, 0);

    public static final Pose PARK = DEGREES.of(8, 120, -174.3889);
    public static final Pose PARK_CONTROL = DEGREES.of(59.25, 125, 0);
}
```

Reuse `OptimalPoses.SHOOT_IN_LOWER_HIVE` and `OptimalPoses.SHOOT_IN_UPPER_HIVE`; avoid creating separate shooting coordinates for every load. If the new autonomous starts somewhere else, add its own ideal `START` pose.

## 4. Apply corrections, then mirror exactly once

Each autonomous declares its corrected RED poses like this:

```java
private final Pose FAR_PICK_UP = hotfix(0, 0, 0)
        .plus(OptimalPoses.VisualizerRoute.FAR_PICK_UP);
```

`hotfix(dx, dy, ddeg)` adds inches in X/Y and degrees in heading. For example, `hotfix(1, -2, 3)` moves the ideal pose by +1 inch X, -2 inches Y and +3 degrees. The correction comes **before** BLUE mirroring. `OptimalPoses.FIELD_FIX = false` disables all those corrections for comparison with the ideal geometry.

Give every control point its own explicit correction, including zero corrections. Reuse the same corrected endpoint object as the next path's start. If two separate declarations represent the same endpoint, correcting only one can introduce a gap between paths.

Use `field_pose(...)` on the start pose returned to Pedro and on every path point. For linear heading interpolation, use the transformed start/end poses too:

```java
Pose start = field_pose(START);
Pose end = field_pose(FAR_PICK_UP);
return Paths.curve(start, field_pose(CONTROL), end).linear(start, end);
```

Do not call `field_pose` on `follower.pose()` or on a restored pose from `LastPositionStorage`: those are already field coordinates.

## 5. Translate exported path construction

Use the imports `com.pedropathing.api.Paths`, `com.pedropathing.math.Pose` and `com.pedropathing.paths.Path`.

| Exported geometry or behavior | Repository implementation |
| --- | --- |
| Straight segment / legacy `BezierLine` | `Paths.line(fieldStart, fieldEnd)` |
| Bézier curve / legacy `BezierCurve` | `Paths.curve(fieldStart, fieldControl1, ..., fieldEnd)` |
| Several segments in one path | `Paths.path(segment1, segment2, ...)` |
| Linear heading | `.linear(fieldStart, fieldEnd)` |
| Constant heading | `.constant(fieldPoseWithDesiredHeading)` |
| Tangent heading | `.tangent()` |
| Reverse tangent heading | `.reverseTangent()` |

An older export may use `PathChain`, `follower.pathBuilder()`, `followPath(...)`, `Point`, or `setLinearHeadingInterpolation(...)`. Translate its geometry and interpolation to the API above. This repository's Pedro 3 follower uses `follower.follow(Path)` through the shared helpers.

Preserve the exported heading behavior for pickup/drive paths unless you deliberately change it. Tangent heading follows the curve and ignores the endpoint pose's stored heading. Shooting paths should finish aimed at the hive; the shared `shoot_in_upper_hive(...)` and `shoot_in_lower_hive(...)` helpers use linear interpolation to the supplied endpoint heading.

The upper shooting helper currently accepts two or three controls; the lower helper accepts one or two controls. For another number of controls, add an overload or a private path factory using the same transformed-point and linear-heading pattern. The endpoint argument must be the hive named by the helper.

When preserving a numeric constant or linear heading from an export, first represent it as a RED pose heading, then pass the transformed pose to interpolation. Passing an unmirrored numeric angle on BLUE can aim the robot the wrong way. Preserve exported constraints, callbacks and pauses deliberately; geometry conversion alone does not reproduce them. Do not attach `PathConstraints.autoPilot` automatically: it contains separate provisional values and is not used by the existing competition autos.

## 6. Connect the action helpers

Call these helpers from `build_autonomous()`. They enqueue actions and construct
their paths during INIT; they do not move motors then. The base class executes
the queued sequence after START. Motor writes, flower timing, shooter readiness
and duration checks all run on the OpMode thread. A path or shooter wait timeout
aborts the sequence and uses ordinary STOP cleanup.

The original route class selects RED. Add a public annotated BLUE subclass that
overrides `autonomous_alliance()` to return `Alliance.BLUE`, as in the existing
`BlueBothFlowersNoGarden`. Keep geometry in the RED parent; the shared helpers
mirror it once. The Driver Hub selects the alliance through the named OpMode.

| Call | What it does |
| --- | --- |
| `queue_path(path)` | Drives until Pedro reports completion; does not start intake or shooting. |
| `queue_intake_path(path)` | Runs the intake for the drive, then turns it off. Does not lower the flower intake. |
| `queue_intake_path(path, lowerAfterSeconds)` | Runs intake throughout; lowers the flower intake after that delay. Raises it one second after lowering, or sooner when the path finishes/stops. |
| `queue_intake_path(path, estimatedPathSeconds, intakeLastSeconds)` | Delays intake until the last part of an estimated drive time. This overload does not lower the flower intake. |
| `queue_shoot_path(path, shotSeconds)` | Drives to completion, waits for flywheel speed, then feeds pollen for that duration. |
| `queue_shot(shotSeconds)` | Waits for flywheel speed and feeds pollen without driving a new path. Useful for preload. |

All durations above are seconds. For the two-argument intake overload, `0.0` means lower the flower intake at path start; it does **not** mean zero intake time. The three-argument overload uses an estimate, not the Visualizer's playback time as a guaranteed measurement. A delayed intake start is cancelled if the path ends first.

The shared runner keeps updating the follower, flywheel target and enabled telemetry during waits. It updates a newly started action before testing completion. Drive helpers wait for `!follower.isBusy()` rather than just the parametric end. This permits Pedro's configured endpoint constraints to settle; it does not override Pedro's endpoint timeout.

There is no general pause helper. Do not use a long `sleep(...)` in a route while assuming the follower still updates. If an exported pause must hold position, implement an active, stop-aware timed helper that keeps updating the follower, or connect it to an existing action such as the shooting helper.

## 7. Complete autonomous example

Save this as `opmodes/auto/comp/VisualizerAuto.java` after adding `VisualizerRoute` above. It demonstrates the geometry/correction/action split and uses the current preload → upper → upper sequence. Its timing values require robot tuning; the example is not a new verified competition route.

```java
package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.pedropathing.api.Paths;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;

import static org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "VisualizerAuto", group = "Autonomous")
public class VisualizerAuto extends AbstractAuto {
    private static final double SHOT_SECONDS = 1.25;
    private static final double LOWER_FLOWER_AFTER_SECONDS = 1.0;

    private final Pose LOWER = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_LOWER_HIVE);
    private final Pose UPPER = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_UPPER_HIVE);
    private final Pose START = LOWER;

    private final Pose FAR = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_PICK_UP);
    private final Pose FAR_C1 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_PICK_UP_CONTROL_1);
    private final Pose FAR_C2 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_PICK_UP_CONTROL_2);
    private final Pose FAR_C3 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_PICK_UP_CONTROL_3);
    private final Pose FAR_SHOOT_C1 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_SHOOT_CONTROL_1);
    private final Pose FAR_SHOOT_C2 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.FAR_SHOOT_CONTROL_2);

    private final Pose MID = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.MID_PICK_UP);
    private final Pose MID_C1 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.MID_PICK_UP_CONTROL_1);
    private final Pose MID_C2 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.MID_PICK_UP_CONTROL_2);
    private final Pose MID_SHOOT_C1 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.MID_SHOOT_CONTROL_1);
    private final Pose MID_SHOOT_C2 = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.MID_SHOOT_CONTROL_2);

    private final Pose PARK = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.PARK);
    private final Pose PARK_C = hotfix(0, 0, 0).plus(OptimalPoses.VisualizerRoute.PARK_CONTROL);

    @Override
    protected Pose starting_pose() {
        return field_pose(START);
    }

    private Path far_pick_up() {
        return Paths.curve(field_pose(START), field_pose(FAR_C1), field_pose(FAR_C2),
                field_pose(FAR_C3), field_pose(FAR)).tangent();
    }

    private Path mid_pick_up() {
        return Paths.curve(field_pose(UPPER), field_pose(MID_C1),
                field_pose(MID_C2), field_pose(MID)).tangent();
    }

    private Path park() {
        return Paths.curve(field_pose(UPPER), field_pose(PARK_C), field_pose(PARK)).tangent();
    }

    @Override
    protected void build_autonomous() {
        // The half-filled starting lower hive tilts with the preload.
        queue_shot(SHOT_SECONDS);

        queue_intake_path(far_pick_up(), LOWER_FLOWER_AFTER_SECONDS);
        queue_shoot_path(shoot_in_upper_hive(FAR, FAR_SHOOT_C1, FAR_SHOOT_C2, UPPER),
                SHOT_SECONDS);

        queue_intake_path(mid_pick_up(), LOWER_FLOWER_AFTER_SECONDS);
        queue_shoot_path(shoot_in_upper_hive(MID, MID_SHOOT_C1, MID_SHOOT_C2, UPPER),
                SHOT_SECONDS);

        queue_path(park());
    }
}
```

Keep exported path factories as methods so paths are built with corrected and alliance-transformed poses. Do not copy an additional follower, hardware initialization, scheduler loop, or final-pose save into this class; `AbstractAuto` already supplies them.

## 8. Alliance, stopping and TeleOp handoff

The base autonomous reads the alliance from `LastPositionStorage.get_current_alliance()`. Its initial default is BLUE. There is currently no autonomous init-menu alliance selector. Before a run, ensure the stored alliance is correct; extending `AbstractAuto` does not automatically infer alliance from the route name or field side.

At completion or an early stop after initialization, the base stops the follower and drivetrain, refreshes localization, and saves the measured field pose plus alliance. It then cancels background tasks and stops the mechanisms. An error reading the final pose still triggers mechanism/task cleanup. Use the measured pose for handoff, not the planned park endpoint; an interrupted auto may be elsewhere.

TeleOp restores that pose during initialization if it is less than 200 seconds old, with no additional mirroring. Storage is static process memory: restarting the Robot Controller app or reloading code clears it. A cancelled auto before START currently also saves its initialized pose. Do not move the robot between auto and TeleOp without deliberately resetting localization.

## 9. Verify a new route

1. Compile with `./gradlew.bat :TeamCode:compileDebugJavaWithJavac` on Windows, or the IDE's build action. Confirm imports use the current Pedro 3 packages.
2. Check every path start against the previous endpoint, including after changing a shooting destination. The park path must start at the last actual shooting endpoint.
3. Check the full shooting order, including preload. After the half-filled starting hive's preload, deliver at least two loads to a hive before switching; a third load is allowed for redundancy. A timer duration is one delivered-load attempt, not a guaranteed number of pollen pieces or hive tilts.
4. Check RED geometry, then BLUE geometry; confirm all controls and interpolation headings mirror once. Confirm the alliance before START.
5. Enable practice telemetry by setting `CompConfig.COMP_MODE = false`, then inspect X/Y/heading and path progress. Tune field corrections in the autonomous and route geometry in `OptimalPoses`.
6. Measure drive and mechanism timings on the robot. Confirm the flower intake lowers over the flower and rises before departure. The Visualizer cannot establish collection success.
7. Test STOP during a drive and a shot, then initialize TeleOp. Verify motors stop and TeleOp uses the measured final pose/alliance.

Remaining tuning limits: autonomous currently forces shooter bypass on at `Shooter.STANDARD_RPM` (3000 RPM); the distance-to-RPM model and intake loaded thresholds are placeholders. Flywheel speed waits have no independent timeout and continue until the target is reached or the OpMode is stopped. Endpoint tolerances/timeouts come from `Constants.foresightConfig`; a configured timeout can end a path despite residual error. Hardware initialization is before the autonomous action `try/finally`, so initialization failures are a separate lifecycle case that still needs robot testing. Validate geometry, headings, collection, shot duration and total route time on hardware before treating an export as competition-ready.

## Review completed with this guide

The shared runner was corrected to update Pedro before checking a newly started action, and to wait for settled path completion before proceeding. Shutdown now explicitly stops the drivetrain and refreshes the localization sample used for TeleOp. The pose-save operation is enclosed so a localization/save failure cannot skip background-task and mechanism cleanup. The two current routes keep their confirmed shooting orders above.

Validation on 2026-10-06: the four autonomous Java files and the complete example in this guide compiled with cached project dependencies. A desktop check exercised the actual compiled runner with a fake follower and confirmed that stale completion cannot skip a newly started path, consecutive paths wait for settlement, and STOP prevents another path from starting. Additional checks passed for RED/BLUE shooting endpoints and headings, field corrections, measured-pose/alliance storage and its 200-second expiry, and both routes' endpoint continuity and paired shooting order. These checks do not establish physical path clearance, localization accuracy, mechanism reliability, or match timing.

The normal Gradle compile task could not start because Java reported `Unable to establish loopback connection`. The direct Java checks above are a fallback, not a verified full Android build.

## Pedro references

- [Visualizer](https://visualizer.pedropathing.com/)
- [Path creation and method-based path factories](https://pedropathing.com/docs/pathing/reference/api)
- [Heading interpolation](https://pedropathing.com/docs/pathing/reference/interpolation)
- [Follow states and completion checks](https://pedropathing.com/docs/pathing/guide/follow-state)

The Java API was also checked against the project's locally cached Pedro 3.0.1 jar. Prefer the repository's pinned version when an exported snippet or an online example uses a different API.
