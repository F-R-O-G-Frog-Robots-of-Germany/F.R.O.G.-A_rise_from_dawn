package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.bylazar.panels.Panels;
import com.pedropathing.api.Paths;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.LoopTiming;
import org.firstinspires.ftc.teamcode.core.control.ShooterSelection;
import org.firstinspires.ftc.teamcode.core.control.TaskManager;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.pedro.PoseMirroring;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** Single-threaded hardware ownership; routes build all paths and enqueue actions during INIT. */
abstract class General extends LinearOpMode {
    private static final double LIFT_AFTER = 1.0;
    private static final double PATH_TIMEOUT_SECONDS = 8.0;
    private static final double SHOOTER_WAIT_TIMEOUT_SECONDS = 3.0;
    protected Follower follower;
    protected Shooter shooterSubsystem;
    protected Intake intakeSubsystem;
    private FlowerIntake flowerIntakeSubsystem;
    private Alliance alliance;
    private List<LynxModule> allHubs = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final LoopTiming loopTiming = new LoopTiming();
    private final ShooterSelection shooterSelection = new ShooterSelection();

    protected abstract Pose starting_pose();
    protected abstract void build_autonomous();
    protected Alliance autonomous_alliance() { return Alliance.RED; }
    protected final Alliance current_alliance() { return alliance; }

    @Override
    public final void runOpMode() throws InterruptedException {
        try {
            if (CompConfig.panels_enabled()) Panels.INSTANCE.enable();
            else Panels.INSTANCE.disable();
            allHubs = hardwareMap.getAll(LynxModule.class);
            for (LynxModule hub : allHubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
            alliance = autonomous_alliance();
            follower = Constants.create_follower(hardwareMap);
            follower.setPose(starting_pose());
            follower.localizer.update();
            intakeSubsystem = new Intake();
            intakeSubsystem.init(hardwareMap);
            flowerIntakeSubsystem = new FlowerIntake();
            flowerIntakeSubsystem.init(hardwareMap);
            shooterSubsystem = new Shooter();
            shooterSubsystem.init(hardwareMap, intakeSubsystem, alliance);
            actions.clear();
            build_autonomous();
            telemetry.setMsTransmissionInterval(100);
            while (opModeInInit()) {
                shooterSelection.update(gamepad2, shooterSubsystem, telemetry);
                if (CompConfig.init_telemetry_enabled()) {
                    telemetry.addData("Alliance", alliance);
                    telemetry.update();
                }
                idle();
            }
            waitForStart();
            if (isStopRequested()) return;
            for (LynxModule hub : allHubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
            try {
                for (Runnable action : actions) {
                    if (!opModeIsActive()) throw new AutoStopped();
                    action.run();
                }
            } catch (AutoStopped ignored) {
                // Driver Station STOP interrupts the sequence.
            } catch (AutoTimeout timeout) {
                // An unreachable action aborts the route; cleanup below stops every mechanism.
                if (CompConfig.telemetry_enabled()) {
                    telemetry.addData("Auto aborted", timeout.getMessage());
                    telemetry.update();
                }
            }
        } finally {
            if (follower != null) {
                follower.stop();
                follower.drivetrain.stop();
            }
            if (shooterSubsystem != null) shooterSubsystem.stop();
            if (intakeSubsystem != null) intakeSubsystem.stop();
            if (flowerIntakeSubsystem != null) flowerIntakeSubsystem.up();
            TaskManager.stop_all();
            if (follower != null && alliance != null) {
                clear_bulk_cache();
                follower.localizer.update();
                LastPositionStorage.store_data(follower.pose(), alliance);
            }
        }
    }

    /**
     * Enqueue a path built during INIT. Use Pedro's completion signal without an extra
     * pose-settling wait: competition cycle speed takes priority over final arrival accuracy.
     */
    protected final void queue_path(Path path) {
        actions.add(() -> execute_path(path, -1, -1));
    }

    protected final void queue_intake_path(Path path) {
        actions.add(() -> execute_path(path, 0, -1));
    }

    protected final void queue_intake_path(Path path, double lowerFlowerIntakeAfter) {
        require_non_negative(lowerFlowerIntakeAfter, "lowerFlowerIntakeAfter");
        actions.add(() -> execute_path(path, 0, lowerFlowerIntakeAfter));
    }

    protected final void queue_intake_path(Path path, double estimatedPathSeconds, double intakeLastSeconds) {
        require_positive(estimatedPathSeconds, "estimatedPathSeconds");
        require_non_negative(intakeLastSeconds, "intakeLastSeconds");
        double delay = intakeLastSeconds == 0 ? -1 : Math.max(0, estimatedPathSeconds - intakeLastSeconds);
        actions.add(() -> execute_path(path, delay, -1));
    }

    protected final void queue_shoot_path(Path path, double shotDurationSeconds) {
        queue_path(path);
        queue_shot(shotDurationSeconds);
    }

    protected final void queue_shot(double shotDurationSeconds) {
        require_positive(shotDurationSeconds, "shotDurationSeconds");
        actions.add(() -> execute_shot(shotDurationSeconds));
    }

    private void execute_path(Path path, double intakeDelaySeconds, double lowerFlowerAfter) {
        // TODO: Verify pickup success with future Limelight integration; current distance
        // sensors cannot reliably locate missed pollen. Until then, use the planned route.
        boolean[] startedIntake = {false};
        boolean[] lowered = {false};
        boolean[] raised = {false};
        try {
            run_until(() -> !follower.isBusy(), PATH_TIMEOUT_SECONDS, "path",
                    () -> follower.follow(path), elapsed -> {
                        if (!startedIntake[0] && intakeDelaySeconds >= 0 && elapsed >= intakeDelaySeconds) {
                            intakeSubsystem.forward();
                            startedIntake[0] = true;
                        }
                        if (!lowered[0] && lowerFlowerAfter >= 0 && elapsed >= lowerFlowerAfter) {
                            flowerIntakeSubsystem.down();
                            lowered[0] = true;
                        }
                        if (lowered[0] && !raised[0] && elapsed >= lowerFlowerAfter + LIFT_AFTER) {
                            flowerIntakeSubsystem.up();
                            raised[0] = true;
                        }
                    });
        } finally {
            intakeSubsystem.off();
            flowerIntakeSubsystem.up();
        }
    }

    private void execute_shot(double durationSeconds) {
        // TODO: Confirm delivery/hive tilt using future Limelight integration when available.
        try {
            shooterSubsystem.prepare_shot();
            run_until(shooterSubsystem::ready_to_feed, SHOOTER_WAIT_TIMEOUT_SECONDS,
                    "flywheel speed", null, null);
            run_until(() -> false, durationSeconds, "shot duration", shooterSubsystem::shoot, null, true);
        } finally {
            shooterSubsystem.stop_shooting();
            intakeSubsystem.off();
        }
    }

    private void run_until(BooleanSupplier finished, double timeoutSeconds, String action,
                           Runnable onStart, DoubleConsumer beforeUpdate) {
        run_until(finished, timeoutSeconds, action, onStart, beforeUpdate, false);
    }

    private void run_until(BooleanSupplier finished, double timeoutSeconds, String action,
                           Runnable onStart, DoubleConsumer beforeUpdate, boolean durationCompletes) {
        if (!opModeIsActive()) throw new AutoStopped();
        long start = System.nanoTime();
        if (onStart != null) onStart.run();
        while (opModeIsActive()) {
            double elapsed = (System.nanoTime() - start) / 1e9;
            if (elapsed >= timeoutSeconds) {
                if (durationCompletes) return;
                throw new AutoTimeout(action + " exceeded " + timeoutSeconds + " s");
            }
            loopTiming.begin();
            clear_bulk_cache();
            if (beforeUpdate != null) beforeUpdate.accept(elapsed);
            update_follower_and_telemetry();
            if (finished.getAsBoolean()) return;
            idle();
        }
        throw new AutoStopped();
    }

    private void clear_bulk_cache() {
        for (LynxModule hub : allHubs) hub.clearBulkCache();
    }

    private void update_follower_and_telemetry() {
        follower.update();
        Pose pose = follower.pose();
        shooterSubsystem.update(pose.x(), pose.y(), SensorReadings.is_valid(pose));
        loopTiming.end_work();
        if (CompConfig.loop_time_telemetry_enabled()) {
            telemetry.addData("Work ms", loopTiming.work_ms());
            telemetry.addData("Cycle ms", loopTiming.cycle_ms());
            telemetry.addData("Hz", loopTiming.hz());
        }
        if (CompConfig.telemetry_enabled()) {
            telemetry.addData("x", pose.x());
            telemetry.addData("y", pose.y());
            telemetry.addData("heading", pose.heading());
            if (follower.currentPath() != null) {
                telemetry.addData("Path distance remaining", follower.distanceToEndpoint());
                telemetry.addData("Path index", follower.pathIndex());
            }
        }
        if (CompConfig.loop_time_telemetry_enabled() || CompConfig.telemetry_enabled()) telemetry.update();
    }

    private static final class AutoStopped extends RuntimeException { }
    private static final class AutoTimeout extends RuntimeException {
        AutoTimeout(String message) { super(message); }
    }

    private static void require_positive(double value, String name) {
        if (!SensorReadings.is_valid(value) || value <= 0) throw new IllegalArgumentException(name + " must be finite and > 0");
    }

    private static void require_non_negative(double value, String name) {
        if (!SensorReadings.is_valid(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and >= 0");
    }

    protected final Pose field_pose(Pose redPose) {
        return PoseMirroring.mirror_if_blue(redPose, alliance);
    }
}

public abstract class AbstractAuto extends General {
    protected final SoloWithStealPaths solo_with_steal_paths = new SoloWithStealPaths();
    protected final BothFlowersNoGardenPaths both_flowers_no_garden_paths = new BothFlowersNoGardenPaths();

    /** Build pickup/drive curves from corrected RED points, mirroring each once. */
    protected final Path tangent_path(Pose... RED_POINTS) {
        return Paths.curve(field_points(RED_POINTS)).tangent();
    }

    protected final Path constant_heading_path(Pose HEADING, Pose... RED_POINTS) {
        return Paths.curve(field_points(RED_POINTS)).constant(field_pose(HEADING));
    }

    protected final Path shoot_in_upper_hive(Pose pickUp, Pose control1, Pose control2, Pose hive) {
        return shoot_in_hive(pickUp, control1, control2, hive);
    }

    protected final Path shoot_in_upper_hive(Pose pickUp, Pose control1, Pose control2,
                                             Pose control3, Pose hive) {
        return shoot_in_hive(pickUp, control1, control2, control3, hive);
    }

    protected final Path shoot_in_lower_hive(Pose pickUp, Pose control, Pose hive) {
        return shoot_in_hive(pickUp, control, hive);
    }

    protected final Path shoot_in_lower_hive(Pose pickUp, Pose control1, Pose control2, Pose hive) {
        return shoot_in_hive(pickUp, control1, control2, hive);
    }

    private Path shoot_in_hive(Pose... redPoints) {
        Pose[] fieldPoints = field_points(redPoints);
        // Finish facing the hive; the curve tangent need not point at the target.
        return Paths.curve(fieldPoints).linear(fieldPoints[0], fieldPoints[fieldPoints.length - 1]);
    }

    private Pose[] field_points(Pose... redPoints) {
        Pose[] fieldPoints = new Pose[redPoints.length];
        for (int i = 0; i < redPoints.length; i++) {
            fieldPoints[i] = field_pose(redPoints[i]);
        }
        return fieldPoints;
    }

    protected final class BothFlowersNoGardenPaths {
        public Path far_flower_pick_up(Pose START, Pose CONTROL_1, Pose CONTROL_2,
                                      Pose CONTROL_3, Pose PICK_UP) {
            return Paths.curve(field_pose(START), field_pose(CONTROL_1),
                    field_pose(CONTROL_2), field_pose(CONTROL_3), field_pose(PICK_UP)).tangent();
        }

        public Path mid_flower_pick_up(Pose SHOOT, Pose CONTROL_1, Pose CONTROL_2, Pose PICK_UP) {
            return Paths.curve(field_pose(SHOOT), field_pose(CONTROL_1),
                    field_pose(CONTROL_2), field_pose(PICK_UP)).tangent();
        }

        public Path park(Pose SHOOT, Pose CONTROL, Pose PARK) {
            return Paths.curve(field_pose(SHOOT), field_pose(CONTROL),
                    field_pose(PARK)).tangent();
        }
    }

    protected final class SoloWithStealPaths {
        public Path opponent_pick_up(Pose start, Pose control1, Pose control2, Pose pickUp) {
            return Paths.curve(field_pose(start), field_pose(control1),
                    field_pose(control2), field_pose(pickUp)).tangent();
        }

        public Path far_flower_pick_up(Pose shoot, Pose control1, Pose control2, Pose pickUp) {
            return Paths.curve(field_pose(shoot), field_pose(control1),
                    field_pose(control2), field_pose(pickUp)).tangent();
        }

        public Path mid_flower_pick_up(Pose shoot, Pose control1, Pose control2, Pose pickUp) {
            return Paths.curve(field_pose(shoot), field_pose(control1),
                    field_pose(control2), field_pose(pickUp)).tangent();
        }

        public Path intake_garden(Pose shoot, Pose garden) {
            return Paths.line(field_pose(shoot), field_pose(garden)).tangent();
        }

        public Path park(Pose shoot, Pose control, Pose park) {
            return Paths.curve(field_pose(shoot), field_pose(control),
                    field_pose(park)).tangent();
        }
    }
}
