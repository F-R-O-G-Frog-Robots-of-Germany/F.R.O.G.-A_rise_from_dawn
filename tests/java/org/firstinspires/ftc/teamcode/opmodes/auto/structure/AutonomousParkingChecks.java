package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.PathSegment;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineField;
import org.firstinspires.ftc.teamcode.limelight.ButineParking;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Real autonomous queue and Pedro/Foresight; only clock, mechanisms and physical motion are simulated. */
public final class AutonomousParkingChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        waits_then_parks_once();
        active_path_is_preempted_from_live_pose();
        blue_and_manual_targets_are_actual_field_poses();
        an_existing_overlap_does_not_bypass_the_partner_target();
        driver_stop_while_waiting_and_parking();
        stalled_parking_stops_at_the_sdk_deadline();
        authored_wall_and_carry_endpoints_can_park();
        blocked_parking_never_dispatches_a_shortcut();
        no_queue_preserves_authored_auto();
        failed_collection_parks_immediately_without_dependent_actions();
        failed_collection_stops_without_a_usable_park();
        System.out.println("Autonomous parking integration checks passed: " + checks);
    }

    private static void waits_then_parks_once() throws Exception {
        Session session = run(Scenario.TERMINAL);
        require(session.parkPaths == 1 && session.authoredPaths == 0,
                "terminal parking dispatches one complete route exactly once");
        require(session.parkAt >= 25 && session.parkAt < 30,
                "an early terminal action waits on the SDK autonomous timer until near the end");
        require(session.waitTicks > 80 && session.parkTicks > 0,
                "common localization and mechanism loops run both while waiting and parking");
        require(session.resetCalls == 1 && session.runtimeReads > session.waitTicks,
                "one SDK runtime reset is the source of all parking deadline reads");
        require(session.prepares == 0 && session.shots == 0,
                "actions queued after the terminal park never run");
        at_target(session, new Pose(19, 108, 0));
        safe_cleanup(session);
    }

    private static void active_path_is_preempted_from_live_pose() throws Exception {
        Session session = run(Scenario.PREEMPT);
        require(session.authoredPaths == 1 && session.parkPaths == 1,
                "one running authored path is replaced by one parking compound");
        require(session.interrupted != null && session.activeStops == 1,
                "parking stops the still-active collection/drive path once");
        require(session.interrupted.distance(session.start) > 10
                        && session.interrupted.distance(session.authored.endPose()) > 10,
                "interruption occurs in motion, away from both authored endpoints");
        require(session.parkStart.distance(session.interrupted) < 1e-6,
                "parking path starts at the live interruption pose, never its authored depot");
        require(session.parkSegments > 1,
                "live-origin parking routes around the frame instead of crossing the direct blocked segment");
        require(session.parkAt >= 25 && session.parkAt < 30,
                "parking preempts the active action using the SDK remaining time");
        require(session.prepares == 0 && session.shots == 0,
                "the queued shot is skipped after active-path parking preemption");
        at_target(session, new Pose(19, 108, 0));
        safe_cleanup(session);
    }

    private static void blue_and_manual_targets_are_actual_field_poses() throws Exception {
        Session blue = run(Scenario.BLUE);
        require(blue.parkPaths == 1, "Blue inheritance selects one parking route");
        at_target(blue, new Pose(125, 36, Math.PI));
        require(blue.parkEnd.x() > 120 && blue.parkEnd.y() < 48,
                "Blue uses F2, not the Red zone or a simulator corner");
        safe_cleanup(blue);
        Session manual = run(Scenario.MANUAL);
        at_target(manual, manual.manualTarget);
        require(manual.parkEnd.distance(new Pose(125, 36, Math.PI)) > 5,
                "manual partner-coordination override replaces the calculated default");
        require(manual.parkEnd.x() > 120, "manual actual-field override is not mirrored a second time");
        safe_cleanup(manual);
    }

    private static void blocked_parking_never_dispatches_a_shortcut() throws Exception {
        Session blocked = run(Scenario.BLOCKED);
        require(blocked.parkPaths == 0 && blocked.authoredPaths == 0,
                "an unreachable loading zone never dispatches a direct fallback path");
        require(blocked.localizer.pose().distance(blocked.start) < 1e-6,
                "unreachable parking leaves the robot at its live safe position");
        safe_cleanup(blocked);
    }

    private static void an_existing_overlap_does_not_bypass_the_partner_target() throws Exception {
        Session session = run(Scenario.ALREADY_IN_ZONE);
        require(session.parkPaths == 1, "an existing zone overlap does not ignore the agreed partner target");
        at_target(session, session.manualTarget);
        safe_cleanup(session);
    }

    private static void driver_stop_while_waiting_and_parking() throws Exception {
        Session waiting = run(Scenario.STOP_WAIT);
        require(waiting.sdkSeconds < 6 && waiting.parkPaths == 0,
                "Driver Station STOP while waiting cannot dispatch a later parking route");
        require(waiting.prepares == 0 && waiting.shots == 0, "STOP cannot fall through to a queued shot");
        safe_cleanup(waiting);
        Session parking = run(Scenario.STOP_PARK);
        require(parking.parkPaths == 1 && parking.localizer.pose().distance(parking.parkEnd) > 1,
                "Driver Station STOP interrupts parking before arrival without dispatching another path");
        require(parking.prepares == 0 && parking.shots == 0, "parking STOP cannot run a dependent shot");
        safe_cleanup(parking);
        Session exiting = run(Scenario.STOP_EGRESS);
        require(exiting.parkPaths == 1 && !exiting.field.is_clear(new VisionPose(exiting.localizer.pose().x(),
                        exiting.localizer.pose().y(), exiting.localizer.pose().heading())),
                "Driver Station STOP during constrained exit does not dispatch the ordinary parking route");
        require(exiting.prepares == 0 && exiting.shots == 0,
                "STOP during the exit preserves a carried load without scoring");
        safe_cleanup(exiting);
    }

    private static void stalled_parking_stops_at_the_sdk_deadline() throws Exception {
        Session stalled = run(Scenario.STALLED);
        require(stalled.parkPaths == 1 && stalled.localizer.pose().distance(stalled.parkEnd) > 10,
                "stalled physical feedback never becomes a successful parking arrival");
        require(stalled.sdkSeconds >= 30 && stalled.sdkSeconds <= 30.11,
                "the SDK autonomous deadline stops a stalled park without waiting on a second match clock");
        require(stalled.prepares == 0 && stalled.shots == 0, "expired parking cannot authorize later actions");
        safe_cleanup(stalled);
    }

    private static void authored_wall_and_carry_endpoints_can_park() throws Exception {
        for (Scenario scenario : new Scenario[] { Scenario.WALL_LOWER, Scenario.WALL_UPPER,
                Scenario.WALL_BLUE, Scenario.CARRY, Scenario.GARDEN }) {
            Session session = run(scenario);
            require(session.parkPaths == 2,
                    "a nominal authored endpoint uses one fixed-heading exit and one parking route: " + scenario);
            require(session.firstParkStart.distance(session.start) < 1e-6,
                    "controlled exit begins at the actual authored endpoint: " + scenario);
            require(session.egressEnd != null
                            && session.field.is_clear(new VisionPose(session.egressEnd.x(), session.egressEnd.y(),
                            session.egressEnd.heading())),
                    "controlled exit reaches ordinary collision-clear routing space: " + scenario);
            require(session.firstParkAt >= 20 && session.firstParkAt < 30,
                    "exit and parking reserve are scheduled together near autonomous end: " + scenario);
            require(session.prepares == 0 && session.shots == 0,
                    "parking preserves a carry load without adding a shot: " + scenario);
            at_target(session, session.alliance == Alliance.BLUE ? new Pose(125, 36, Math.PI)
                    : new Pose(19, 108, 0));
            safe_cleanup(session);
        }
    }

    private static void no_queue_preserves_authored_auto() throws Exception {
        Session normal = run(Scenario.NO_PARK);
        require(normal.authoredPaths == 1 && normal.parkPaths == 0,
                "an OpMode without queue_park never invents a parking action");
        require(normal.localizer.pose().distance(normal.authored.endPose()) < 1e-6,
                "existing authored path completes normally without parking opt-in");
        require(normal.sdkSeconds < 5 && normal.resetCalls == 1,
                "unopted auto does not wait near thirty seconds");
        safe_cleanup(normal);
    }

    private enum Scenario { TERMINAL, PREEMPT, BLUE, MANUAL, ALREADY_IN_ZONE,
        STOP_WAIT, STOP_PARK, STOP_EGRESS, STALLED, WALL_LOWER, WALL_UPPER, WALL_BLUE, CARRY, GARDEN,
        BLOCKED, NO_PARK, REFUSED_COLLECTION, EXPIRED_COLLECTION, BAD_DEPOT, BLOCKED_FALLBACK,
        NO_PARK_FALLBACK, TOO_LATE_FALLBACK }

    private static boolean collection_scenario(Scenario scenario) {
        return scenario.ordinal() >= Scenario.REFUSED_COLLECTION.ordinal();
    }

    private static void failed_collection_parks_immediately_without_dependent_actions() throws Exception {
        for (Scenario scenario : new Scenario[] { Scenario.REFUSED_COLLECTION, Scenario.EXPIRED_COLLECTION,
                Scenario.BAD_DEPOT }) {
            Session session = run(scenario);
            require(session.parkPaths == 1 && session.firstParkAt < 1,
                    "collection failure starts one checked park immediately: " + scenario);
            require(session.authoredPaths == 0 && session.prepares == 0 && session.shots == 0,
                    "collection failure skips all dependent path and shooting actions: " + scenario);
            ButineRunner.Result result = session.opMode.workers.collection_result();
            require(result != null && !result.success(), "cleanup preserves the failed attempt: " + scenario);
            require(result.outcome == (scenario == Scenario.REFUSED_COLLECTION ? ButineRunner.Outcome.REFUSED
                            : scenario == Scenario.EXPIRED_COLLECTION ? ButineRunner.Outcome.TIMED_OUT
                            : ButineRunner.Outcome.NO_FEASIBLE_RUN),
                    "preflight and request failures keep distinct typed outcomes: " + scenario);
            at_target(session, new Pose(19, 108, 0));
            safe_cleanup(session);
        }
    }

    private static void failed_collection_stops_without_a_usable_park() throws Exception {
        for (Scenario scenario : new Scenario[] { Scenario.BLOCKED_FALLBACK, Scenario.NO_PARK_FALLBACK,
                Scenario.TOO_LATE_FALLBACK }) {
            Session session = run(scenario);
            require(session.parkPaths == 0 && session.authoredPaths == 0
                            && session.prepares == 0 && session.shots == 0,
                    "failed collection cannot invent an unchecked, unqueued or late park: " + scenario);
            safe_cleanup(session);
        }
    }

    private static Session run(Scenario scenario) throws Exception {
        Session session = new Session(scenario);
        new Harness(session).runOpMode();
        return session;
    }

    private static final class Session {
        final Scenario scenario;
        final Alliance alliance;
        final Pose start;
        final Pose manualTarget;
        final ButineField field;
        final long watchdog = System.nanoTime() + 10_000_000_000L;
        Harness opMode;
        SimulatedLocalizer localizer;
        ObservedFollower follower;
        Path authored;
        Pose interrupted, parkStart, parkEnd, firstParkStart, egressEnd;
        double sdkSeconds = 83, parkAt = Double.NaN, firstParkAt = Double.NaN;
        int resetCalls, runtimeReads, authoredPaths, parkPaths, parkSegments, activeStops;
        int localizerTicks, shooterTicks, waitTicks, parkTicks, prepares, shots;
        int shooterStops, intakeStops, driveStops, flowerUps;
        boolean started, intaking, shooting, flowerDown, originalHoldEnd;

        Session(Scenario scenario) {
            this.scenario = scenario;
            alliance = scenario == Scenario.BLUE || scenario == Scenario.MANUAL || scenario == Scenario.WALL_BLUE
                    ? Alliance.BLUE : Alliance.RED;
            switch (scenario) {
                case ALREADY_IN_ZONE: start = new Pose(19, 100, 0); break;
                case WALL_LOWER: case STOP_EGRESS: start = OptimalPoses.SHOOT_IN_LOWER_HIVE; break;
                case WALL_UPPER: start = OptimalPoses.SHOOT_IN_UPPER_HIVE; break;
                case WALL_BLUE: start = new Pose(144 - OptimalPoses.SHOOT_IN_LOWER_HIVE.x(),
                        144 - OptimalPoses.SHOOT_IN_LOWER_HIVE.y(),
                        OptimalPoses.SHOOT_IN_LOWER_HIVE.heading() + Math.PI); break;
                case CARRY: start = OptimalPoses.OpponentFarMidPickupParkRoute.MID_FLOWER_PICK_UP; break;
                case GARDEN: start = OptimalPoses.OpponentFarMidGardenPickupRoute.GARDEN_PICK_UP; break;
                default: start = alliance == Alliance.BLUE ? new Pose(72, 36, Math.PI) : new Pose(72, 108, 0);
            }
            manualTarget = scenario == Scenario.ALREADY_IN_ZONE ? new Pose(19, 115, 0)
                    : new Pose(126, 44, Math.PI);
            field = scenario == Scenario.BLOCKED || scenario == Scenario.BLOCKED_FALLBACK
                    ? new ButineField(144, 18, 18, 1, new ButineField.Box(40, 50, 0, 144))
                    : ButineField.biobuzz();
        }

        void tick() {
            if (System.nanoTime() > watchdog || sdkSeconds > 40 && started)
                throw new AssertionError("parking test exceeded watchdog: " + scenario + ", runtime=" + sdkSeconds
                        + ", authored=" + authoredPaths + ", parking=" + parkPaths);
            if ((scenario == Scenario.STOP_WAIT && started && sdkSeconds >= 5)
                    || ((scenario == Scenario.STOP_PARK || scenario == Scenario.STOP_EGRESS) && parkTicks >= 1))
                set(opMode, "stopRequested", true);
        }
    }

    private static final class Harness extends AbstractAuto {
        @Override protected boolean optional_assistance_enabled() { return true; }
        final Session session;
        Harness(Session session) {
            this.session = session;
            session.opMode = this;
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
                    new Class<?>[] { Telemetry.class },
                    (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
            set(this, "isStarted", true);
        }

        @Override public double getRuntime() {
            if (session == null) return 0;
            session.runtimeReads++;
            return session.sdkSeconds;
        }

        @Override public void resetRuntime() {
            if (session != null) {
                session.resetCalls++;
                session.sdkSeconds = 0;
                if (session.scenario == Scenario.TOO_LATE_FALLBACK) session.sdkSeconds = 29.99;
                session.started = true;
            }
        }

        @Override protected int control_core() { return -1; }
        @Override protected Alliance autonomous_alliance() { return session.alliance; }
        @Override protected Pose starting_pose() { return session.start; }
        @Override protected CompetitionWorkers create_workers() {
            return new CompetitionWorkers(null, null, null, false);
        }

        @Override protected Butine.Params parking_parameters() {
            Butine.Params params = new Butine.Params();
            params.field = session.field;
            return params;
        }

        @Override protected Pose parking_pose() {
            return session.scenario == Scenario.MANUAL || session.scenario == Scenario.ALREADY_IN_ZONE
                    ? session.manualTarget : super.parking_pose();
        }

        @Override protected void initialize_hardware() {
            set(this, "allHubs", new ArrayList<>());
            set(this, "alliance", session.alliance);
            session.localizer = new SimulatedLocalizer(session);
            session.follower = new ObservedFollower(session, session.localizer);
            follower = session.follower;
            intakeSubsystem = new Intake() {
                @Override public void off() { session.intaking = false; }
                @Override public void forward() { session.intaking = true; }
                @Override public void stop() { session.intakeStops++; session.intaking = false; }
            };
            shooterSubsystem = new Shooter() {
                @Override public void update(double x, double y, boolean valid) {
                    session.tick();
                    require(valid, "common mechanism update receives valid localization");
                    session.shooterTicks++;
                    if (session.parkPaths > 0) session.parkTicks++;
                    else if (session.authoredPaths == 0) session.waitTicks++;
                }
                @Override public void prepare_shot() { session.prepares++; }
                @Override public boolean ready_to_feed() { return true; }
                @Override public void shoot() { session.shots++; session.shooting = true; }
                @Override public void stop_shooting() { session.shooting = false; }
                @Override public void stop() { session.shooterStops++; session.shooting = false; }
            };
            set(this, "flowerIntakeSubsystem", new FlowerIntake() {
                @Override public void up() { session.flowerUps++; session.flowerDown = false; }
                @Override public void down() { session.flowerDown = true; }
            });
        }

        @Override protected void build_autonomous() {
            if (collection_scenario(session.scenario)) {
                Butine.Params params = new Butine.Params();
                if (session.scenario == Scenario.EXPIRED_COLLECTION) params.remainingS = 0;
                VisionPose depot = session.scenario == Scenario.BAD_DEPOT ? new VisionPose(0, 0, 0)
                        : new VisionPose(session.start.x(), session.start.y(), session.start.heading());
                queue_collection(depot, params, 0.6);
                session.authored = Paths.line(session.start, new Pose(72, 112, 0)).constant(0);
                queue_path(session.authored);
                queue_shot(0.001);
                if (session.scenario != Scenario.NO_PARK_FALLBACK) queue_park();
                return;
            }
            if (session.scenario == Scenario.PREEMPT || session.scenario == Scenario.NO_PARK) {
                Pose end = session.scenario == Scenario.PREEMPT ? new Pose(72, 36, 0) : new Pose(72, 112, 0);
                session.authored = Paths.line(session.start, end).constant(session.start.heading());
                queue_path(session.authored);
                if (session.scenario == Scenario.PREEMPT) queue_shot(0.001);
            }
            if (session.scenario != Scenario.NO_PARK) {
                queue_park();
                queue_shot(0.001); // Terminal means this action cannot run after parking.
            }
        }
    }

    private static final class SimulatedLocalizer implements Localizer {
        final Session session;
        MotionState motion;
        PathSegment segment;
        int segmentTicks;
        SimulatedLocalizer(Session session) {
            this.session = session;
            motion = MotionState.zero().withPose(session.start);
        }
        @Override public void setPose(Pose pose) { motion = motion.withPose(pose); }
        @Override public Pose pose() { return motion.pose(); }
        @Override public MotionState state() { return motion; }
        @Override public void reset() { motion = MotionState.zero().withPose(session.start); }
        @Override public void update() {
            session.tick();
            session.localizerTicks++;
            if (session.started) session.sdkSeconds += session.parkPaths > 0 ? 0.1 : 0.25;
            if (session.scenario == Scenario.STALLED && session.parkPaths > 0) return;
            Follower follower = session.follower;
            if (follower == null) return;
            if (follower.following()) {
                PathSegment current = follower.currentSegment();
                if (current == null) return;
                if (current != segment) { segment = current; segmentTicks = 0; }
                double fraction = session.scenario == Scenario.PREEMPT && session.parkPaths == 0
                        ? 0.5 : Math.min(1, ++segmentTicks
                        / (session.scenario == Scenario.STOP_PARK || session.scenario == Scenario.STOP_EGRESS ? 20.0 : 2.0));
                motion = MotionState.zero().withPose(current.get(fraction));
            } else if (follower.holding()) {
                motion = MotionState.zero().withPose(follower.poseAt(0));
            }
        }
    }

    private static final class ObservedFollower extends Follower {
        final Session session;
        ObservedFollower(Session session, Localizer localizer) {
            super(localizer, new SimulatedDrive(session), new Foresight(Constants.foresightConfig));
            this.session = session;
            if (session.scenario != Scenario.NO_PARK) holdEnd.set(false);
            session.originalHoldEnd = holdEnd.get();
        }
        @Override public void follow(Path path) {
            if (path == session.authored) session.authoredPaths++;
            else {
                session.parkPaths++;
                session.parkAt = session.sdkSeconds;
                session.parkStart = path.get(0);
                session.parkEnd = path.endPose();
                if (session.parkPaths == 1) {
                    session.firstParkStart = session.parkStart;
                    session.firstParkAt = session.parkAt;
                    session.egressEnd = session.parkEnd;
                    if (session.scenario == Scenario.WALL_LOWER || session.scenario == Scenario.WALL_UPPER
                            || session.scenario == Scenario.WALL_BLUE || session.scenario == Scenario.CARRY
                            || session.scenario == Scenario.GARDEN) {
                        for (double t : new double[] { 0, 0.5, 1 }) {
                            require(Math.abs(VisionPose.wrap_pi(path.get(t).heading() - session.start.heading())) < 1e-8,
                                    "the constrained exit preserves the original physical body heading");
                        }
                    }
                }
                session.parkSegments = path.getSegments().size();
                require(session.parkStart.distance(pose()) < 1e-6,
                        "Pedro parking dispatch begins at its live localized pose");
                require(!session.intaking && !session.shooting && !session.flowerDown,
                        "parking turns off intake/shooting and raises flower intake before driving");
            }
            super.follow(path);
        }
        @Override public void stop() {
            if (session.scenario == Scenario.PREEMPT && session.parkPaths == 0 && following()) {
                session.activeStops++;
                session.interrupted = pose();
            }
            super.stop();
        }
    }

    private static final class SimulatedDrive implements Drivetrain {
        final Session session;
        SimulatedDrive(Session session) { this.session = session; }
        @Override public void drive(DrivePowers powers, boolean manual) { session.tick(); }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { session.driveStops++; }
        @Override public void stop(boolean manual) { stop(); }
        @Override public double interpolateVelocity(double forward, double strafe, double turn) { return 0; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
    }

    private static void at_target(Session session, Pose target) {
        require(session.parkEnd != null && session.parkEnd.distance(target) < 1e-6,
                "parking compound ends at the selected target");
        require(session.localizer.pose().distance(target) < 1e-6,
                "real follower lifecycle reaches the target in simulated motion");
        Pose finalPose = session.localizer.pose();
        require(ButineParking.is_parked(new VisionPose(finalPose.x(), finalPose.y(), finalPose.heading()),
                        session.alliance == Alliance.BLUE ? ButineParking.blue_zone() : ButineParking.red_zone(),
                        session.field, 0), "final physical body has positive loading-zone overlap");
        require(session.sdkSeconds < 30, "simulated parking completes before the SDK thirty-second deadline");
    }

    private static void safe_cleanup(Session session) {
        require(session.shooterStops == 1 && session.intakeStops == 1 && session.driveStops > 0
                        && session.flowerUps > 0, "autonomous finally stops every hardware owner");
        require(!session.intaking && !session.shooting && !session.flowerDown,
                "autonomous cleanup leaves mechanisms safe");
        require(session.localizerTicks >= session.shooterTicks,
                "mechanisms are serviced after localization in the common loop");
        require(session.follower.holdEnd.get() == session.originalHoldEnd,
                "parking restores the follower's original final-hold setting after completion, STOP or timeout");
    }

    private static void set(Object object, String name, Object value) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException missing) { }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("missing SDK/harness field " + name);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
