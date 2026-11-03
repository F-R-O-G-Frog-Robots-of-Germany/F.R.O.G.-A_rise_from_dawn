package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.bylazar.panels.Panels;
import com.pedropathing.algorithm.Foresight;
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
import org.firstinspires.ftc.teamcode.limelight.ButineField;
import org.firstinspires.ftc.teamcode.limelight.ButineIntake;
import org.firstinspires.ftc.teamcode.limelight.ButineParking;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
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
    private static final double AUTONOMOUS_SECONDS = 30.0;
    private static final double PARK_RESERVE_SECONDS = 1.0;
    private static final double PARK_SPEED_SCALE = 0.5;
    protected Follower follower;
    protected Shooter shooterSubsystem;
    protected Intake intakeSubsystem;
    protected CompetitionWorkers workers;
    private ControlThreadAffinity controlAffinity;
    private boolean collectionPlanned;
    private boolean collectingDynamic, collectionIntakeOn;
    private boolean parkingPlanned, parkingStarted;
    private Butine.Params parkingParams;
    private VisionPose parkingTarget;
    private ButineParking.Zone parkingZone;
    private PedroButineDrive parkingDrive;
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
            workers = null;
            collectionPlanned = false;
            collectingDynamic = collectionIntakeOn = false;
            parkingPlanned = parkingStarted = false;
            parkingDrive = null;
            parkingParams = null;
            build_autonomous();
            // Retained for later bench opt-in. Competition autos do not construct these services.
            if (optional_assistance_enabled()) {
                if (parkingPlanned) configure_parking();
                workers = create_workers();
                workers.set_auto_timer(this::getRuntime);
                configure_workers(workers);
                workers.start();
            }
            controlAffinity.bind(control_core());
            telemetry.setMsTransmissionInterval(100);
            while (opModeInInit()) {
                follower.localizer.update();
                if (workers != null) workers.update();
                shooterSelection.update(gamepad2, shooterSubsystem, telemetry);
                if (CompConfig.init_telemetry_enabled()) {
                    telemetry.addData("Alliance", alliance);
                    telemetry.addData("Control affinity", controlAffinity.detail());
                    if (workers != null) workers.telemetry(telemetry);
                    telemetry.update();
                }
                idle();
            }
            waitForStart();
            if (isStopRequested()) return;
            if (optional_assistance_enabled()) resetRuntime();
            for (LynxModule hub : allHubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
            try {
                execute_actions();
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
                    () -> { if (parkingDrive != null) parkingDrive.stop(); },
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

    protected boolean optional_assistance_enabled() { return CompConfig.OPTIONAL_ASSISTANCE_ENABLED; }
    protected boolean vision_enabled() { return CompConfig.VISION_ENABLED; }
    protected boolean planning_enabled() { return collectionPlanned; }
    protected int control_core() { return CompConfig.CONTROL_CORE; }
    protected ControlThreadAffinity create_control_affinity() { return new ControlThreadAffinity(); }
    protected CompetitionWorkers create_workers() {
        return new CompetitionWorkers(hardwareMap, follower, vision_enabled(), planning_enabled());
    }
    /** Configure camera calibration, map and optional worker cores before their owners start. */
    protected void configure_workers(CompetitionWorkers workers) { }

    /** Override this pose in actual field coordinates to agree on parking space with a partner. */
    protected Pose parking_pose() {
        ButineField field = parkingParams == null ? parking_parameters().field : parkingParams.field;
        VisionPose target = ButineParking.default_target(parking_zone(), field);
        if (target == null) throw new IllegalArgumentException("No default park fits the configured robot envelope");
        return new Pose(target.x, target.y, target.heading);
    }

    /** Nominal geometry and seed motion values until the robot exists. */
    protected Butine.Params parking_parameters() { return new Butine.Params(); }

    private ButineParking.Zone parking_zone() {
        return alliance == Alliance.BLUE ? ButineParking.blue_zone() : ButineParking.red_zone();
    }

    /** One terminal action; the approach is calculated from the live pose, never an authored origin. */
    protected final void queue_park() {
        if (!optional_assistance_enabled()) return;
        if (parkingPlanned) throw new IllegalStateException("Only one terminal park may be queued");
        parkingPlanned = true;
        actions.add(this::execute_park);
    }

    private void configure_parking() {
        parkingParams = new Butine.Params(parking_parameters());
        parkingParams.kinematics.cruiseSpeedInS *= PARK_SPEED_SCALE;
        parkingZone = parking_zone();
        Pose target = parking_pose();
        if (!SensorReadings.is_valid(target)) throw new IllegalArgumentException("Finite parking pose required");
        parkingTarget = new VisionPose(target.x(), target.y(), target.heading());
        if (!ButineParking.is_parked(parkingTarget, parkingZone, parkingParams.field, 0))
            throw new IllegalArgumentException("Parking pose must clear obstacles and overlap our loading zone");
        parkingDrive = new PedroButineDrive(follower);
    }

    private void execute_actions() {
        if (!optional_assistance_enabled()) {
            for (Runnable action : actions) {
                if (!opModeIsActive()) throw new AutoStopped();
                action.run();
            }
            return;
        }
        try {
            for (Runnable action : actions) {
                if (!opModeIsActive()) throw new AutoStopped();
                check_parking_due();
                action.run();
                if (parkingStarted) break;
            }
        } catch (AutoParkDue due) {
            execute_park();
        } catch (AutoCollectionFallback fallback) {
            if (CompConfig.telemetry_enabled()) {
                telemetry.addData("Collection fallback", fallback.getMessage());
                telemetry.update();
            }
            if (!parkingPlanned) throw new AutoStopped();
            execute_park(true);
        }
    }

    private ButineIntake.Route parking_route(VisionPose from) {
        return ButineParking.route(from, parkingTarget, parkingParams);
    }

    private ButineParking.Approach parking_approach(VisionPose from) {
        return ButineParking.approach(from, parkingTarget, parkingParams);
    }

    private boolean parking_arrived(VisionPose here) {
        double speed = parkingDrive.speed_in_s();
        return here != null && here.is_finite() && here.distance_to(parkingTarget) <= 0.5
                && Math.abs(VisionPose.wrap_pi(here.heading - parkingTarget.heading)) <= Math.toRadians(2)
                && Double.isFinite(speed) && speed <= 1.0
                && ButineParking.is_parked(here, parkingZone, parkingParams.field, 0);
    }

    private void check_parking_due() {
        if (getRuntime() >= AUTONOMOUS_SECONDS) throw new AutoTimeout("autonomous deadline reached");
        if (!parkingPlanned) return;
        if (parkingStarted || parkingDrive == null) return;
        VisionPose here = parkingDrive.pose();
        ButineParking.Approach approach = parking_approach(here);
        if (approach != null && AUTONOMOUS_SECONDS - getRuntime() <= approach.timeS + PARK_RESERVE_SECONDS)
            throw new AutoParkDue();
    }

    private void execute_park() {
        execute_park(false);
    }

    private void execute_park(boolean immediate) {
        if (parkingStarted) return;
        if (!opModeIsActive()) throw new AutoStopped();
        if (getRuntime() >= AUTONOMOUS_SECONDS) throw new AutoTimeout("autonomous deadline reached");
        parkingStarted = true;
        if (workers != null) workers.cancel_drive(ButineRunner.Reason.TERMINAL_PARK_PREEMPTION);
        follower.stop();
        follower.drivetrain.stop();
        shooterSubsystem.stop_shooting();
        intakeSubsystem.off();
        flowerIntakeSubsystem.up();
        ButineParking.Approach approach;
        while (opModeIsActive()) {
            check_parking_due();
            VisionPose here = parkingDrive.pose();
            if (parking_arrived(here)) return;
            approach = parking_approach(here);
            if (approach == null) throw new AutoTimeout("no checked live-position approach to park");
            double remaining = AUTONOMOUS_SECONDS - getRuntime();
            if (approach.timeS > remaining) throw new AutoTimeout("not enough autonomous time for checked park");
            if (immediate || remaining <= approach.timeS + PARK_RESERVE_SECONDS) break;
            loopTiming.begin();
            clear_bulk_cache();
            update_follower_and_telemetry();
            idle();
        }
        if (!opModeIsActive()) throw new AutoStopped();
        VisionPose from = parkingDrive.pose();
        approach = parking_approach(from);
        if (approach == null) throw new AutoTimeout("parking origin changed to a blocked pose");
        if (approach.timeS > AUTONOMOUS_SECONDS - getRuntime())
            throw new AutoTimeout("not enough autonomous time for checked park");
        if (approach.egress != null) execute_parking_egress(approach.egress);
        // Re-route from measured arrival after recovery; never assume the planned exit was reached.
        from = parkingDrive.pose();
        ButineIntake.Route route = parking_route(from);
        if (route == null) throw new AutoTimeout("parking origin changed to a blocked pose");
        double remaining = AUTONOMOUS_SECONDS - getRuntime();
        if (remaining <= 0) throw new AutoTimeout("autonomous time expired before parking");
        if (route.timeS > remaining) throw new AutoTimeout("not enough autonomous time after parking exit");
        Butine.Run terminal = new Butine.Run(new int[0], new VisionPose[0], route.timeS, false, route);
        List<ButineRunner.Leg> legs = ButineRunner.build_run_legs(terminal, from, parkingTarget,
                parkingParams.kinematics, parkingParams.field);
        run_until(() -> !parkingDrive.is_busy() && parking_arrived(parkingDrive.pose()), remaining, "terminal park",
                () -> parkingDrive.follow_run(legs, PARK_SPEED_SCALE), null);
    }

    private void execute_parking_egress(ButineParking.Egress egress) {
        VisionPose live = parkingDrive.pose();
        if (!ButineParking.egress_clear(live, egress.target, parkingParams.field)
                || !(follower.algorithm() instanceof Foresight))
            throw new AutoTimeout("parking exit is no longer valid from the live pose");
        Pose start = new Pose(live.x, live.y, live.heading);
        Pose target = new Pose(egress.target.x, egress.target.y, live.heading);
        Foresight foresight = (Foresight) follower.algorithm();
        double limit = Math.min(foresight.config.maxVelocityConstraint.get(), parkingParams.kinematics.cruiseSpeedInS);
        if (!Double.isFinite(limit) || limit <= 0) throw new AutoTimeout("invalid parking exit velocity");
        Path exit = Paths.line(start, target).constant(start.heading()).with(
                foresight.config.maxVelocityConstraint.at(limit),
                foresight.config.pathSkip.at(false),
                foresight.config.brakeAtEnd.at(true),
                foresight.config.parametricTConstraint.at(1 - 1e-6));
        boolean holdEnd = follower.holdEnd.get();
        follower.holdEnd.set(true);
        Throwable failure = null;
        try {
            run_until(() -> !follower.isBusy() && parkingParams.field.is_clear(parkingDrive.pose())
                            && parkingDrive.pose().distance_to(egress.target) <= 0.25
                            && parkingDrive.speed_in_s() <= 1.0,
                    Math.max(0, AUTONOMOUS_SECONDS - getRuntime()), "parking exit",
                    () -> follower.follow(exit), null);
        } catch (RuntimeException | Error stopped) {
            failure = stopped;
            throw stopped;
        } finally {
            OpModeCleanup.run(failure, follower::stop, follower.drivetrain::stop,
                    () -> follower.holdEnd.set(holdEnd));
        }
    }

    /** Depot coordinates are already in the same real field frame as camera observations. */
    protected final void queue_collection(VisionPose depot, Butine.Params params, double speedScale) {
        if (!optional_assistance_enabled()) return;
        if (depot == null || !depot.is_finite() || params == null)
            throw new IllegalArgumentException("finite depot and planner parameters are required");
        require_positive(speedScale, "speedScale");
        if (speedScale > 1) throw new IllegalArgumentException("speedScale must be <= 1");
        Butine.Params settings = new Butine.Params(params);
        collectionPlanned = true;
        actions.add(() -> {
            Butine.Params remaining = new Butine.Params(settings);
            remaining.elapsedAutoS = getRuntime();
            remaining.remainingS = Math.max(0, settings.remainingS - remaining.elapsedAutoS);
            if (parkingPlanned) {
                ButineIntake.Route tail = parking_route(depot);
                if (tail == null) {
                    long id = workers.decline_collection(depot, ButineRunner.Outcome.NO_FEASIBLE_RUN,
                            ButineRunner.Reason.PARK_UNREACHABLE, "collection depot has no route to park");
                    resolve_collection(id);
                    return;
                }
                remaining.remainingS = Math.min(remaining.remainingS, Math.max(0,
                        AUTONOMOUS_SECONDS - remaining.elapsedAutoS - tail.timeS - PARK_RESERVE_SECONDS));
            }
            if (remaining.remainingS <= 0) {
                long id = workers.decline_collection(depot, ButineRunner.Outcome.TIMED_OUT,
                        ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED, "no collection allowance remains");
                resolve_collection(id);
                return;
            }
            boolean accepted = workers.request_collection(depot, remaining, speedScale);
            long attempt = workers.collection_attempt_id();
            if (!accepted) {
                resolve_collection(attempt);
                return;
            }
            Throwable collectionFailure = null;
            try {
                collectingDynamic = true;
                collectionIntakeOn = false;
                intakeSubsystem.off();
                flowerIntakeSubsystem.down();
                run_until(() -> workers.collection_result(attempt) != null, remaining.remainingS,
                        "collection match allowance", null, null);
            } catch (AutoParkDue due) {
                collectionFailure = due;
                workers.finish_collection(ButineRunner.Outcome.CANCELLED,
                        ButineRunner.Reason.TERMINAL_PARK_PREEMPTION, "collection yielded to terminal parking");
                throw due;
            } catch (AutoTimeout timeout) {
                collectionFailure = timeout;
                boolean deadline = getRuntime() >= AUTONOMOUS_SECONDS;
                workers.finish_collection(ButineRunner.Outcome.TIMED_OUT,
                        deadline ? ButineRunner.Reason.AUTONOMOUS_DEADLINE : ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED,
                        timeout.getMessage());
                if (deadline) throw timeout;
                collectionFailure = null; // The handled timeout must not hide a subsequent cleanup failure.
            } catch (AutoStopped stopped) {
                collectionFailure = stopped;
                workers.finish_collection(ButineRunner.Outcome.CANCELLED,
                        ButineRunner.Reason.STOP_REQUESTED, "collection stopped with autonomous");
                throw stopped;
            } catch (RuntimeException | Error stopped) {
                collectionFailure = stopped;
                workers.finish_collection(ButineRunner.Outcome.ABORTED,
                        ButineRunner.Reason.SERVICE_FAILURE, "collection failed: " + stopped);
                throw stopped;
            } finally {
                collectingDynamic = false;
                collectionIntakeOn = false;
                OpModeCleanup.run(collectionFailure, workers::cancel_drive,
                        intakeSubsystem::off, flowerIntakeSubsystem::up);
            }
            resolve_collection(attempt);
        });
    }

    /** Only this attempt's checked depot arrival can authorize the queued continuation. */
    private void resolve_collection(long attempt) {
        ButineRunner.Result result = workers.collection_result(attempt);
        if (result == null) throw new AutoStopped();
        if (!opModeIsActive()) throw new AutoStopped();
        if (getRuntime() >= AUTONOMOUS_SECONDS) throw new AutoTimeout("autonomous deadline reached");
        if (result.success()) return;
        switch (result.reason) {
            case INVALID_POSE:
            case MANUAL_OVERRIDE:
            case DRIVE_BUSY:
            case POSE_RESET:
            case STOP_REQUESTED:
            case AUTONOMOUS_DEADLINE:
                throw new AutoStopped();
            default:
                throw new AutoCollectionFallback(result.outcome + ": " + result.detail);
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
        if (optional_assistance_enabled()) check_parking_due();
        long start = System.nanoTime();
        if (onStart != null) onStart.run();
        while (opModeIsActive()) {
            if (optional_assistance_enabled()) check_parking_due();
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
    private static final class AutoParkDue extends RuntimeException { }
    private static final class AutoCollectionFallback extends RuntimeException {
        AutoCollectionFallback(String message) { super(message); }
    }
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
