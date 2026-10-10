# Central finite-value validation

Validate each new sensor sample and each outgoing actuator command at its boundary, so downstream control uses cached validity instead of repeating NaN/infinity checks.

Current scope: all robot implementation stays under `TeamCode/src/main/java/org/firstinspires/ftc/teamcode`. Pedro Pathing uses its standard follower, localizer, drivetrain and tuners. Desktop regression checks under `TeamCode/src/test` are separate from the robot implementation.

## Proposed implementation

1. Expand the existing `core/hardware/SensorReadings` into the shared validation entry point. Reject NaN, positive infinity and negative infinity. Cache the result with each sensor sample; do not reread or recheck that sample for readiness, control and telemetry.
2. Apply this to intake distance sensors, shooter encoder velocity, and pose data consumed by Shooter. Check that pose at the mechanism input boundary. Leave Pedro's localization, drive commands, controller updates and tuning behavior to Pedro.
3. Route mechanism motor power/velocity and servo positions through guarded hardware wrappers. Validate computed outputs before clamping or writing them, so infinity cannot silently become full power. Replace scattered equivalent finite checks with the central helper or the cached validity flag. Keep physical range checks, configuration validation and initialization state checks where they serve a separate purpose.
4. Preserve existing shooter/hood changes. Reuse one shooter velocity sample for control, readiness, shot capture and telemetry. Expose invalid-source status without making a stale sample count as a fresh valid one. Refuse nonfinite handoff poses. Limelight currently has no implemented sensor reads.
5. Add fault-injection checks for NaN and both infinities, invalid first samples, invalid samples after a valid command, recovery, unchanged servo settling, and exact motor STOP. Run the existing hood checks and compile TeamCode.

## Proposed invalid-value behavior

- Skip target calculations and servo movements that depend on an invalid sample; hold the last accepted target/position and mark the dependent control unavailable.
- Stop sensor-dependent mechanism motor control and feeding while its required input is invalid. Keep independent controls available and resume when a new valid sample arrives. Pedro owns drivetrain behavior.
- For an invalid outgoing motor command, send zero power/velocity. For an invalid outgoing servo position, send no position command. Explicit STOP always works.
- Reset/freeze controller history on a rejected sample so NaN or a long invalid interval cannot poison the next valid update.

## Verification setup

The original Gradle startup failure was isolated to Java's Unix-domain socket directory. A temporary `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/tmp` override lets the launcher and daemon start. With that override, `:TeamCode:compileDebugJavaWithJavac --offline --no-daemon` passed after restoring standard Pedro. The remaining desktop boundary and hood checks also passed: 277 assertions. No permanent Java or operating-system settings were changed.

Skipping a motor write alone leaves the previous power/velocity command active. That can keep the robot driving or feeding indefinitely, so the proposed policy uses a deliberate stop for those outputs. Valid sensor inputs also cannot guarantee finite computed outputs: arithmetic overflow or a bad model can still produce infinity/NaN, hence the separate actuator boundary check.

## Changes you would like

<!-- Add requested adjustments here. -->

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
