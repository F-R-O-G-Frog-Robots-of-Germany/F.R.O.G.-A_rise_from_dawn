package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/**
 * Test opMode for tuning the shooter velocity controller and flywheel distance formula.
 *
 * <p>Controls (gamepad 1):
 * <ul>
 *     <li>Start: toggle the shooter on/off.</li>
 *     <li>Y/B: increase/decrease target RPM by 100.</li>
 *     <li>D-pad up/right/down/left: select P/I/D/F respectively.</li>
 *     <li>Right bumper/left bumper: increase/decrease the selected PIDF value.</li>
 *     <li>Back: restore 4000 RPM and the default PIDF values.</li>
 * </ul>
 * Controls are edge-triggered; tap a button to make one change.</p>
 */
@TeleOp(name = "Shooter PIDF Tuner", group = "Test")
public class ShooterPidfTuner extends LinearOpMode {
    private static final double DEFAULT_TARGET_RPM = 4000.0;
    private static final double RPM_STEP = 100.0;
    private static final int LOOP_PERIOD_MS = 40;

    private static final double DEFAULT_P = 0.0002;
    private static final double DEFAULT_I = 0.0000001;
    private static final double DEFAULT_D = 0.00001;
    private static final double DEFAULT_F = 0.00017;

    private DcMotorEx shooterMotor;
    private Follower follower;
    private Alliance alliance;
    private double targetRpm = DEFAULT_TARGET_RPM;
    private PIDFCoefficients pidf = new PIDFCoefficients(DEFAULT_P, DEFAULT_I, DEFAULT_D, DEFAULT_F);
    private PidfParameter selectedParameter = PidfParameter.P;
    private boolean shooterEnabled = false;

    private boolean previousStart;
    private boolean previousBack;
    private boolean previousY;
    private boolean previousB;
    private boolean previousDpadUp;
    private boolean previousDpadRight;
    private boolean previousDpadDown;
    private boolean previousDpadLeft;
    private boolean previousRightBumper;
    private boolean previousLeftBumper;
    private boolean previousModel;
    private boolean previousModelLeft, previousModelRight, previousModelDecrease, previousModelIncrease;
    private boolean distanceModelEnabled;
    private int selectedCoefficient;
    private final double[] coefficients = {Shooter.DISTANCE_A, Shooter.DISTANCE_B, Shooter.DISTANCE_C};
    private static final double[] COEFFICIENT_STEPS = {0.0001, 0.01, 1.0};
    private double commandedRpm = DEFAULT_TARGET_RPM;
    private String modelMessage = "Fit A/B/C in Desmos and enter them in Shooter.java manually";

    @Override
    public void runOpMode() throws InterruptedException {
        try {
            shooterMotor = hardwareMap.get(DcMotorEx.class, "shooter");
            shooterMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            shooterMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
            apply_pidf();

            follower = Constants.create_follower(hardwareMap);
            alliance = LastPositionStorage.get_current_alliance();
            if (LastPositionStorage.valid_data_available()) follower.setPose(LastPositionStorage.get_last_position());
            follower.update();
            telemetry.setMsTransmissionInterval(LOOP_PERIOD_MS);

            telemetry.addLine("Shooter PIDF tuner ready");
            telemetry.addLine("Start: toggle | Y/B: RPM | D-pad: P/I/D/F");
            telemetry.addLine("Bumpers: decrease/increase selected PIDF");
            telemetry.addLine("G2 Y: model test; left/right: A/B/C; bumpers: edit");
            telemetry.update();

            waitForStart();
            if (isStopRequested()) {
                return;
            }

            while (opModeIsActive()) {
                follower.update();
                update_controls();
                Pose pose = follower.pose();
                Shooter.HiveDistance hive = Shooter.nearest_hive(pose.x(), pose.y(), alliance);
                update_model_controls();
                commandedRpm = distanceModelEnabled
                        ? coefficients[0] * hive.distance * hive.distance + coefficients[1] * hive.distance + coefficients[2]
                        : targetRpm;
                if (!Double.isFinite(commandedRpm)) {
                    commandedRpm = 0;
                    modelMessage = "Invalid model output; motor command stopped";
                }
                commandedRpm = Math.max(0, commandedRpm);

                if (shooterEnabled) {
                    shooterMotor.setVelocity(rpm_to_ticks_per_second(commandedRpm));
                } else {
                    shooterMotor.setVelocity(0.0);
                }

                add_telemetry(pose, hive);
                sleep(LOOP_PERIOD_MS);
            }

        } finally {
            if (shooterMotor != null) {
                shooterMotor.setVelocity(0.0);
                shooterMotor.setPower(0.0);
            }
            if (follower != null) {
                follower.stop();
                follower.drivetrain.stop();
            }
        }
    }

    private void update_controls() {
        if (pressed(gamepad1.start, previousStart)) {
            shooterEnabled = !shooterEnabled;
        }
        if (pressed(gamepad1.back, previousBack)) {
            targetRpm = DEFAULT_TARGET_RPM;
            pidf = new PIDFCoefficients(DEFAULT_P, DEFAULT_I, DEFAULT_D, DEFAULT_F);
            selectedParameter = PidfParameter.P;
            apply_pidf();
        }

        if (pressed(gamepad1.y, previousY)) {
            set_target_rpm(targetRpm + RPM_STEP);
        }
        if (pressed(gamepad1.b, previousB)) {
            set_target_rpm(targetRpm - RPM_STEP);
        }

        if (pressed(gamepad1.dpad_up, previousDpadUp)) {
            selectedParameter = PidfParameter.P;
        }
        if (pressed(gamepad1.dpad_right, previousDpadRight)) {
            selectedParameter = PidfParameter.I;
        }
        if (pressed(gamepad1.dpad_down, previousDpadDown)) {
            selectedParameter = PidfParameter.D;
        }
        if (pressed(gamepad1.dpad_left, previousDpadLeft)) {
            selectedParameter = PidfParameter.F;
        }
        if (pressed(gamepad1.right_bumper, previousRightBumper)) {
            change_selected_pidf(1.0);
        }
        if (pressed(gamepad1.left_bumper, previousLeftBumper)) {
            change_selected_pidf(-1.0);
        }

        previousStart = gamepad1.start;
        previousBack = gamepad1.back;
        previousY = gamepad1.y;
        previousB = gamepad1.b;
        previousDpadUp = gamepad1.dpad_up;
        previousDpadRight = gamepad1.dpad_right;
        previousDpadDown = gamepad1.dpad_down;
        previousDpadLeft = gamepad1.dpad_left;
        previousRightBumper = gamepad1.right_bumper;
        previousLeftBumper = gamepad1.left_bumper;
    }

    private void set_target_rpm(double rpm) {
        targetRpm = Math.max(0.0, rpm);
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
        if (shooterMotor != null) {
            shooterMotor.setVelocityPIDFCoefficients(pidf.p, pidf.i, pidf.d, pidf.f);
        }
    }

    private void update_model_controls() {
        if (pressed(gamepad2.y, previousModel)) distanceModelEnabled = !distanceModelEnabled;
        if (pressed(gamepad2.dpad_left, previousModelLeft)) selectedCoefficient = (selectedCoefficient + 2) % 3;
        if (pressed(gamepad2.dpad_right, previousModelRight)) selectedCoefficient = (selectedCoefficient + 1) % 3;
        if (pressed(gamepad2.left_bumper, previousModelDecrease)) {
            coefficients[selectedCoefficient] -= COEFFICIENT_STEPS[selectedCoefficient];
        }
        if (pressed(gamepad2.right_bumper, previousModelIncrease)) {
            coefficients[selectedCoefficient] += COEFFICIENT_STEPS[selectedCoefficient];
        }
        previousModel = gamepad2.y;
        previousModelLeft = gamepad2.dpad_left; previousModelRight = gamepad2.dpad_right;
        previousModelDecrease = gamepad2.left_bumper; previousModelIncrease = gamepad2.right_bumper;
    }

    private void add_telemetry(Pose pose, Shooter.HiveDistance nearestHive) {
        double measuredVelocity = Math.abs(shooterMotor.getVelocity());
        double measuredRpm = measuredVelocity * 60.0 / Shooter.TICKS_PER_REV;
        double robotX = pose.x();
        double robotY = pose.y();

        telemetry.addData("Shooter", shooterEnabled ? "RUNNING" : "OFF");
        telemetry.addData("Target RPM", "%.1f", commandedRpm);
        telemetry.addData("Target mode", distanceModelEnabled ? "QUADRATIC MODEL" : "MANUAL RPM");
        telemetry.addData("Selected coefficient", "ABC".charAt(selectedCoefficient));
        telemetry.addData("A (RPM/in^2)", "%.8f", coefficients[0]);
        telemetry.addData("B (RPM/in)", "%.8f", coefficients[1]);
        telemetry.addData("C (RPM)", "%.8f", coefficients[2]);
        telemetry.addData("Model", modelMessage);
        telemetry.addData("Measured RPM", "%.1f", measuredRpm);
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
        return rpm * Shooter.TICKS_PER_REV / 60.0;
    }

    private static boolean pressed(boolean current, boolean previous) {
        return current && !previous;
    }

    private enum PidfParameter {
        P, I, D, F
    }

    private static class HivePosition {
        private final String name;
        private final double x;
        private final double y;
        private final double z;

        private HivePosition(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class HiveDistance {
        private final HivePosition position;
        private final double distance;

        private HiveDistance(HivePosition position, double distance) {
            this.position = position;
            this.distance = distance;
        }
    }
}
