package org.firstinspires.ftc.teamcode.opmodes.teleop.comp;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.follower.ManualDrive;
import com.pedropathing.math.Vector2D;
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
    private boolean aimRequested, aimingAtHive;
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

        robot.update_targeting(currentPose);
        register_controls();
    }

    private void register_controls() {
        inputManager.gamepad1.a.add_button_press_listener(() -> aimRequested = true);
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

        inputManager.gamepad2.dpadRight.add_button_press_listener(() -> reset_teleop_pose(9.0, 9.0));
        inputManager.gamepad2.dpadLeft.add_button_press_listener(() -> reset_teleop_pose(9.0, 133.0));
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
        if (CompConfig.telemetry_enabled()) {
            get_telemetry_manager().addData("Intake", intakeSubsystem.get_intake_mode());
            get_telemetry_manager().addData("Full intake paused", intakeSubsystem.full_load_held());
            get_telemetry_manager().addData("Shot feed", shooterSubsystem.feed_status());
            get_telemetry_manager().addData("Shooter RPM", shooterSubsystem.get_current_rpm());
            get_telemetry_manager().addData("RPM sample valid", shooterSubsystem.velocity_valid());
        }
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
            get_telemetry_manager().addLine("Gamepad 1: tap A to aim at hive; driving sticks cancel");
            get_telemetry_manager().update();
        }
    }

    @Override
    protected void before_drive_update() {
        update_alliance_selection(System.nanoTime());
        if (aimRequested) {
            aimRequested = false;
            aim_at_hive();
        }
        if (driveForward != 0 || driveStrafe != 0 || driveTurn != 0) {
            aimingAtHive = false;
        }
        if (aimingAtHive) return;

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

    private void aim_at_hive() {
        Shooter.HiveDistance hive = shooterSubsystem.get_target_hive();
        if (!hive.valid) return;

        double heading = Vector2D.cartesian(hive.position.x - currentPose.x(), hive.position.y - currentPose.y()).theta();
        follower.hold(currentPose.withHeading(heading));
        aimingAtHive = true;
        if (shooterSubsystem.get_state() != Shooter.State.SHOOT) shooterSubsystem.prepare_shot();
    }

    private void reset_teleop_pose(double x, double y) {
        aimRequested = false;
        aimingAtHive = false;
        reset_pose(x, y);
        robot.update_targeting(currentPose);
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
            aimRequested = false;
            aimingAtHive = false;
            set_alliance(alliance == Alliance.RED ? Alliance.BLUE : Alliance.RED);
            robot.set_alliance(alliance);
            robot.update_targeting(currentPose);
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
