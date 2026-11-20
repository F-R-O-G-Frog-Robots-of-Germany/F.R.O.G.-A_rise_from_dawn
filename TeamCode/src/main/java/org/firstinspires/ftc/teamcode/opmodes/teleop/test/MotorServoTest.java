package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;

/**
 * Independent hardware checks; no subsystem, sensor, or localizer initialization is required.
 * Hold a motor button for +0.2 power in SDK FORWARD; releasing it stops that motor.
 * Hold a servo button for position 0.2; releasing it commands position 0.0.
 * Positional servos stay untouched until their button is first pressed.
 */
@TeleOp(name = "Motor and Servo Test", group = "Test")
public class MotorServoTest extends OpMode {
    private static final double MOTOR_POWER = 0.2;
    private static final double SERVO_TEST_POSITION = 0.2;
    private static final double SERVO_RELEASE_POSITION = 0.0;

    // Match the configuration names in Constants, Intake, Shooter, and FlowerIntake.
    private static final String[] MOTOR_NAMES = {
            "leftFront", "leftRear", "rightFront", "rightRear",
            "intakeMotor", "transferMotor", "shooterMotorLeft", "shooterMotorRight"
    };
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

    @Override
    public void init() {
        for (int i = 0; i < motors.length; i++) {
            // Each lookup is independent: a missing motor does not skip the other devices.
            try {
                motors[i] = hardwareMap.get(DcMotor.class, MOTOR_NAMES[i]);
            } catch (IllegalArgumentException missingDevice) {
                motorStatus[i] = "MISSING / wrong device type: " + missingDevice.getMessage();
                continue;
            }
            motors[i].setPower(0.0);
            motors[i].setDirection(DcMotorSimple.Direction.FORWARD);
            motors[i].setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
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
        boolean[] motorHeld = {
                gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.dpad_left, gamepad1.dpad_right,
                gamepad1.a, gamepad1.b, gamepad1.x, gamepad1.y
        };
        for (int i = 0; i < motors.length; i++) {
            if (motors[i] != null) {
                motors[i].setPower(motorHeld[i] ? MOTOR_POWER : 0.0);
                motorStatus[i] = motorHeld[i] ? "RUNNING +0.2" : "STOPPED";
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
        telemetry.addLine("Hold motor button: FORWARD +0.2; release: stop.");
        telemetry.addLine("Hold servo button: position 0.2; release: position 0.0.");
        for (int i = 0; i < motors.length; i++) {
            telemetry.addData(MOTOR_NAMES[i] + " [" + MOTOR_BUTTONS[i] + "]", motorStatus[i]);
        }
        for (int i = 0; i < servos.length; i++) {
            telemetry.addData(SERVO_NAMES[i] + " [" + SERVO_BUTTONS[i] + "]", servoStatus[i]);
        }
        telemetry.update();
    }
}
