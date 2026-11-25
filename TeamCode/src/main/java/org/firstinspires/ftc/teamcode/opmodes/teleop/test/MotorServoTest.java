package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.teamcode.subsystems.Shooter;

import java.util.Locale;

/**
 * Independent hardware checks.
 * Gamepad 1 bumpers change shooter RPM. The right trigger toggles both flywheels.
 * Holding X or Y runs the corresponding flywheel at the target RPM.
 * Other motor buttons run their motor at full power while held.
 * Hold a servo button for position 0.2; releasing it commands position 0.0.
 * Positional servos stay untouched until their button is first pressed.
 */
@TeleOp(name = "Motor and Servo Test", group = "Test")
public class MotorServoTest extends OpMode {
    private static final double MOTOR_POWER = 1.0;
    private static final double DEFAULT_SHOOTER_RPM = 1000.0;
    private static final double RPM_STEP = 100.0;
    private static final double TICKS_PER_SECOND_PER_RPM = Shooter.ENCODER_TICKS_PER_REV / 60.0;
    private static final double SERVO_TEST_POSITION = 0.2;
    private static final double SERVO_RELEASE_POSITION = 0.0;

    // Match the configuration names in Constants, Intake, Shooter, and FlowerIntake.
    private static final String[] MOTOR_NAMES = {
            "left_front", "left_back", "right_front", "right_back",
            "intakeMotor", "transferMotor", "shooterMotorLeft", "shooterMotorRight"
    };
    private static final int FIRST_SHOOTER_MOTOR = 6;
    private static final String[] MOTOR_BUTTONS = {
            "G1 D-pad up", "G1 D-pad down", "G1 D-pad left", "G1 D-pad right",
            "G1 A", "G1 B", "G1 X", "G1 Y"
    };
    private static final String[] SERVO_NAMES = {
            "flowerServoLeft", "flowerServoRight", "servoHood"
    };
    private static final String[] SERVO_BUTTONS = {"G2 A", "G2 B", "G2 X"};

    private final DcMotor[] motors = new DcMotor[MOTOR_NAMES.length];
    private final Servo[] servos = new Servo[SERVO_NAMES.length];
    private final String[] motorStatus = new String[MOTOR_NAMES.length];
    private final String[] servoStatus = new String[SERVO_NAMES.length];
    private final boolean[] servoWasHeld = new boolean[SERVO_NAMES.length];
    private double targetRpm;
    private boolean shooterEnabled;

    @Override
    public void init() {
        targetRpm = DEFAULT_SHOOTER_RPM;
        shooterEnabled = false;
        for (int i = 0; i < motors.length; i++) {
            // Each lookup is independent: a missing motor does not skip the other devices.
            boolean shooterMotor = i >= FIRST_SHOOTER_MOTOR;
            try {
                motors[i] = hardwareMap.get(shooterMotor ? DcMotorEx.class : DcMotor.class, MOTOR_NAMES[i]);
            } catch (IllegalArgumentException missingDevice) {
                motors[i] = null;
                motorStatus[i] = "MISSING / wrong device type: " + missingDevice.getMessage();
                continue;
            }
            motors[i].setPower(0.0);
            boolean reversed = MOTOR_NAMES[i].equals("left_front")
                    || MOTOR_NAMES[i].equals("left_back")
                    || MOTOR_NAMES[i].equals("intakeMotor")
                    || MOTOR_NAMES[i].equals("shooterMotorLeft"); // G1 X
            motors[i].setDirection(reversed
                    ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
            motors[i].setMode(shooterMotor ? DcMotor.RunMode.RUN_USING_ENCODER : DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motorStatus[i] = "READY";
        }

        for (int i = 0; i < servos.length; i++) {
            try {
                servos[i] = hardwareMap.get(Servo.class, SERVO_NAMES[i]);
                servoStatus[i] = "READY (not moved)";
            } catch (IllegalArgumentException missingDevice) {
                servoStatus[i] = "MISSING / wrong device type: " + missingDevice.getMessage();
            }
        }
        show_telemetry();
    }

    @Override
    public void init_loop() {
        show_telemetry();
    }

    @Override
    public void loop() {
        boolean increaseRpm = gamepad1.rightBumperWasPressed();
        boolean decreaseRpm = gamepad1.leftBumperWasPressed();
        if (increaseRpm != decreaseRpm) {
            double delta = increaseRpm ? RPM_STEP : -RPM_STEP;
            targetRpm = Math.max(0.0, Math.min(Shooter.MAX_RPM, targetRpm + delta));
        }
        if (gamepad1.rightTriggerWasPressed()) shooterEnabled = !shooterEnabled;

        boolean[] motorHeld = {
                gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.dpad_left, gamepad1.dpad_right,
                gamepad1.a, gamepad1.b, gamepad1.x, gamepad1.y
        };
        for (int i = 0; i < motors.length; i++) {
            if (motors[i] == null) continue;
            if (i >= FIRST_SHOOTER_MOTOR) {
                DcMotorEx motor = (DcMotorEx) motors[i];
                double commandedRpm = shooterEnabled || motorHeld[i] ? targetRpm : 0.0;
                motor.setVelocity(commandedRpm * TICKS_PER_SECOND_PER_RPM);
                double measuredRpm = motor.getVelocity() / TICKS_PER_SECOND_PER_RPM;
                motorStatus[i] = String.format(Locale.US, "%.0f RPM / %.0f commanded | SDK power %.3f",
                        measuredRpm, commandedRpm, motor.getPower());
            } else {
                motors[i].setPower(motorHeld[i] ? MOTOR_POWER : 0.0);
                motorStatus[i] = motorHeld[i]
                        ? "RUNNING " + MOTOR_POWER + " " + motors[i].getDirection() : "STOPPED";
            }
        }

        boolean[] servoHeld = {gamepad2.a, gamepad2.b, gamepad2.x};
        for (int i = 0; i < servos.length; i++) {
            if (servos[i] != null && servoHeld[i] != servoWasHeld[i]) {
                double position = servoHeld[i] ? SERVO_TEST_POSITION : SERVO_RELEASE_POSITION;
                servos[i].setPosition(position);
                servoStatus[i] = "Position " + position;
            }
            servoWasHeld[i] = servoHeld[i];
        }
        show_telemetry();
    }

    @Override
    public void stop() {
        for (int i = 0; i < motors.length; i++) {
            if (motors[i] == null) continue;
            try {
                motors[i].setPower(0.0);
            } catch (RuntimeException stopFailure) {
                // Still attempt to stop every other motor if a controller fails.
                telemetry.log().add("Could not stop %s: %s", MOTOR_NAMES[i], stopFailure.getMessage());
            }
        }
    }

    private void show_telemetry() {
        telemetry.addData("Shooter target RPM (G1 LB/RB -/+100)", "%.0f", targetRpm);
        telemetry.addData("Both shooter motors (G1 RT toggles)", shooterEnabled ? "ENABLED" : "OFF");
        for (int i = FIRST_SHOOTER_MOTOR; i < motors.length; i++) {
            add_motor_telemetry(i);
        }
        telemetry.addLine("Hold G1 X/Y for a single shooter motor. Hold G1 A/B for intake/transfer.");
        telemetry.addLine("Hold servo button: position 0.2; release: position 0.0.");
        for (int i = 0; i < FIRST_SHOOTER_MOTOR; i++) {
            add_motor_telemetry(i);
        }
        for (int i = 0; i < servos.length; i++) {
            telemetry.addData(SERVO_NAMES[i] + " [" + SERVO_BUTTONS[i] + "]", servoStatus[i]);
        }
        telemetry.update();
    }

    private void add_motor_telemetry(int index) {
        telemetry.addData(MOTOR_NAMES[index] + " [" + MOTOR_BUTTONS[index] + "]", motorStatus[index]);
    }
}
