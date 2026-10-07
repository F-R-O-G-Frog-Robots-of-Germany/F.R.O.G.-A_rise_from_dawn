package org.firstinspires.ftc.teamcode.opmodes.teleop.comp;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.follower.ManualDrive;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.RobotState;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.ShooterSelection;
import org.firstinspires.ftc.teamcode.opmodes.teleop.AbstractTeleOp;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

@TeleOp(name = "Main opMode", group = "main")
public class Main extends AbstractTeleOp {
    private Robot robot;
    private Shooter shooterSubsystem;
    private Intake intakeSubsystem;
    private FlowerIntake flowerIntakeSubsystem;
    private double driveForward = 0, driveStrafe = 0, driveTurn = 0;
    private static final double DEADBAND = 0.05;
    private static final long ALLIANCE_HOLD_NANOS = 3_000_000_000L;
    private final ShooterSelection shooterSelection = new ShooterSelection();
    private boolean ignoreConfigUntilReleased;
    private boolean allianceButtonHeld, allianceSwitchConsumed;
    private long allianceHoldStartedNanos;

    @Override
    protected void on_init() {
        robot = new Robot();
        robot.init_robot(hardwareMap, follower, alliance, telemetry, CompConfig.panels_enabled());
        shooterSubsystem = robot.get_shooter();
        intakeSubsystem = robot.get_intake();
        flowerIntakeSubsystem = robot.get_flower_intake();
        set_telemetry_manager(robot.get_telemetry_manager());

        inputManager.gamepad1.rightTrigger.add_press_listener(value -> shooterSubsystem.shoot());
        inputManager.gamepad1.rightTrigger.add_trigger_release_listener(value -> {
            shooterSubsystem.stop_shooting();
            intakeSubsystem.collect();
        });
        inputManager.gamepad1.leftBumper.add_button_press_listener(flowerIntakeSubsystem::toggle);
        inputManager.gamepad1.leftTrigger.add_full_press_listener(value -> intakeSubsystem.toggle_score_mode());
       // reserved for limelight, will force update megatag 2 as well as pollen detection and butine
        // inputManager.gamepad1.rightBumper.add_button_press_listener(Limelight.update);
        inputManager.gamepad1.leftStick.add_update_listener((x, y) -> {
            driveForward = apply_deadband(y);
            driveStrafe = apply_deadband(-x);
        });
        inputManager.gamepad1.rightStick.add_update_listener((x, y) -> {
            driveTurn = apply_deadband(-x);
        });

        inputManager.gamepad2.dpadRight.add_button_press_listener(() -> reset_pose(9.0, 9.0));
        inputManager.gamepad2.dpadLeft.add_button_press_listener(() -> reset_pose(9.0, 133.0));
        inputManager.gamepad2.x.add_button_press_listener(() -> {
            if (!ignoreConfigUntilReleased) shooterSubsystem.toggle_bypass();
        });
        inputManager.gamepad2.leftBumper.add_button_press_listener(() -> adjust_shooter_target_rpm(-100));
        inputManager.gamepad2.rightBumper.add_button_press_listener(() -> adjust_shooter_target_rpm(100));
    }

    @Override
    protected void on_start() {
        ignoreConfigUntilReleased = gamepad2.x || gamepad2.left_bumper || gamepad2.right_bumper;
        robot.start_robot();
    }

    @Override
    protected void op_mode_loop() {
        robot.update_robot(currentPose);
        if (!gamepad2.x && !gamepad2.left_bumper && !gamepad2.right_bumper) {
            ignoreConfigUntilReleased = false;
        }
    }

    @Override
    protected void on_init_loop() {
        update_alliance_selection(System.nanoTime());
        shooterSelection.update(gamepad2, shooterSubsystem, get_telemetry_manager());
        if (CompConfig.init_telemetry_enabled()) {
            get_telemetry_manager().addData("Alliance", alliance);
            get_telemetry_manager().addLine("Gamepad 2: hold B for 3 seconds to switch alliance");
            get_telemetry_manager().update();
        }
    }

    @Override
    protected void before_drive_update() {
        update_alliance_selection(System.nanoTime());
        double heading = currentPose.heading();
        double forward = driveForward * driveSpeed * vetoSpeed;
        double strafe = driveStrafe * driveSpeed * vetoSpeed;
        double turn = driveTurn * driveSpeed * vetoSpeed;

        DrivePowers powers = ManualDrive.fieldCentric(
                forward, strafe, turn, heading,
                alliance == Alliance.BLUE ? Math.PI : 0
        );
        follower.manual(powers);
    }

    /** A continuous hold switches once; release rearms it without delaying the drive loop. */
    private void update_alliance_selection(long nowNanos) {
        if (!gamepad2.b) {
            allianceButtonHeld = false;
            allianceSwitchConsumed = false;
            return;
        }
        if (!allianceButtonHeld) {
            allianceButtonHeld = true;
            allianceHoldStartedNanos = nowNanos;
        }
        if (!allianceSwitchConsumed && nowNanos - allianceHoldStartedNanos >= ALLIANCE_HOLD_NANOS) {
            allianceSwitchConsumed = true;
            set_alliance(alliance == Alliance.RED ? Alliance.BLUE : Alliance.RED);
            robot.set_alliance(alliance);
            gamepad2.rumble(200);
            if (robotState == RobotState.TELEOP) {
                get_telemetry_manager().addData("Alliance", alliance);
                get_telemetry_manager().update();
            }
        }
    }

    private double apply_deadband(double val) {
        if (Math.abs(val) < DEADBAND) return 0;
        return Math.copySign((Math.abs(val) - DEADBAND) / (1.0 - DEADBAND), val);
}

    private void adjust_shooter_target_rpm(int delta) {
        if (ignoreConfigUntilReleased) return;
        if (shooterSubsystem.is_bypass_enabled()) {
            shooterSubsystem.adjust_target_rpm(delta);
        } else {
            gamepad2.rumble(0.0, 1.0, 200);
        }
    }

    @Override
    protected void op_mode_stop() {
        if (robot != null) {
            robot.stop_robot();
        }
    }
}
