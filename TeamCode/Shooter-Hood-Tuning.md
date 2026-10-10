# Shooter and hood tuning

Both outputs use the same horizontal distance from the robot's localization pose to the nearest alliance hive, in inches. Hive height is not included. The selector compares distances to both alliance hives; with the current coordinates the boundary is Y=71.5, with a tie choosing the bottom hive. Use this same distance definition when collecting data.

## Implemented features

- One matched shot solution: distance-to-RPM and distance-to-hood-angle quadratics fitted to the same minimum-RPM shot pairs.
- Hood angles in degrees, converted to servo position with two measured endpoints; reversed servo travel is supported.
- RPM and mechanical angle limits, cached servo commands, and rejection of invalid numerical inputs.
- Fixed RPM and hood angle mode for collecting successful shots.
- Feeding gated by RPM tolerance, a valid hood command, and a settling timer after each actual servo command. There is no angle feedback sensor.
- RPM regulation remains active while shooting.
- Tuner controls for both curves and a session-only sample capture showing distance, measured RPM, and commanded hood angle.

## Active filler settings

`Shooter.java` currently commands 3000 RPM and 45 degrees at every distance. The active hood range is 0–90 degrees mapped to servo positions 0–1; the RPM cap is 6000. These are filler values, not measured robot settings. There is no calibration flag: commands run immediately with the configured constants.

Replace `HOOD_MIN_ANGLE_DEG`, `HOOD_MAX_ANGLE_DEG`, `HOOD_SERVO_AT_MIN_ANGLE`, `HOOD_SERVO_AT_MAX_ANGLE`, `DEFAULT_HOOD_ANGLE_DEG`, and `MAX_RPM` with measured values once the robot is built. `HOOD_SETTLE_TIME_MS` is provisionally 250 ms: measure worst-case full-travel movement and use a suitable value. Endpoint interpolation assumes a linear linkage; check intermediate angles too. Keep a consistent physical hood-angle reference throughout calibration and shot collection.

## Collect data and fit in Desmos

1. Open **Shooter PIDF Tuner** with the robot physically at (9, 9) inches, facing field +X (heading 0 degrees). This pose is always used, even if another OpMode stored a recent pose. Alliance starts from `LastPositionStorage`; gamepad 2 B toggles it during INIT. Verify the displayed alliance before START. The tuner initializes the hood to its default angle during INIT; the flywheel remains off until toggled.
2. Leave gamepad 2 Y's distance-model mode off. Gamepad 1 Start toggles the flywheel; Y/B changes RPM by 100. Gamepad 2 up/down changes the manual hood by one degree. Feed test shots through your chosen manual setup; this tuner does not drive the intake.
3. At each distance, test several hood angles. For each angle, lower RPM until shots stop succeeding reliably, then return to the lowest RPM that repeatedly succeeds. Compare those candidates and retain the lowest reliable RPM together with its matching hood angle. Do not combine the lowest RPM from one trial with the angle from another. Wait for RPM to stabilize before recording. Gamepad 2 A captures the displayed distance, measured RPM, and last applied hood command; copy the sample before capturing another. Capture requires the motor enabled, positive target RPM, measured RPM within 70 RPM of target, valid numerical inputs and hood mapping, and an elapsed settling timer. A rejected capture preserves the previous sample. Captures are not saved to disk. Hood angle is a command, not a sensor measurement.
4. Build a Desmos table with columns `x_1` (distance in inches), `y_1` (lowest reliable measured RPM), and `z_1` (its matching hood angle in degrees). Each row is one matched shot, not two independently chosen outputs.
5. Enter two separate regressions:

   ```text
   y_1 ~ a*x_1^2 + b*x_1 + c
   z_1 ~ p*x_1^2 + q*x_1 + r
   ```

6. Copy `a`, `b`, `c` into `DISTANCE_A`, `DISTANCE_B`, `DISTANCE_C`. Copy `p`, `q`, `r` into `HOOD_DISTANCE_A`, `HOOD_DISTANCE_B`, `HOOD_DISTANCE_C`. Rebuild and deploy.

Collect more than three distinct distances and repeat shots; validate between fitted distances as well. The two regressions describe one distance-dependent pair `(RPM, hood angle)` along your measured lowest-reliable-RPM shots. Regression does not perform an online search or guarantee the mathematical minimum: the experimental angle/RPM search supplies that objective. Retest the fitted pairs, since fit error can make a pair unreliable even when its original table row worked. If you change the shot strategy or refit one curve, validate the pair together.

Use the fitted distance range when shooting: the code clamps outputs but does not restrict extrapolation by distance. Clamping can alter the fitted pair, so collect data within the configured RPM/angle limits and validate any shots near those limits.

## Test fitted curves

In the tuner, gamepad 2 Y toggles both curves together. X selects which curve to edit; left/right selects A/B/C; bumpers adjust the selected coefficient. Telemetry shows both curves, distance, target and measured RPM, commanded hood angle, and servo position. Changes made in the tuner are session-only: copy final coefficients into `Shooter.java`.

Competition INIT selection uses gamepad 2 X for fixed/model mode, bumpers for fixed RPM, and up/down for fixed hood angle. The subsystem also exposes setters and getters for autonomous code and telemetry.

The tuner uses the hub velocity controller; competition uses the existing custom PIDF controller. Tuner PIDF edits do not change competition PIDF gains. Validate actual RPM under the competition controller before trusting the shot fit.

## Where and how the hood runs

- `Shooter.init()` maps the configured servo named `servoHood` and commands the default hood angle during INIT.
- `Shooter.update(robotX, robotY)` runs both distance quadratics in model mode and uses the fixed RPM/angle in fixed mode. It converts the angle to a normalized servo position, clamps it to the calibrated angle endpoints, and sends a new command only when position changes by at least 0.005. Small edits accumulate against the last sent command. Reversed servo endpoints are supported.
- Competition TeleOp `Main` obtains `Shooter` through `Robot`. `Robot.update_robot(currentPose)` updates hood targeting each loop. Gamepad 1 right trigger requests feeding; releasing stops feeding. Gamepad 2 X selects fixed/model mode and bumpers edit fixed RPM during the match. Hood up/down editing is currently available during INIT through `ShooterSelection`, in fixed mode; it moves the servo immediately. There are no match-time hood angle buttons.
- Autonomous `AbstractAuto` initializes its own `Shooter`, uses the same INIT selection, and calls `Shooter.update()` during route actions. `queue_shot()` prepares the flywheel, waits for both RPM and hood readiness (up to 3 seconds), then requests feeding for the configured duration. Invalid hood commands or movement hold the transfer off.
- The tuner uses its own `CachedServo` and writes directly to the same hardware name. Its manual angle, RPM, PIDF object and two coefficient arrays belong only to that tuner instance. Reading `Shooter` defaults does not modify them. Bumpers edit local arrays, never `Shooter` constants; PIDF edits go only to the hub controller used by the tuner. No file writes, persistence or shared configuration setters are used. Competition continues to use its source constants after the tuner exits. Copying results into competition source is an explicit manual step.

## Audit fixes and remaining gaps

The audit added a settling timer to competition feed readiness; immediate fixed-angle commands during INIT; rejection of invalid servo mapping in the tuner; capture of the actual cached angle command rather than an unsent small adjustment; capture rejection for disabled/unstable/invalid shots; a deterministic (9, 9, 0) tuner pose; and true nearest-hive distance selection. Unused duplicate hive classes were removed from the tuner.

Robot measurements are still required for the endpoint angles/servo positions, settling time, both shot curves, encoder ticks/gearing and RPM limits. The active curves remain filler values of 3000 RPM and 45 degrees. Servo interpolation assumes a linear linkage; the code cannot detect a stalled/disconnected servo. There is no fitted-distance domain guard: model outputs can extrapolate and then clamp. The tuner does not drive the intake or drivetrain; reposition through a controlled manual setup and check localization. The 250 ms timer estimates movement completion and cannot confirm it. A continuously changing model angle can keep restarting the timer until the robot/target stabilizes. Measured shot reliability and competition PIDF behavior must be verified on the robot.

Validation on 2026-10-08: the changed Java sources and their direct dependencies compiled with `javac` against the cached FTC SDK and Pedro libraries. The standalone `ShooterHoodChecks` main under `src/test/java` passed 26 checks covering mapping/clamps, invalid input rejection, nearest-hive selection, cached servo writes, immediate fixed-angle edits, settling, and RPM/hood feed gating. This is a standalone JVM check, not an automatically discovered JUnit test. The Android Gradle build could not start because Gradle reported `Unable to establish loopback connection`; no APK or physical robot validation was completed.
