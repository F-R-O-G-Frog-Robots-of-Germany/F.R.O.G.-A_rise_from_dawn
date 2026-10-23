package org.firstinspires.ftc.teamcode.core.control;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.limelight.ThreadAffinity;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.teleop.AbstractTeleOp;
import org.firstinspires.ftc.teamcode.opmodes.teleop.comp.Main;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Exercises actual competition runOpMode methods and SDK START/STOP predicates without hardware. */
public final class CompetitionLifecycleChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        teleop_order();
        auto_order();
        auto_optional_planner();
        main_drive_arbitration();
        for (boolean autonomous : new boolean[] { false, true }) {
            init_stop(autonomous);
            init_failure(autonomous);
            loop_and_cleanup_failures(autonomous);
            cleanup_failure_without_primary(autonomous);
        }
        System.out.println("Competition lifecycle checks passed: " + checks);
    }

    private static void teleop_order() throws Exception {
        State state = new State();
        TeleOpHarness opMode = new TeleOpHarness(state);
        opMode.runOpMode();
        ordered(state.events, "affinity-created", "hardware", "init", "workers-created", "bind", "init-loop", "start");
        ordered(state.events, "controls", "before-drive", "localize", "after-drive", "mechanisms", "drive-stop", "mechanisms-stop", "restore");
        check(state.mechanismTicks == 1, "TeleOp important mechanisms run once per control iteration");
        check(state.affinityBindings == 1 && state.restores == 1, "TeleOp binds/restores once");
        check(flag(state.workers, "attempted") && flag(state.workers, "stopping"), "TeleOp owns one worker lifecycle");
        check(!Thread.currentThread().isInterrupted(), "normal TeleOp exit does not introduce interruption");
    }

    private static void auto_order() throws Exception {
        State state = new State();
        AutoHarness opMode = new AutoHarness(state);
        opMode.runOpMode();
        ordered(state.events, "affinity-created", "hardware", "build", "workers-created", "bind", "localize", "mechanisms");
        ordered(state.events, "drive-stop", "shooter-stop", "intake-stop", "flower-up", "restore");
        check(state.mechanismTicks == 1, "autonomous path wait continues ticking the shooter");
        check(state.affinityBindings == 1 && state.restores == 1, "autonomous binds/restores once");
        check(!state.planningRequested, "static autonomous route does not request a planner");
        check(flag(state.workers, "attempted") && flag(state.workers, "stopping"), "autonomous owns one worker lifecycle");
    }

    private static void auto_optional_planner() throws Exception {
        State state = new State();
        state.queueCollection = true;
        state.stopInInit = true;
        try {
            new AutoHarness(state).runOpMode();
            check(state.planningRequested, "an explicitly queued autonomous collection requests the planner during INIT");
            check(state.mechanismTicks == 0, "queued collection cannot run before START");
        } finally { Thread.interrupted(); }
    }

    private static void main_drive_arbitration() throws Exception {
        MainHarness neutral = new MainHarness();
        neutral.claim_collection();
        neutral.before_drive_update();
        check(neutral.owner.owns_drive() && !neutral.manual(), "neutral Main controls preserve collection ownership");

        MainHarness manual = new MainHarness();
        manual.claim_collection();
        set(manual, "driveForward", 0.3);
        manual.before_drive_update();
        check(!manual.owner.owns_drive() && manual.manual(), "driver stick cancels collection before applying manual drive");

        MainHarness aim = new MainHarness();
        aim.claim_collection();
        set(aim, "aimRequested", true);
        aim.before_drive_update();
        check(!aim.owner.owns_drive() && aim.holding(), "explicit hive aim cancels collection before taking Pedro ownership");

        MainHarness reset = new MainHarness();
        reset.claim_collection();
        reset.reset();
        check(!reset.owner.owns_drive(), "pose reset cancels the previous collection before changing coordinates");

        MainHarness invalid = new MainHarness();
        invalid.claim_collection();
        invalid.invalidate_pose();
        invalid.before_drive_update();
        check(!invalid.owner.owns_drive() && invalid.idle_drive(), "invalid pose cancels assistance before stopping Pedro");
    }

    private static void init_stop(boolean autonomous) throws Exception {
        State state = new State();
        state.stopInInit = true;
        LinearOpMode opMode = autonomous ? new AutoHarness(state) : new TeleOpHarness(state);
        try {
            opMode.runOpMode();
            check(state.mechanismTicks == 0, "STOP during INIT prevents active mechanism updates");
            check(!state.events.contains("start"), "STOP during INIT prevents TeleOp on_start");
            check(state.restores == 1 && state.restoreSawInterrupt, "INIT STOP restores affinity while interrupted");
            check(Thread.currentThread().isInterrupted(), "INIT STOP cleanup preserves interruption");
            check(flag(state.workers, "stopping"), "INIT STOP cancels worker ownership");
        } finally { Thread.interrupted(); }
    }

    private static void init_failure(boolean autonomous) throws Exception {
        State state = new State();
        state.failInit = true;
        LinearOpMode opMode = autonomous ? new AutoHarness(state) : new TeleOpHarness(state);
        Throwable failure = run_failure(opMode);
        check(failure == state.primaryFailure, "initialization failure remains the primary exception");
        check(state.affinityBindings == 0 && state.restores == 0, "failed setup never changes affinity");
        check(state.events.contains("drive-stop"), "failed setup still stops initialized drivetrain");
        check(state.events.contains(autonomous ? "shooter-stop" : "mechanisms-stop"), "failed setup still stops initialized mechanisms");
    }

    private static void loop_and_cleanup_failures(boolean autonomous) throws Exception {
        State state = new State();
        state.failLoop = true;
        state.failDriveStop = true;
        state.failMechanismStop = true;
        LinearOpMode opMode = autonomous ? new AutoHarness(state) : new TeleOpHarness(state);
        Throwable failure = run_failure(opMode);
        check(failure == state.primaryFailure, "control failure survives throwing cleanup operations");
        check(failure.getSuppressed().length > 0, "cleanup failures are retained as suppressed diagnostics");
        check(state.events.contains("follower-stop") && state.driveStopCalls >= 1,
                "independent drivetrain stop is attempted after follower.stop throws");
        check(state.restores == 1, "throwing hardware cleanup cannot skip affinity restoration");
        check(flag(state.workers, "stopping"), "work cancellation precedes throwing hardware cleanup");
        check(state.events.get(state.events.size() - 1).equals("restore"), "affinity restoration is the last cleanup operation");
        if (autonomous) {
            ordered(state.events, "shooter-stop", "intake-stop", "flower-up", "restore");
        } else ordered(state.events, "mechanisms-stop", "restore");
    }

    private static void cleanup_failure_without_primary(boolean autonomous) throws Exception {
        State state = new State();
        state.failMechanismStop = true;
        LinearOpMode opMode = autonomous ? new AutoHarness(state) : new TeleOpHarness(state);
        Throwable failure = run_failure(opMode);
        check(failure instanceof IllegalStateException, "ordinary STOP reports a cleanup failure");
        check(failure.getMessage().contains("stop"), "cleanup failure retains its cause");
        check(state.restores == 1, "ordinary STOP cleanup failure still restores affinity");
        if (autonomous) check(state.events.contains("intake-stop") && state.events.contains("flower-up"),
                "later autonomous mechanisms stop after earlier stop failure");
    }

    private static Throwable run_failure(LinearOpMode opMode) {
        try { opMode.runOpMode(); }
        catch (Throwable failure) { return failure; }
        throw new AssertionError("expected failure");
    }

    private static final class State {
        final List<String> events = new ArrayList<>();
        final RuntimeException primaryFailure = new IllegalStateException("primary control failure");
        CompetitionWorkers workers;
        LinearOpMode opMode;
        boolean autonomous, queueCollection, planningRequested, initSampled, cleanupStarted, stopInInit, failInit, failLoop, failDriveStop, failMechanismStop, restoreSawInterrupt;
        int mechanismTicks, driveStopCalls, affinityBindings, restores;

        ControlThreadAffinity affinity() {
            events.add("affinity-created");
            return new ControlThreadAffinity(core -> {
                events.add("bind");
                affinityBindings++;
                check(workers != null && flag(workers, "attempted"), "workers launch before control thread binding");
                check(Thread.currentThread().getName().equals("main"), "actual lifecycle caller receives the affinity request");
                return affinity_result();
            }, result -> {
                events.add("restore");
                restores++;
                restoreSawInterrupt = Thread.currentThread().isInterrupted();
                check(workers == null || flag(workers, "stopping"), "worker cancellation happens before affinity restoration");
                return true;
            });
        }

        CompetitionWorkers workers() {
            events.add("workers-created");
            workers = new CompetitionWorkers(null, null, null, false);
            return workers;
        }

        void init_sample() {
            if (initSampled) return;
            initSampled = true;
            events.add("init-loop");
            if (stopInInit) Thread.currentThread().interrupt();
            else set(opMode, "isStarted", true);
        }

        void mechanism_tick() {
            events.add("mechanisms");
            mechanismTicks++;
            if (failLoop) throw primaryFailure;
            set(opMode, "stopRequested", true);
        }

        void drive_stop() {
            events.add("drive-stop");
            driveStopCalls++;
            if (cleanupStarted && workers != null) check(flag(workers, "stopping"), "worker cancellation precedes motor shutdown");
            if (cleanupStarted && failDriveStop) throw new IllegalStateException("drive stop failed");
        }

        void mechanism_stop(String event) {
            events.add(event);
            if (failMechanismStop) throw new IllegalStateException(event + " stop failed");
        }
    }

    private static final class TeleOpHarness extends AbstractTeleOp {
        final State state;
        final TestLocalizer localizer;
        TeleOpHarness(State state) {
            this.state = state;
            this.localizer = new TestLocalizer(state);
            state.opMode = this;
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            telemetry = no_op_telemetry();
        }
        @Override protected ControlThreadAffinity create_control_affinity() { return state.affinity(); }
        @Override protected CompetitionWorkers create_workers() { return state.workers(); }
        @Override protected int control_core() { return 2; }
        @Override protected void initialize_hardware() {
            state.events.add("hardware");
            set(this, "allHubs", new ArrayList<>());
            follower = new TestFollower(state, localizer);
            currentPose = localizer.pose();
            alliance = Alliance.RED;
        }
        @Override protected void on_init() {
            state.events.add("init");
            if (state.failInit) throw state.primaryFailure;
        }
        @Override protected void on_init_loop() { state.init_sample(); }
        @Override protected void on_start() { state.events.add("start"); }
        @Override protected void update_controls() { state.events.add("controls"); }
        @Override protected void before_drive_update() { state.events.add("before-drive"); }
        @Override protected void after_drive_update() { state.events.add("after-drive"); }
        @Override protected void op_mode_loop() { state.mechanism_tick(); }
        @Override protected void op_mode_stop() { state.mechanism_stop("mechanisms-stop"); }
    }

    private static final class AutoHarness extends AbstractAuto {
        final State state;
        final TestLocalizer localizer;
        AutoHarness(State state) {
            this.state = state;
            state.autonomous = true;
            localizer = new TestLocalizer(state);
            state.opMode = this;
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            telemetry = no_op_telemetry();
        }
        @Override protected ControlThreadAffinity create_control_affinity() { return state.affinity(); }
        @Override protected CompetitionWorkers create_workers() {
            state.planningRequested = planning_enabled();
            return state.workers();
        }
        @Override protected int control_core() { return 2; }
        @Override protected void initialize_hardware() {
            state.events.add("hardware");
            set(this, "allHubs", new ArrayList<>());
            set(this, "alliance", Alliance.RED);
            follower = new TestFollower(state, localizer);
            shooterSubsystem = new Shooter() {
                @Override public void update(double x, double y, boolean valid) { state.mechanism_tick(); }
                @Override public void stop() { state.mechanism_stop("shooter-stop"); }
            };
            intakeSubsystem = new Intake() {
                @Override public void stop() { state.mechanism_stop("intake-stop"); }
            };
            set(this, "flowerIntakeSubsystem", new FlowerIntake() {
                @Override public void up() { state.mechanism_stop("flower-up"); }
            });
        }
        @Override protected Pose starting_pose() { return new Pose(10, 10, 0); }
        @Override protected void build_autonomous() {
            state.events.add("build");
            if (state.failInit) throw state.primaryFailure;
            if (state.queueCollection) {
                queue_collection(new VisionPose(10, 10, 0), new Butine.Params(), 0.5);
                return;
            }
            queue_path(Paths.line(new Pose(10, 10, 0), new Pose(100, 10, 0)).constant(0));
        }
        @Override public void waitForStart() {
            state.init_sample();
            super.waitForStart();
        }
    }

    private static final class MainHarness extends Main {
        final CompetitionWorkers owner;
        MainHarness() {
            State state = new State();
            TestLocalizer localizer = new TestLocalizer(state);
            follower = new Follower(localizer, new TestDrive(state), new Foresight(Constants.foresightConfig));
            currentPose = localizer.pose();
            alliance = Alliance.RED;
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            Robot robot = new Robot();
            Intake intake = new Intake();
            Shooter shooter = new Shooter();
            set(shooter, "intakeSubsystem", intake);
            set(robot, "shooterSubsystem", shooter);
            robot.set_alliance(alliance);
            robot.update_targeting(currentPose);
            set(this, "robot", robot);
            set(this, "shooterSubsystem", shooter);
            set(this, "intakeSubsystem", intake);
            set(this, "flowerIntakeSubsystem", new FlowerIntake());
            owner = new CompetitionWorkers(new Limelight(), null, new PedroButineDrive(follower), true);
            workers = owner;
        }
        void claim_collection() throws Exception {
            Object collection = find_field(owner, "collection").get(owner);
            Field field = find_field(collection, "state");
            for (Object value : field.getType().getEnumConstants()) {
                if (value.toString().equals("PLANNING")) { field.set(collection, value); return; }
            }
            throw new AssertionError("missing PLANNING state");
        }
        @Override protected void before_drive_update() { super.before_drive_update(); }
        void reset() { reset_pose(9, 9); }
        void invalidate_pose() { currentPose = new Pose(Double.NaN, 10, 0); }
        boolean manual() { return follower.manual(); }
        boolean holding() { return follower.holding(); }
        boolean idle_drive() { return follower.idle(); }
    }

    private static final class TestLocalizer implements Localizer {
        final State state;
        MotionState motion = MotionState.zero().withPose(new Pose(10, 10, 0));
        TestLocalizer(State state) { this.state = state; }
        @Override public void setPose(Pose pose) { motion = motion.withPose(pose); }
        @Override public Pose pose() { return motion.pose(); }
        @Override public MotionState state() { return motion; }
        @Override public void update() {
            state.events.add("localize");
            if (state.autonomous && state.affinityBindings > 0 && !state.initSampled) state.init_sample();
        }
        @Override public void reset() { motion = MotionState.zero(); }
    }

    private static final class TestDrive implements Drivetrain {
        final State state;
        TestDrive(State state) { this.state = state; }
        @Override public void drive(DrivePowers powers, boolean manual) { }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { state.drive_stop(); }
        @Override public void stop(boolean manual) { stop(); }
        @Override public double interpolateVelocity(double forward, double strafe, double turn) { return 0; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
    }

    private static final class TestFollower extends Follower {
        final State state;
        TestFollower(State state, Localizer localizer) {
            super(localizer, new TestDrive(state), new Foresight(Constants.foresightConfig));
            this.state = state;
        }
        @Override public void stop() {
            state.cleanupStarted = true;
            state.events.add("follower-stop");
            if (state.failDriveStop) throw new IllegalStateException("follower stop failed");
            super.stop();
        }
    }

    private static Telemetry no_op_telemetry() {
        return (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(), new Class<?>[] { Telemetry.class },
                (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
    }

    private static ThreadAffinity.Result affinity_result() {
        try {
            Constructor<ThreadAffinity.Result> constructor = ThreadAffinity.Result.class.getDeclaredConstructor(
                    boolean.class, int.class, int.class, String.class, String.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(true, 42, 2, "Verified fixture CPU 2", "f", true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static boolean flag(Object object, String name) {
        try { return find_field(object, name).getBoolean(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void set(Object object, String name, Object value) {
        try { find_field(object, name).set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Field find_field(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void ordered(List<String> events, String... expected) {
        int cursor = 0;
        for (String event : events) if (cursor < expected.length && event.equals(expected[cursor])) cursor++;
        check(cursor == expected.length, "expected event order " + Arrays.toString(expected) + ", got " + events);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
