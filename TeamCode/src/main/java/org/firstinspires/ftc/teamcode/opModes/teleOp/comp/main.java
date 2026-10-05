package org.firstinspires.ftc.teamcode.opModes.teleOp.comp;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.follower.ManualDrive;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.opModes.teleOp.AbstractTeleOp;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.intake;
import org.firstinspires.ftc.teamcode.subsystems.shooter;

@TeleOp(name = "Main opMode", group = "main")
public class main extends AbstractTeleOp {
    private Robot robot;
    private shooter shooterSubsystem;
    private intake intakeSubsystem;
    private FlowerIntake flowerIntakeSubsystem;
    private double driveForward = 0, driveStrafe = 0, driveTurn = 0;
    private static final double DEADBAND = 0.05;

    public main() {
        compMode = true;
    }

    @Override
    protected void onInit() {
        robot = new Robot();
        robot.initRobot(hardwareMap, follower, alliance, telemetry, panelsEnabled());
        shooterSubsystem = robot.getShooter();
        intakeSubsystem = robot.getIntake();
        flowerIntakeSubsystem = robot.getFlowerIntake();
        setTelemetryManager(robot.getTelemetryManager());

        inputManager.gamepad2.dpad_right.addButtonPressListener(() -> resetPose(9.0, 9.0));
        inputManager.gamepad2.dpad_left.addButtonPressListener(() -> resetPose(9.0, 133.0));
        inputManager.gamepad2.b.addButtonPressListener(shooterSubsystem::toggleBypass);
        inputManager.gamepad2.left_bumper.addButtonPressListener(() -> adjustShooterTargetRpm(-100));
        inputManager.gamepad2.right_bumper.addButtonPressListener(() -> adjustShooterTargetRpm(100));
        inputManager.gamepad1.right_trigger.addFullPressListener(value -> shooterSubsystem.shoot());
        inputManager.gamepad1.right_trigger.addTriggerReleaseListener(value -> shooterSubsystem.stopShooting());
        inputManager.gamepad1.left_bumper.addButtonPressListener(() -> FlowerIntake.toggle());
        inputManager.gamepad1.left_trigger.addFullPressListener(value -> intakeSubsystem.toggleShootMode());
        inputManager.gamepad1.left_stick.addUpdateListener((x, y) -> {
            driveForward = apply_deadband(-y);
            driveStrafe = apply_deadband(x);
        });
        inputManager.gamepad1.right_stick.addUpdateListener((x, y) -> {
            driveTurn = apply_deadband(x);
        });
    }

    @Override
    protected void onStart() {
        robot.startRobot();
    }

    @Override
    protected void opModeLoop() {
        robot.updateRobot();

        double heading = follower.pose().heading();
        double forward = driveForward * driveSpeed * vetoSpeed;
        double strafe = driveStrafe * driveSpeed * vetoSpeed;
        double turn = driveTurn * driveSpeed * vetoSpeed;

        DrivePowers powers = ManualDrive.fieldCentric(
                forward, strafe, turn, heading,
                alliance == Alliance.BLUE ? Math.PI : 0
        );
        follower.manual(powers);
    }

    private double apply_deadband(double val) {
        if (Math.abs(val) < DEADBAND) return 0;
        return Math.copySign((Math.abs(val) - DEADBAND) / (1.0 - DEADBAND), val);
    }

    private void adjustShooterTargetRpm(int delta) {
        if (shooterSubsystem.isBypassEnabled()) {
            shooterSubsystem.adjustTargetRpm(delta);
        } else {
            gamepad2.rumble(0.0, 1.0, 200);
        }
    }

    @Override
    protected void opModeStop() {
        if (robot != null) {
            robot.stopRobot();
        }
    }
}
