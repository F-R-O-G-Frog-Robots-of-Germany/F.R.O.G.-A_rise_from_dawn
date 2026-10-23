package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.bylazar.panels.Panels;
import com.pedropathing.api.Paths;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;
import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.core.control.ControlThreadAffinity;
import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.LoopTiming;
import org.firstinspires.ftc.teamcode.core.control.OpModeCleanup;
import org.firstinspires.ftc.teamcode.core.control.ShooterSelection;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.pedro.PoseMirroring;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;
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
    protected CompetitionWorkers workers;
    private ControlThreadAffinity controlAffinity;
    private boolean collectionPlanned;
    private boolean collectingDynamic, collectionIntakeOn;
    private long autoStartedNanos;
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
        Throwable failure = null;
        try {
            controlAffinity = create_control_affinity();
            initialize_hardware();
            actions.clear();
            collectionPlanned = false;
            build_autonomous();
            workers = create_workers();
            configure_workers(workers);
            workers.start();
            controlAffinity.bind(control_core());
            telemetry.setMsTransmissionInterval(100);
            while (opModeInInit()) {
                follower.localizer.update();
                workers.update();
                shooterSelection.update(gamepad2, shooterSubsystem, telemetry);
                if (CompConfig.init_telemetry_enabled()) {
                    telemetry.addData("Alliance", alliance);
                    telemetry.addData("Control affinity", controlAffinity.detail());
                    workers.telemetry(telemetry);
                    telemetry.update();
                }
                idle();
            }
            waitForStart();
            if (isStopRequested()) return;
            for (LynxModule hub : allHubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
            autoStartedNanos = System.nanoTime();
            try {
                for (Runnable action : actions) {
                    if (!opModeIsActive()) throw new AutoStopped();
                    action.run();
                }
            } catch (AutoStopped ignored) {
                // Driver Station STOP or invalid localization aborts the sequence.
            } catch (AutoTimeout timeout) {
                // An unreachable action aborts the route; cleanup below stops every mechanism.
                if (CompConfig.telemetry_enabled()) {
                    telemetry.addData("Auto aborted", timeout.getMessage());
                    telemetry.update();
                }
            }
        } catch (RuntimeException | Error stopped) {
            failure = stopped;
            throw stopped;
        } finally {
            OpModeCleanup.run(failure,
                    () -> { if (workers != null) workers.request_stop(); },
                    () -> { if (follower != null) follower.stop(); },
                    () -> { if (follower != null) follower.drivetrain.stop(); },
                    () -> { if (shooterSubsystem != null) shooterSubsystem.stop(); },
                    () -> { if (intakeSubsystem != null) intakeSubsystem.stop(); },
                    () -> { if (flowerIntakeSubsystem != null) flowerIntakeSubsystem.up(); },
                    () -> {
                        if (follower != null && alliance != null) {
                            clear_bulk_cache();
                            follower.localizer.update();
                            LastPositionStorage.store_data(follower.pose(), alliance);
                        }
                    },
                    () -> { if (workers != null && !workers.stop())
                        RobotLog.ww("Competition", "Worker still finishing after bounded shutdown"); },
                    () -> { if (controlAffinity != null && !controlAffinity.restore())
                        RobotLog.ww("Competition", controlAffinity.detail()); });
        }
    }

    protected void initialize_hardware() {
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
    }

    protected boolean vision_enabled() { return CompConfig.VISION_ENABLED; }
    protected boolean planning_enabled() { return collectionPlanned; }
    protected int control_core() { return CompConfig.CONTROL_CORE; }
    protected ControlThreadAffinity create_control_affinity() { return new ControlThreadAffinity(); }
    protected CompetitionWorkers create_workers() {
        return new CompetitionWorkers(hardwareMap, follower, vision_enabled(), planning_enabled());
    }
    /** Configure camera calibration, map and optional worker cores before their owners start. */
    protected void configure_workers(CompetitionWorkers workers) { }

    /** Depot coordinates are already in the same real field frame as camera observations. */
    protected final void queue_collection(VisionPose depot, Butine.Params params, double speedScale) {
        if (depot == null || !depot.is_finite() || params == null)
            throw new IllegalArgumentException("finite depot and planner parameters are required");
        require_positive(speedScale, "speedScale");
        if (speedScale > 1) throw new IllegalArgumentException("speedScale must be <= 1");
        Butine.Params settings = new Butine.Params(params);
        collectionPlanned = true;
        actions.add(() -> {
            Butine.Params remaining = new Butine.Params(settings);
            remaining.remainingS = Math.max(0, settings.remainingS - (System.nanoTime() - autoStartedNanos) / 1e9);
            if (remaining.remainingS <= 0) return;
            follower.stop();
            follower.drivetrain.stop();
            if (!workers.request_collection(depot, remaining, speedScale)) {
                RobotLog.ww("Competition", workers.detail());
                return;
            }
            Throwable collectionFailure = null;
            try {
                collectingDynamic = true;
                collectionIntakeOn = false;
                intakeSubsystem.off();
                flowerIntakeSubsystem.down();
                run_until(() -> !workers.owns_drive(), remaining.remainingS, "collection match allowance", null, null);
            } catch (RuntimeException | Error stopped) {
                collectionFailure = stopped;
                throw stopped;
            } finally {
                collectingDynamic = false;
                collectionIntakeOn = false;
                OpModeCleanup.run(collectionFailure, workers::cancel_drive,
                        intakeSubsystem::off, flowerIntakeSubsystem::up);
            }
        });
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

    protected final void queue_shoot_path(Path path, double shotDurationSeconds) {
        queue_path(path);
        queue_shot(shotDurationSeconds);
    }

    protected final void queue_shot(double shotDurationSeconds) {
        require_positive(shotDurationSeconds, "shotDurationSeconds");
        actions.add(() -> execute_shot(shotDurationSeconds));
    }

    private void execute_path(Path path, double intakeDelaySeconds, double lowerFlowerAfter) {
        if (workers != null) workers.cancel_drive();
        // TODO: Verify pickup success with future Limelight integration; current distance
        // sensors cannot reliably locate missed pollen. Until then, use the planned route.
        boolean[] startedIntake = {false};
        boolean[] lowered = {false};
        boolean[] raised = {false};
        Throwable failure = null;
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
        } catch (RuntimeException | Error stopped) {
            failure = stopped;
            throw stopped;
        } finally {
            OpModeCleanup.run(failure, intakeSubsystem::off, flowerIntakeSubsystem::up);
        }
    }

    private void execute_shot(double durationSeconds) {
        if (workers != null) workers.cancel_drive();
        // TODO: Confirm delivery/hive tilt using future Limelight integration when available.
        Throwable failure = null;
        try {
            shooterSubsystem.prepare_shot();
            run_until(shooterSubsystem::ready_to_feed, SHOOTER_WAIT_TIMEOUT_SECONDS,
                    "flywheel speed", null, null);
            run_until(() -> false, durationSeconds, "shot duration", shooterSubsystem::shoot, null, true);
        } catch (RuntimeException | Error stopped) {
            failure = stopped;
            throw stopped;
        } finally {
            OpModeCleanup.run(failure, shooterSubsystem::stop_shooting, intakeSubsystem::off);
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
        if (collectingDynamic && (intakeSubsystem.full_load_held() || intakeSubsystem.loaded())) {
            if (collectionIntakeOn) {
                intakeSubsystem.off();
                collectionIntakeOn = false;
            }
            workers.return_to_depot_if_loaded(true);
        }
        if (workers != null) workers.update();
        if (collectingDynamic) {
            boolean requested = workers.collection_intake_required();
            if (requested != collectionIntakeOn) {
                if (requested) intakeSubsystem.forward(); else intakeSubsystem.off();
                collectionIntakeOn = requested;
            }
        }
        Pose pose = follower.pose();
        boolean poseValid = SensorReadings.is_valid(pose);
        if (!poseValid) {
            follower.stop();
            follower.drivetrain.stop();
            throw new AutoStopped();
        }
        shooterSubsystem.update(pose.x(), pose.y(), poseValid);
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
    /** Build pickup/drive curves from corrected RED points, mirroring each once. */
    protected final Path tangent_path(Pose... RED_POINTS) {
        return Paths.curve(field_points(RED_POINTS)).tangent();
    }

    protected final Path tangent_line_path(Pose redStart, Pose redEnd) {
        return Paths.line(field_pose(redStart), field_pose(redEnd)).tangent();
    }

    protected final Path constant_heading_path(Pose HEADING, Pose... RED_POINTS) {
        return Paths.curve(field_points(RED_POINTS)).constant(field_pose(HEADING));
    }

    protected final Path shoot_in_hive(Pose... redPoints) {
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

}
