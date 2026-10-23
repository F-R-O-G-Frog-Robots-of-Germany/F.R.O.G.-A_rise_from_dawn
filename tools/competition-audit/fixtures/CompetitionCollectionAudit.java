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
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/**
 * Actual autonomous lifecycle, queue, camera/map, asynchronous planner, runner, Pedro adapter
 * and Foresight. Only camera transport, mechanism hardware and physical movement are simulated.
 * Reflection supplies SDK START and reads the runner; it never sets a worker/runner state.
 */
public final class CompetitionCollectionAudit {
    private static final Pose START = new Pose(20, 20, 0);
    private static final VisionPose DEPOT = new VisionPose(18, 20, 0);
    private static final Pose NEXT = new Pose(16, 50, Math.PI / 2);
    private static int checks;

    public static void main(String[] args) throws Exception {
        // A second complete OpMode catches leaked camera/planner ownership from the first.
        complete_collection_sequence();
        complete_collection_sequence();
        sensor_loaded_returns_without_finishing_collection();
        System.out.println("Competition collection integration checks passed: " + checks);
    }

    private static void sensor_loaded_returns_without_finishing_collection() throws Exception {
        Session session = new Session(true);
        new Harness(session).runOpMode();
        ButineRunner runner = session.runner();
        require(session.sensorLoaded && session.loadedReads > 3,
                "REV loaded signal remains asserted across multiple return updates");
        require(session.loadedPose != null && session.loadedPose.distance(START) > 1e-4
                && session.loadedPose.distance(new Pose(DEPOT.x, DEPOT.y, DEPOT.heading)) > 1e-4,
                "loaded signal interrupts an actual moving collection away from start and depot");
        require(runner.state() == ButineRunner.State.COMPLETE,
                "sensor-full interruption completes its safe depot return");
        require(runner.stats().runsStarted == 1 && runner.stats().runsCompleted == 1,
                "sensor-full collection is one started run and one completed return");
        require(runner.stats().sensorReturns == 1 && session.sensorReturnPaths == 1,
                "held loaded signal replaces the collection path exactly once");
        require(session.collectionPaths == 1 && session.nextPaths == 1,
                "the discarded collection compound is never redispatched and the queue continues once");
        require(runner.stats().collected == 0 && session.workers.map().size() == 1,
                "sensor-full return does not fabricate a pickup or remove the untouched pollen track");
        require(runner.stats().solves == 1 && runner.stats().replans == 0,
                "loaded return does not request another collection solve");
        require(runner.stats().legsTimedOut == 0 && runner.stats().legsNeverBusy == 0
                && runner.stats().legsMissed == 0, "sensor-full return completes without follower failures");
        require(session.pickupTicks >= 2 && session.returnTicks > 2 && session.nextTicks > 2 && session.shots == 1,
                "common loop runs through interrupted collection, safe return and later queued actions");
        require(!session.intaking && !session.flowerDown && !session.shooting,
                "sensor-full completion cleans up intake and mechanisms");
        require(session.shooterStops == 1 && session.intakeStops == 1 && session.driveStops > 0,
                "sensor-full scenario shuts down each hardware owner");
        require(!session.workers.vision().has_owner() && !session.workers.planner().has_owner()
                && !session.workers.owns_drive(), "sensor-full return releases camera, planner and drive ownership");
        require(!session.wrongThread && !session.device.wrongThread,
                "sensor-full return preserves control-thread hardware ownership");
        ordered(session.events, "detector-ready", "start", "flower-down", "intake-forward", "collection-follow",
                "sensor-loaded", "intake-off", "loaded-return-follow", "flower-up", "next-follow",
                "prepare-shot", "shoot", "stop-shooting", "shooter-stop", "intake-stop");
    }

    private static void complete_collection_sequence() throws Exception {
        Session session = new Session();
        Harness opMode = new Harness(session);
        opMode.runOpMode();
        ButineRunner runner = session.runner();
        require(session.planningRequested, "queued collection starts the optional planner during INIT");
        require(session.startedReady, "START follows an actual localized detector frame and mapped target");
        require(runner.state() == ButineRunner.State.COMPLETE, "collection ends COMPLETE, never cancellation or empty-plan fallback");
        require(runner.stats().runsStarted == 1 && runner.stats().runsCompleted == 1,
                "exactly one nonempty collection run starts and returns successfully");
        require(runner.stats().planned == 1 && runner.stats().collected == 1,
                "real planner chooses and real runner removes the reached pollen track");
        require(runner.stats().solves == 1 && runner.stats().replans == 0,
                "the accepted asynchronous result completes without replanning");
        require(runner.stats().legsTimedOut == 0 && runner.stats().legsNeverBusy == 0
                && runner.stats().legsMissed == 0, "both collection legs complete through real Pedro busy/completion signals");
        require(session.collectionPaths == 1 && session.nextPaths == 1,
                "one compound collection run and the next authored action each dispatch once");
        require(session.planningTicks > 0 && session.pickupTicks > 2 && session.returnTicks > 2,
                "shooter/common control loop keeps running during planning, pickup and return");
        require(session.nextTicks > 2 && session.shots == 1,
                "the next path and following shot execute after completed collection");
        require(session.localizerTicks >= session.shooterTicks && session.powerWrites > 0,
                "real follower localizes and drives while the same loop updates mechanisms");
        require(!session.intaking && !session.flowerDown && !session.shooting,
                "normal exit leaves intake, flower and shooting stopped");
        require(session.shooterStops == 1 && session.intakeStops == 1 && session.driveStops > 0,
                "normal autonomous shutdown stops every hardware owner");
        require(!session.workers.vision().has_owner() && !session.workers.planner().has_owner()
                && !session.workers.owns_drive(), "camera/planner owners are fully joined after successful route");
        require(session.device.started == 1 && session.device.stopped == 1,
                "one camera startup and shutdown per actual OpMode");
        require(!session.wrongThread && !session.device.wrongThread,
                "mechanisms and drive stay on the control thread; camera transport stays on its worker");
        ordered(session.events, "detector-ready", "start", "flower-down", "intake-forward", "collection-follow",
                "intake-off", "return-follow", "flower-up", "next-follow", "prepare-shot",
                "shoot", "stop-shooting", "shooter-stop", "intake-stop");
    }

    private static final class Session {
        final Thread controlThread = Thread.currentThread();
        final long deadlineNanos = System.nanoTime() + 5_000_000_000L;
        final List<String> events = new ArrayList<>();
        final Device device = new Device();
        final boolean loadMidRun;
        Harness opMode;
        CompetitionWorkers workers;
        boolean planningRequested, startedReady, intaking, flowerDown, shooting, wrongThread;
        int collectionPaths, nextPaths, localizerTicks, shooterTicks, planningTicks, pickupTicks,
                returnTicks, nextTicks, shots, shooterStops, intakeStops, driveStops, powerWrites;
        int sensorReturnPaths, loadedReads;
        boolean sensorLoaded;
        Pose loadedPose;
        boolean returning;

        Session() { this(false); }
        Session(boolean loadMidRun) { this.loadMidRun = loadMidRun; }

        void on_control_thread() {
            wrongThread |= Thread.currentThread() != controlThread;
            if (System.nanoTime() >= deadlineNanos) throw new AssertionError("integration route exceeded desktop test watchdog: " + events);
        }

        ButineRunner runner() { return (ButineRunner) read(workers, "collection"); }

        void shooter_tick(boolean valid) {
            on_control_thread();
            if (!valid) throw new AssertionError("common loop supplied invalid localization to shooter");
            shooterTicks++;
            if (workers.owns_drive()) {
                if (collectionPaths == 0) planningTicks++;
                else if (!returning) pickupTicks++;
                else returnTicks++;
            } else if (nextPaths > 0) nextTicks++;
        }
    }

    private static final class Harness extends AbstractAuto {
        final Session session;
        final SimulatedLocalizer simulatedLocalizer;

        Harness(Session session) {
            this.session = session;
            session.opMode = this;
            simulatedLocalizer = new SimulatedLocalizer(session);
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
                    new Class<?>[] { Telemetry.class },
                    (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
        }

        @Override protected int control_core() { return -1; }

        @Override protected void initialize_hardware() {
            set(this, "allHubs", new ArrayList<>());
            set(this, "alliance", Alliance.RED);
            follower = new ObservedFollower(session, simulatedLocalizer);
            simulatedLocalizer.follower = follower;
            intakeSubsystem = new Intake() {
                @Override public synchronized boolean loaded() {
                    session.on_control_thread();
                    if (session.loadMidRun && !session.sensorLoaded && session.collectionPaths == 1
                            && session.pickupTicks >= 2 && !session.returning) {
                        require(session.runner().state() == ButineRunner.State.DRIVING
                                && session.runner().stats().collected == 0,
                                "loaded signal precedes the first actual intake contact");
                        session.sensorLoaded = true;
                        session.loadedPose = follower.pose();
                        session.events.add("sensor-loaded");
                    }
                    if (session.sensorLoaded) session.loadedReads++;
                    return session.sensorLoaded;
                }
                @Override public boolean full_load_held() { session.on_control_thread(); return false; }
                @Override public void forward() {
                    session.on_control_thread();
                    require(session.workers.owns_drive() && session.runner().state() == ButineRunner.State.DRIVING,
                            "intake starts only after an executable collection plan is installed");
                    require(!session.sensorLoaded, "intake never restarts after the loaded signal during return");
                    session.events.add("intake-forward");
                    session.intaking = true;
                    super.forward();
                }
                @Override public void off() {
                    session.on_control_thread();
                    session.events.add("intake-off");
                    session.intaking = false;
                    super.off();
                }
                @Override public void stop() {
                    session.on_control_thread(); session.events.add("intake-stop");
                    session.intakeStops++; super.stop();
                }
            };
            set(this, "flowerIntakeSubsystem", new FlowerIntake() {
                @Override public void down() {
                    session.on_control_thread(); session.events.add("flower-down"); session.flowerDown = true;
                }
                @Override public void up() {
                    session.on_control_thread(); session.events.add("flower-up"); session.flowerDown = false; super.up();
                }
            });
            shooterSubsystem = new Shooter() {
                @Override public void update(double x, double y, boolean valid) { session.shooter_tick(valid); }
                @Override public void prepare_shot() {
                    session.on_control_thread();
                    require(follower.pose().distance(NEXT) < 1e-6 && !follower.isBusy(),
                            "shot preparation follows completion of the next authored path");
                    session.events.add("prepare-shot");
                }
                @Override public boolean ready_to_feed() { session.on_control_thread(); return true; }
                @Override public void shoot() {
                    session.on_control_thread(); session.events.add("shoot"); session.shooting = true; session.shots++;
                }
                @Override public void stop_shooting() {
                    session.on_control_thread(); session.events.add("stop-shooting"); session.shooting = false;
                }
                @Override public void stop() {
                    session.on_control_thread(); session.events.add("shooter-stop"); session.shooterStops++; session.shooting = false;
                }
            };
        }

        @Override protected Pose starting_pose() { return START; }

        @Override protected void build_autonomous() {
            Butine.Params params = new Butine.Params();
            params.capacity = 1;
            params.lookaheadRuns = 1;
            params.iterationCap = 0;
            params.localSearchPasses = 0;
            params.remainingS = 8;
            queue_collection(DEPOT, params, 0.6);
            queue_path(Paths.line(new Pose(DEPOT.x, DEPOT.y, DEPOT.heading), NEXT).linear(DEPOT.heading, NEXT.heading()));
            queue_shot(0.005);
        }

        @Override protected CompetitionWorkers create_workers() {
            session.planningRequested = planning_enabled();
            HardwareMap hardwareMap = new HardwareMap();
            hardwareMap.device = session.device;
            Limelight camera = new Limelight(hardwareMap);
            // Frozen frame remains fresh for this bounded test and is mapped once during INIT.
            camera.maxFrameAgeMs = 10_000;
            session.workers = new CompetitionWorkers(camera, null, new PedroButineDrive(follower), planning_enabled());
            session.workers.vision().preferredCore = -1;
            session.workers.planner().preferredCore = -1;
            return session.workers;
        }

        @Override public void waitForStart() {
            require(session.workers.ready(), "SDK START begins with ready real workers");
            session.events.add("start");
            super.waitForStart();
        }
    }

    private static final class SimulatedLocalizer implements Localizer {
        final Session session;
        MotionState motion = MotionState.zero().withPose(START);
        Follower follower;
        PathSegment simulatedSegment;
        int pathTicks;
        SimulatedLocalizer(Session session) { this.session = session; }
        @Override public void setPose(Pose pose) { session.on_control_thread(); motion = motion.withPose(pose); }
        @Override public Pose pose() { return motion.pose(); }
        @Override public MotionState state() { return motion; }
        @Override public void reset() { motion = MotionState.zero().withPose(START); }
        @Override public void update() {
            session.on_control_thread();
            session.localizerTicks++;
            if (!session.startedReady && session.workers != null && session.workers.ready()
                    && session.workers.map().size() == 1) {
                session.startedReady = true;
                session.events.add("detector-ready");
                set(session.opMode, "isStarted", true);
            }
            if (follower != null && follower.following()) {
                PathSegment current = follower.currentSegment();
                // Pedro may clear its last segment one update before entering HOLD.
                if (current == null) return;
                if (current != simulatedSegment) {
                    simulatedSegment = current; pathTicks = 0;
                    if (session.workers.owns_drive() && follower.pathIndex() > 0 && !session.returning) {
                        session.returning = true; session.events.add("return-follow");
                        require(!session.intaking, "intake is off before the return segment starts");
                    }
                }
                // Sample the actual front-mouth sweep, rather than teleporting to the compound endpoint.
                motion = MotionState.zero().withPose(current.get(Math.min(1, ++pathTicks / 12.0)));
            } else if (follower != null && follower.holding()) {
                motion = MotionState.zero().withPose(follower.poseAt(0));
            }
        }
    }

    private static final class ObservedFollower extends Follower {
        final Session session;
        ObservedFollower(Session session, Localizer localizer) {
            super(localizer, new RecordingDrive(session), new Foresight(Constants.foresightConfig));
            this.session = session;
        }
        @Override public void follow(Path path) {
            session.on_control_thread();
            if (session.workers.owns_drive()) {
                require(session.flowerDown, "collection lowers its mechanism before dispatch");
                if (session.collectionPaths == 0) {
                    session.collectionPaths++;
                    session.events.add("collection-follow");
                } else {
                    require(session.loadMidRun && session.sensorLoaded && session.sensorReturnPaths == 0,
                            "only a new loaded signal replaces the original compound collection path");
                    require(!session.intaking && !session.runner().intake_required(),
                            "intake is off before the loaded return begins");
                    require(path.get(0).distance(session.loadedPose) < 1e-6,
                            "replacement return begins at the sensor's live interruption pose");
                    require(!session.runner().legs().isEmpty(), "loaded return contains safe home legs");
                    for (ButineRunner.Leg leg : session.runner().legs()) {
                        require(!leg.intakeActive && leg.pollenId == -1 && leg.field.segment_clear(leg.from, leg.target),
                                "every replacement leg is intake-off and collision-clear without pickup IDs");
                    }
                    session.sensorReturnPaths++;
                    session.returning = true;
                    session.events.add("loaded-return-follow");
                }
                require(path.endPose().distance(new Pose(DEPOT.x, DEPOT.y, DEPOT.heading)) < 1e-6,
                        "compound run actually ends at the requested depot");
            } else {
                require(session.runner().state() == ButineRunner.State.COMPLETE && session.runner().stats().runsCompleted == 1,
                        "next queued path begins only after an actual completed return");
                require(pose().distance(new Pose(DEPOT.x, DEPOT.y, DEPOT.heading)) < 1e-6,
                        "next queued action starts at the depot");
                require(Math.abs(VisionPose.wrap_pi(pose().heading() - DEPOT.heading)) < 1e-6 && !isBusy(),
                        "next action waits for the precise settled depot heading and completed follower");
                require(!session.intaking && !session.flowerDown, "collection cleanup finishes before next queued action");
                session.nextPaths++; session.events.add("next-follow");
            }
            super.follow(path);
        }
    }

    private static final class RecordingDrive implements Drivetrain {
        final Session session;
        RecordingDrive(Session session) { this.session = session; }
        @Override public void drive(DrivePowers powers, boolean manual) { session.on_control_thread(); session.powerWrites++; }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { session.on_control_thread(); session.driveStops++; }
        @Override public void stop(boolean manual) { stop(); }
        @Override public double interpolateVelocity(double forward, double strafe, double turn) { return 0; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
    }

    private static final class Device extends Limelight3A {
        Thread owner;
        volatile boolean wrongThread;
        Device() {
            result = new LLResult();
            result.detectors.add(new LLResultTypes.DetectorResult());
        }
        void check_thread() {
            if (owner == null) owner = Thread.currentThread();
            wrongThread |= owner != Thread.currentThread() || !owner.getName().equals("FROG-Limelight");
        }
        @Override public boolean pipelineSwitch(int index) { check_thread(); return super.pipelineSwitch(index); }
        @Override public void setPollRateHz(int hz) { check_thread(); super.setPollRateHz(hz); }
        @Override public void start() { check_thread(); super.start(); }
        @Override public void stop() { check_thread(); super.stop(); }
        @Override public boolean isConnected() { check_thread(); return super.isConnected(); }
        @Override public LLResult getLatestResult() { check_thread(); return super.getLatestResult(); }
    }

    private static Object read(Object object, String name) {
        try { return field(object, name).get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object object, String name, Object value) {
        try { field(object, name).set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Field field(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void ordered(List<String> events, String... expected) {
        int cursor = 0;
        for (String event : events) if (cursor < expected.length && event.equals(expected[cursor])) cursor++;
        require(cursor == expected.length, "expected sequence " + Arrays.toString(expected) + ", got " + events);
    }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
