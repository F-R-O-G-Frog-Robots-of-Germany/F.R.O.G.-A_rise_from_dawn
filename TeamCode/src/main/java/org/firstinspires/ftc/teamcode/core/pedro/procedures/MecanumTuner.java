package org.firstinspires.ftc.teamcode.core.pedro.procedures;

import com.pedropathing.tuning.autotune.Display;
import com.pedropathing.tuning.autotune.DisplayName;
import com.pedropathing.tuning.autotune.Inputs;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.TuningOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

/**
 * Pedro Pathing Quickstart v3.0.0 Mecanum tuner, adapted to this project's package.
 * Licensed under the Pedro Pathing BSD 3-Clause Clear License.
 */
public class MecanumTuner extends Procedure {
    private enum Direction {
        @DisplayName("Forward") FORWARD,
        @DisplayName("Reversed") REVERSE
    }

    public MecanumTuner() {
        super("Mecanum Tuner", "A procedure to find the directions of mecanum wheels.");
    }

    @Override
    public void run() throws InterruptedException {
        Inputs motorNames = inputs("Mecanum Motor Names", "Enter the names in HardwareMap of your drivetrain motors.");
        Inputs.Field<String> frontLeftName = motorNames.s("Front Left Name");
        Inputs.Field<String> frontRightName = motorNames.s("Front Right Name");
        Inputs.Field<String> backLeftName = motorNames.s("Back Left Name");
        Inputs.Field<String> backRightName = motorNames.s("Back Right Name");
        awaitInputs(motorNames);

        confirmation("Motor Directions", "Each drivetrain motor will spin, one at a time. After each one, enter whether it spun forward or reversed.");
        Direction frontLeftDirection = test_motor(Display.FourWheelBot.Wheel.FRONT_LEFT, "Front Left", frontLeftName.get());
        Direction frontRightDirection = test_motor(Display.FourWheelBot.Wheel.FRONT_RIGHT, "Front Right", frontRightName.get());
        Direction backLeftDirection = test_motor(Display.FourWheelBot.Wheel.BACK_LEFT, "Back Left", backLeftName.get());
        Direction backRightDirection = test_motor(Display.FourWheelBot.Wheel.BACK_RIGHT, "Back Right", backRightName.get());

        result("frontLeftName", frontLeftName.get());
        result("frontRightName", frontRightName.get());
        result("backLeftName", backLeftName.get());
        result("backRightName", backRightName.get());
        result("frontLeftDirection", frontLeftDirection);
        result("frontRightDirection", frontRightDirection);
        result("backLeftDirection", backLeftDirection);
        result("backRightDirection", backRightDirection);

        code(Language.JAVA, "public static MecanumConfig drivetrainConfig = new MecanumConfig(c -> {\n" +
                " c.frontLeftName.set(\"" + frontLeftName.get() + "\");\n" +
                " c.frontRightName.set(\"" + frontRightName.get() + "\");\n" +
                " c.backLeftName.set(\"" + backLeftName.get() + "\");\n" +
                " c.backRightName.set(\"" + backRightName.get() + "\");\n" +
                " c.frontLeftDirection.set(DcMotorSimple.Direction." + frontLeftDirection + ");\n" +
                " c.frontRightDirection.set(DcMotorSimple.Direction." + frontRightDirection + ");\n" +
                " c.backLeftDirection.set(DcMotorSimple.Direction." + backLeftDirection + ");\n" +
                " c.backRightDirection.set(DcMotorSimple.Direction." + backRightDirection + ");\n" +
                "});");
    }

    private Direction test_motor(Display.FourWheelBot.Wheel wheel, String displayName, String hardwareName) throws InterruptedException {
        final boolean[] correctMotor = new boolean[1];
        final Direction[] direction = new Direction[1];
        withDisplay(Display.fourWheelBot(wheel, false), () -> {
            runOpMode(new SpinMotor(displayName, hardwareName));
            Inputs inputs = inputs(displayName, "Determine the " + displayName.toLowerCase() + " motor direction.");
            Inputs.Field<Boolean> correctMotorField = inputs.b("Did the " + displayName.toLowerCase() + " motor spin?").withDefault(true);
            Inputs.Field<Direction> directionField = inputs.e("Which way did the motor spin?", Direction.class);
            awaitInputs(inputs);
            correctMotor[0] = correctMotorField.get();
            direction[0] = directionField.get();
        });
        if (!correctMotor[0]) {
            abort("The wrong motor spun. Check that your motors are plugged into the correct ports and configured correctly, then try again.");
        }
        return direction[0];
    }

    private static class SpinMotor extends TuningOpMode<Void> {
        private final String name;

        SpinMotor(String displayName, String hardwareName) {
            super(displayName, "The " + displayName.toLowerCase() + " motor will spin. Stop when you know its direction.", true);
            this.name = hardwareName;
        }

        @Override
        protected Void runTuningOpMode() {
            DcMotor motor = hardwareMap.dcMotor.get(name);
            waitForStart();
            motor.setPower(0.5);
            while (opModeIsActive()) {
                // This test intentionally runs until stopped from AutoTune/Driver Station.
            }
            motor.setPower(0.0);
            return null;
        }
    }
}
