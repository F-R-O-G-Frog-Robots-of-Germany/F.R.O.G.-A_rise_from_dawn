package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/**
 * Test opMode for tuning PIDF and collecting distance / RPM / hood-angle shot data.
 *
 * <p>Controls (gamepad 1):
 * <ul>
 *     <li>Start: toggle the shooter on/off.</li>
 *     <li>X: disable/enable the right shooter motor (both enabled by default).</li>
 *     <li>Y/B: increase/decrease target RPM by 100.</li>
 *     <li>D-pad up/right/down/left: select P/I/D/F respectively.</li>
 *     <li>Right bumper/left bumper: increase/decrease the selected PIDF value.</li>
 *     <li>Back: restore 4000 RPM and the default PIDF values.</li>
 * </ul>
 * Controls are edge-triggered; tap a button to make one change.</p>
 * <p>Gamepad 2: Y toggles both distance models; X selects RPM or hood coefficients;
 * left/right selects A/B/C; bumpers edit the coefficient; up/down edits manual hood
 * angle by one degree; A captures the displayed distance, measured RPM and commanded
 * hood angle for a Desmos table. Captures are session-only and must be copied manually.</p>
 */
@TeleOp(name = "Shooter PIDF Tuner", group = "Test")
public class ShooterPidfTuner extends LinearOpMode {
    private static final double DEFAULT_TARGET_RPM = 4000.0;
    private static final double RPM_STEP = 100.0;
    private static final int LOOP_PERIOD_MS = 40;
    private static final double START_X = 9.0;
    private static final double START_Y = 9.0;
    private static final double START_HEADING = 0.0; // Radians; face along field +X.

    private static final double DEFAULT_P = 0.0002;
    private static final double DEFAULT_I = 0.0000001;
    private static final double DEFAULT_D = 0.00001;
    private static final double DEFAULT_F = 0.00017;

    private CachedMotor shooterMotorLeft;
    private CachedMotor shooterMotorRight;
    private CachedServo hoodServo;
    private Follower follower;
    private Alliance alliance;
    private double targetRpm = DEFAULT_TARGET_RPM;
    private PIDFCoefficients pidf = new PIDFCoefficients(DEFAULT_P, DEFAULT_I, DEFAULT_D, DEFAULT_F);
    private PidfParameter selectedParameter = PidfParameter.P;
    private boolean shooterEnabled = false;
    private boolean rightMotorEnabled = true;

    private boolean editingHood;
    private boolean distanceModelEnabled;
    private int selectedCoefficient;
    private final double[] coefficients = {Shooter.DISTANCE_A, Shooter.DISTANCE_B, Shooter.DISTANCE_C};
    private final double[] hoodCoefficients = {Shooter.HOOD_DISTANCE_A, Shooter.HOOD_DISTANCE_B, Shooter.HOOD_DISTANCE_C};
    private static final double[] COEFFICIENT_STEPS = {0.0001, 0.01, 1.0};
    private static final double[] HOOD_COEFFICIENT_STEPS = {0.0001, 0.001, 0.1};
    private double manualHoodAngle = Shooter.DEFAULT_HOOD_ANGLE_DEG;
    private double commandedHoodAngle = Shooter.DEFAULT_HOOD_ANGLE_DEG;
    private double hoodPosition = Double.NaN;
    private double appliedHoodPosition;
    private boolean hoodCommanded;
    private double appliedHoodAngle = Double.NaN;
    private long lastHoodCommandNanos;
    private String capturedSample = "No sample; G2 A captures distance[in], measured RPM, commanded hood[deg]";
    private String captureMessage = "No capture attempted";
    private double commandedRpm = DEFAULT_TARGET_RPM;
    private double measuredRpm;
    private double measuredRpm2;
    private boolean validTargets;
    private String modelMessage = "Fit A/B/C in Desmos and enter them in Shooter.java manually";

    @Override
    public void runOpMode() throws InterruptedException {
        try {
            shooterMotorLeft = new CachedMotor(hardwareMap.get(DcMotorEx.class, "shooterMotorLeft"), 0.002);
            shooterMotorLeft.set_power(0.0);
            shooterMotorLeft.raw().setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            shooterMotorLeft.raw().setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
            shooterMotorRight = new CachedMotor(hardwareMap.get(DcMotorEx.class, "shooterMotorRight"), 0.002);
            shooterMotorRight.set_power(0.0);
            shooterMotorRight.raw().setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            shooterMotorRight.raw().setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
            hoodServo = new CachedServo(hardwareMap.get(Servo.class, "servoHood"), Shooter.HOOD_POSITION_EPSILON);
            command_hood();
            apply_pidf();

            follower = Constants.create_follower(hardwareMap);
            alliance = LastPositionStorage.get_current_alliance();
            follower.setPose(new Pose(START_X, START_Y, START_HEADING));
            follower.update();
            telemetry.setMsTransmissionInterval(LOOP_PERIOD_MS);

            while (opModeInInit()) {
                if (gamepad2.bWasPressed()) {
                    alliance = alliance == Alliance.RED ? Alliance.BLUE : Alliance.RED;
                }
                telemetry.addLine("Shooter PIDF tuner ready; start at (9, 9), heading 0 deg");
                telemetry.addData("Alliance (G2 B toggles during INIT)", alliance);
                telemetry.addLine("Start: toggle | Y/B: RPM | D-pad: P/I/D/F");
                telemetry.addLine("G1 X: disable/enable shooterMotorRight (both motors enabled by default)");
                telemetry.addLine("Bumpers: decrease/increase selected PIDF");
                telemetry.addLine("G2 Y: both models; X: RPM/hood curve; left/right: A/B/C; bumpers: edit");
                telemetry.addLine("G2 up/down: manual hood +/-1 deg; A: capture sample");
                telemetry.addLine("Edits are local to this run; no competition constants are changed");
                telemetry.update();
                idle();
            }

            waitForStart();
            if (isStopRequested()) {
                return;
            }

            start_controls();

            while (opModeIsActive()) {
                follower.update();
                update_controls();
                Pose pose = follower.pose();
                boolean poseValid = SensorReadings.is_valid(pose);
                Shooter.HiveDistance hive = Shooter.nearest_hive(pose.x(), pose.y(), alliance, poseValid);
                SensorReadings.Sample velocity = shooterMotorLeft.sample_velocity();
                SensorReadings.Sample velocity2 = shooterMotorRight.sample_velocity();
                measuredRpm = velocity.get_value() / Shooter.ENCODER_TICKS_PER_REV * 60.0;
                measuredRpm2 = velocity2.get_value() / Shooter.ENCODER_TICKS_PER_REV * 60.0;
                update_model_controls();
                validTargets = !distanceModelEnabled || hive.valid;
                if (distanceModelEnabled) {
                    Shooter.ShotSolution solution = Shooter.calculate_shot_solution(hive.distance,
                            coefficients[0], coefficients[1], coefficients[2],
                            hoodCoefficients[0], hoodCoefficients[1], hoodCoefficients[2]);
                    validTargets = validTargets && solution.valid;
                    if (validTargets) {
                        commandedRpm = solution.rpm;
                        commandedHoodAngle = solution.hoodAngleDegrees;
                    }
                } else {
                    commandedRpm = targetRpm;
                    commandedHoodAngle = manualHoodAngle;
                }
                if (validTargets) validTargets = command_hood();
                if (!validTargets) {
                    modelMessage = "Invalid shot or hood mapping; motor stopped, hood holding last command";
                } else {
                    modelMessage = "Fit both curves to matched lowest-reliable-RPM shot rows";
                }
                validTargets &= command_flywheels(velocity.is_valid()
                        && (!rightMotorEnabled || velocity2.is_valid()));

                if (gamepad2.aWasPressed()) {
                    if (shooterEnabled && validTargets && commandedRpm > 0
                            && hive.valid && velocity.is_valid()
                            && Math.abs(measuredRpm - commandedRpm) <= Shooter.SHOOT_SPEED_TOLERANCE_RPM
                            && (!rightMotorEnabled || (velocity2.is_valid()
                                && Math.abs(measuredRpm2 - commandedRpm) <= Shooter.SHOOT_SPEED_TOLERANCE_RPM))
                            && hood_settled()) {
                        capturedSample = String.format(java.util.Locale.US, "%.3f, %.1f, %.2f",
                                hive.distance, measuredRpm, appliedHoodAngle);
                        captureMessage = "Captured with " + (rightMotorEnabled ? "both motors" : "left motor only")
                                + "; verify the shot succeeded before using this row";
                    } else {
                        captureMessage = "Rejected: require valid targets, motor ON, RPM in tolerance and settled hood";
                    }
                }

                add_telemetry(pose, hive);
                sleep(LOOP_PERIOD_MS);
            }

        } finally {
            if (shooterMotorLeft != null) {
                shooterMotorLeft.set_velocity(0.0);
                shooterMotorLeft.set_power(0.0);
            }
            if (shooterMotorRight != null) {
                shooterMotorRight.set_velocity(0.0);
                shooterMotorRight.set_power(0.0);
            }
            if (follower != null) {
                follower.stop();
                follower.drivetrain.stop();
            }
        }
    }

    /** Track the angle actually sent, including the servo cache's deadband. */
    private boolean command_hood() {
        hoodPosition = Shooter.hood_angle_to_servo_position(commandedHoodAngle);
        if (!hoodServo.set_position(hoodPosition)) return false;
        if (!hoodCommanded || Math.abs(hoodPosition - appliedHoodPosition) >= Shooter.HOOD_POSITION_EPSILON) {
            appliedHoodPosition = hoodPosition;
            hoodCommanded = true;
            appliedHoodAngle = Math.max(Shooter.HOOD_MIN_ANGLE_DEG,
                    Math.min(Shooter.HOOD_MAX_ANGLE_DEG, commandedHoodAngle));
            lastHoodCommandNanos = System.nanoTime();
        }
        return true;
    }

    private boolean hood_settled() {
        return hoodCommanded
                && System.nanoTime() - lastHoodCommandNanos >= Shooter.HOOD_SETTLE_TIME_MS * 1_000_000L;
    }

    private void start_controls() {
        gamepad1.resetEdgeDetection();
        gamepad1.copy(gamepad1);
        gamepad2.resetEdgeDetection();
        gamepad2.copy(gamepad2);
    }

    private void update_controls() {
        if (gamepad1.startWasPressed()) {
            shooterEnabled = !shooterEnabled;
        }
        if (gamepad1.xWasPressed()) {
            rightMotorEnabled = !rightMotorEnabled;
        }
        if (gamepad1.backWasPressed()) {
            targetRpm = DEFAULT_TARGET_RPM;
            pidf = new PIDFCoefficients(DEFAULT_P, DEFAULT_I, DEFAULT_D, DEFAULT_F);
            selectedParameter = PidfParameter.P;
            apply_pidf();
        }

        if (gamepad1.yWasPressed()) {
            set_target_rpm(targetRpm + RPM_STEP);
        }
        if (gamepad1.bWasPressed()) {
            set_target_rpm(targetRpm - RPM_STEP);
        }

        if (gamepad1.dpadUpWasPressed()) {
            selectedParameter = PidfParameter.P;
        }
        if (gamepad1.dpadRightWasPressed()) {
            selectedParameter = PidfParameter.I;
        }
        if (gamepad1.dpadDownWasPressed()) {
            selectedParameter = PidfParameter.D;
        }
        if (gamepad1.dpadLeftWasPressed()) {
            selectedParameter = PidfParameter.F;
        }
        if (gamepad1.rightBumperWasPressed()) {
            change_selected_pidf(1.0);
        }
        if (gamepad1.leftBumperWasPressed()) {
            change_selected_pidf(-1.0);
        }
    }

    private void set_target_rpm(double rpm) {
        targetRpm = SensorReadings.clamp(rpm, 0.0, Shooter.MAX_RPM);
    }

    private void change_selected_pidf(double direction) {
        switch (selectedParameter) {
            case P:
                pidf.p = Math.max(0.0, pidf.p + direction * 0.0001);
                break;
            case I:
                pidf.i = Math.max(0.0, pidf.i + direction * 0.00000001);
                break;
            case D:
                pidf.d = Math.max(0.0, pidf.d + direction * 0.000001);
                break;
            case F:
                pidf.f = Math.max(0.0, pidf.f + direction * 0.0001);
                break;
        }
        apply_pidf();
    }

    private void apply_pidf() {
        if (shooterMotorLeft != null && SensorReadings.are_valid(pidf.p, pidf.i, pidf.d, pidf.f)) {
            shooterMotorLeft.raw().setVelocityPIDFCoefficients(pidf.p, pidf.i, pidf.d, pidf.f);
        }
        if (shooterMotorRight != null && SensorReadings.are_valid(pidf.p, pidf.i, pidf.d, pidf.f)) {
            shooterMotorRight.raw().setVelocityPIDFCoefficients(pidf.p, pidf.i, pidf.d, pidf.f);
        }
    }

    private boolean command_flywheels(boolean velocityValid) {
        double targetVelocity = shooterEnabled && validTargets && velocityValid
                ? rpm_to_ticks_per_second(commandedRpm) : 0.0;
        boolean accepted = shooterMotorLeft.set_velocity(targetVelocity);
        accepted &= shooterMotorRight.set_velocity(rightMotorEnabled ? targetVelocity : 0.0);
        return accepted;
    }

    private void update_model_controls() {
        boolean up = gamepad2.dpadUpWasPressed();
        boolean down = gamepad2.dpadDownWasPressed();
        if (gamepad2.yWasPressed()) distanceModelEnabled = !distanceModelEnabled;
        if (gamepad2.xWasPressed()) editingHood = !editingHood;
        if (!distanceModelEnabled) {
            if (up) manualHoodAngle = Math.min(Shooter.HOOD_MAX_ANGLE_DEG, manualHoodAngle + 1);
            if (down) manualHoodAngle = Math.max(Shooter.HOOD_MIN_ANGLE_DEG, manualHoodAngle - 1);
        }
        if (gamepad2.dpadLeftWasPressed()) selectedCoefficient = (selectedCoefficient + 2) % 3;
        if (gamepad2.dpadRightWasPressed()) selectedCoefficient = (selectedCoefficient + 1) % 3;
        double[] curve = editingHood ? hoodCoefficients : coefficients;
        double[] steps = editingHood ? HOOD_COEFFICIENT_STEPS : COEFFICIENT_STEPS;
        if (gamepad2.leftBumperWasPressed()) {
            curve[selectedCoefficient] -= steps[selectedCoefficient];
        }
        if (gamepad2.rightBumperWasPressed()) {
            curve[selectedCoefficient] += steps[selectedCoefficient];
        }
    }

    private void add_telemetry(Pose pose, Shooter.HiveDistance nearestHive) {
        double robotX = pose.x();
        double robotY = pose.y();

        telemetry.addData("Shooter", shooterEnabled ? "RUNNING" : "OFF");
        telemetry.addData("Right shooter (G1 X toggles)", rightMotorEnabled ? "ENABLED" : "DISABLED");
        telemetry.addData("Target RPM", "%.1f", commandedRpm);
        telemetry.addData("Target mode", distanceModelEnabled ? "QUADRATIC MODEL" : "MANUAL RPM");
        telemetry.addData("Selected coefficient", "ABC".charAt(selectedCoefficient));
        telemetry.addData("Editing curve", editingHood ? "HOOD" : "RPM");
        telemetry.addData("A (RPM/in^2)", "%.8f", coefficients[0]);
        telemetry.addData("B (RPM/in)", "%.8f", coefficients[1]);
        telemetry.addData("C (RPM)", "%.8f", coefficients[2]);
        telemetry.addData("Hood A (deg/in^2)", "%.8f", hoodCoefficients[0]);
        telemetry.addData("Hood B (deg/in)", "%.8f", hoodCoefficients[1]);
        telemetry.addData("Hood C (deg)", "%.8f", hoodCoefficients[2]);
        telemetry.addData("Commanded hood [deg]", "%.2f", commandedHoodAngle);
        telemetry.addData("Applied hood command [deg]", "%.2f", appliedHoodAngle);
        telemetry.addData("Hood servo position (last sent)", appliedHoodPosition);
        telemetry.addData("Hood command settled (timer only)", hood_settled());
        telemetry.addData("Last captured sample: distance, RPM, hood", capturedSample);
        telemetry.addData("Capture status", captureMessage);
        telemetry.addData("Model", modelMessage);
        telemetry.addData("Measured RPM", "%.1f", measuredRpm);
        telemetry.addData("Encoder valid", shooterMotorLeft.get_velocity_sample().is_valid());
        telemetry.addData("Right shooter measured RPM", "%.1f", measuredRpm2);
        telemetry.addData("Right shooter encoder valid", shooterMotorRight.get_velocity_sample().is_valid());
        telemetry.addData("Selected PIDF", selectedParameter.name());
        telemetry.addData("PIDF step", "P/F: 0.0001 | I: 0.00000001 | D: 0.000001");
        telemetry.addData("P", "%.8f", pidf.p);
        telemetry.addData("I", "%.8f", pidf.i);
        telemetry.addData("D", "%.8f", pidf.d);
        telemetry.addData("F", "%.8f", pidf.f);
        telemetry.addData("Alliance", alliance);
        telemetry.addData("Robot position", "x %.2f, y %.2f", robotX, robotY);
        telemetry.addData("Target hive", nearestHive.position.name);
        telemetry.addData("Distance to nearest hive [in]", "%.2f", nearestHive.distance);
        telemetry.addData("Loop", LOOP_PERIOD_MS + " ms");
        telemetry.update();
    }

    private static double rpm_to_ticks_per_second(double rpm) {
        return rpm * Shooter.ENCODER_TICKS_PER_REV / 60.0;
    }

    private enum PidfParameter {
        P, I, D, F
    }

}
