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
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.RobotState;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.opmodes.auto.comp.*;
import org.firstinspires.ftc.teamcode.opmodes.teleop.AbstractTeleOp;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Default competition lifecycle must not construct or service optional camera/planner code. */
public final class ActiveAutonomousIsolationChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!CompConfig.OPTIONAL_ASSISTANCE_ENABLED && !CompConfig.VISION_ENABLED,
                "optional assistance and camera startup are disabled in the competition configuration");
        default_auto_never_enters_optional_hooks();
        default_teleop_never_enters_optional_hooks();
        dormant_queue_calls_have_no_effect();
        active_exports_retain_their_authored_sequences();
        disabled_vision_benches_stay_disabled();
        System.out.println("Active autonomous/TeleOp isolation checks passed: " + checks);
    }

    private static void default_auto_never_enters_optional_hooks() throws Exception {
        Session session = new Session();
        StaticAuto auto = new StaticAuto(session);
        session.opMode = auto;
        auto.runOpMode();
        check(session.initUpdates >= 2 && session.mechanismUpdates == 1,
                "actual static autonomous lifecycle services INIT and an active mechanism loop");
        check(session.workerFactories == 0 && session.workerConfigurations == 0 && value(auto, "workers") == null,
                "default autonomous creates no worker facade, camera or planner");
        check(session.runtimeReads == 0 && session.runtimeResets == 0,
                "dormant assistance adds no SDK match-timer reads or resets to static autonomous");
        check(session.shooterStops == 1 && session.intakeStops == 1 && session.driveStops > 0,
                "ordinary autonomous cleanup remains independent of optional workers");
        check(!flag(auto, "collectionPlanned") && !flag(auto, "parkingPlanned") && !flag(auto, "parkingStarted"),
                "static auto never enables dynamic collection or parking");
    }

    private static void default_teleop_never_enters_optional_hooks() throws Exception {
        Session session = new Session();
        StaticTeleOp teleop = new StaticTeleOp(session);
        session.opMode = teleop;
        teleop.runOpMode();
        check(session.initUpdates >= 2 && session.mechanismUpdates == 1,
                "actual TeleOp lifecycle services INIT and one normal active loop");
        check(session.workerFactories == 0 && session.workerConfigurations == 0 && value(teleop, "workers") == null,
                "default TeleOp creates no worker facade, camera or planner");
        check(session.teleopStopped && session.driveStops > 0,
                "default TeleOp retains its normal mechanism and drivetrain cleanup");
    }

    private static void dormant_queue_calls_have_no_effect() throws Exception {
        DormantCalls auto = new DormantCalls();
        invoke_build(auto);
        check(actions(auto).isEmpty(), "disabled optional queues add no autonomous actions");
        check(!flag(auto, "collectionPlanned") && !flag(auto, "parkingPlanned"),
                "even invalid optional arguments cannot enable dormant planning or parking");
        check(value(auto, "parkingParams") == null && value(auto, "parkingDrive") == null,
                "disabled optional queue calls do not allocate parking parameters or a drive adapter");
    }

    private static void active_exports_retain_their_authored_sequences() throws Exception {
        Class<?>[][] types = {
                {BothFlowersNoGarden.class, BlueBothFlowersNoGarden.class},
                {FarMidPark.class, BlueFarMidPark.class},
                {FarMidGardenPark.class, BlueFarMidGardenPark.class},
                {GardenFarMidPark.class, BlueGardenFarMidPark.class},
                {GardenFarPark.class, BlueGardenFarPark.class},
                {OpponentFarGardenPark.class, BlueOpponentFarGardenPark.class},
                {OpponentFarMidPickupPark.class, BlueOpponentFarMidPickupPark.class},
                {OpponentFarMidGardenPickup.class, BlueOpponentFarMidGardenPickup.class},
                {SoloWithSteal.class, BlueSoloWithSteal.class}
        };
        String[][] endpoints = {
                {"FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "MID_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "PARK"},
                {"FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "MID_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "PARK"},
                {"FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "MID_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "GARDEN_PICK_UP", "SHOOT_IN_UPPER_HIVE", "PARK"},
                {"GARDEN_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "MID_FLOWER_PICK_UP", "SHOOT_IN_LOWER_HIVE", "PARK"},
                {"GARDEN_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "PARK"},
                {"OPPONENT_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "GARDEN_PICK_UP", "SHOOT_IN_LOWER_HIVE", "PARK"},
                {"OPPONENT_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "MID_FLOWER_PICK_UP", "PARK"},
                {"OPPONENT_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "MID_FLOWER_PICK_UP", "SHOOT_IN_LOWER_HIVE", "GARDEN_PICK_UP"},
                {"OPPONENT_PICK_UP", "SHOOT_IN_UPPER_HIVE", "FAR_FLOWER_PICK_UP", "SHOOT_IN_UPPER_HIVE",
                        "MID_FLOWER_PICK_UP", "SHOOT_IN_LOWER_HIVE", "INTAKE_GARDEN", "SHOOT_IN_LOWER_HIVE", "PARK"}
        };
        int[] shots = {3, 3, 4, 4, 3, 4, 3, 4, 5};
        String[][] tailPoints = {
                {"SHOOT_IN_UPPER_HIVE", "PARK_CONTROL1", "PARK"},
                {"SHOOT_IN_UPPER_HIVE", "PARK_CONTROL_1", "PARK"},
                {"SHOOT_IN_UPPER_HIVE", "PARK_CONTROL_1", "PARK"},
                {"SHOOT_IN_LOWER_HIVE", "PARK_CONTROL_1", "PARK_CONTROL_2", "PARK"},
                {"SHOOT_IN_UPPER_HIVE", "PARK_CONTROL_1", "PARK"},
                {"SHOOT_IN_LOWER_HIVE", "PARK_CONTROL_1", "PARK_CONTROL_2", "PARK"},
                {"MID_FLOWER_PICK_UP", "PARK_CONTROL_1", "PARK"},
                null,
                {"SHOOT_IN_LOWER_HIVE", "PARK_CONTROL1", "PARK"}
        };
        Method enabled = General.class.getDeclaredMethod("optional_assistance_enabled");
        enabled.setAccessible(true);
        for (int route = 0; route < types.length; route++) for (int side = 0; side < 2; side++) {
            AbstractAuto auto = (AbstractAuto) types[route][side].getDeclaredConstructor().newInstance();
            Alliance alliance = side == 0 ? Alliance.RED : Alliance.BLUE;
            set(auto, "alliance", alliance);
            check(!(Boolean) enabled.invoke(auto), "active export inherits disabled assistance: " + types[route][side].getSimpleName());
            invoke_build(auto);
            List<Path> paths = captured_paths(actions(auto));
            check(paths.size() == endpoints[route].length
                            && actions(auto).size() == paths.size() + shots[route],
                    "active export retains pickup, shot and authored-parking action counts: " + types[route][side].getSimpleName());
            for (int p = 0; p < paths.size(); p++) {
                Pose expected = field_pose((Pose) value(auto, endpoints[route][p]), alliance);
                check(paths.get(p).endPose().distance(expected) < 1e-8,
                        "authored path order and destination are unchanged: " + types[route][side].getSimpleName() + "/" + p);
            }
            check(!flag(auto, "collectionPlanned") && !flag(auto, "parkingPlanned")
                            && value(auto, "workers") == null && value(auto, "parkingParams") == null,
                    "building an active export does not request optional work");
            if (tailPoints[route] != null) {
                Pose[] points = new Pose[tailPoints[route].length];
                for (int p = 0; p < points.length; p++) points[p] = field_pose((Pose) value(auto, tailPoints[route][p]), alliance);
                Path expected = route == 6 ? Paths.curve(points).constant(field_pose((Pose) value(auto, "PARK"), alliance))
                        : Paths.curve(points).tangent();
                Path actual = paths.get(paths.size() - 1);
                for (double t : new double[] {0, 0.25, 0.5, 0.75, 1}) {
                    Pose a = actual.get(t), b = expected.get(t);
                    check(a.distance(b) < 1e-8 && Math.abs(wrap(a.heading() - b.heading())) < 1e-8,
                            "authored park curve and heading are retained, without dynamic replacement");
                }
            } else {
                check(endpoints[route][endpoints[route].length - 1].equals("GARDEN_PICK_UP"),
                        "garden carry export retains its intentional no-park finish");
            }
        }
    }

    private static void disabled_vision_benches_stay_disabled() throws Exception {
        for (String name : new String[] {"ButineTest", "CpuThreadTest", "LimelightPositionTest", "LimelightNeuralNetworkTrainer"}) {
            Class<?> bench = Class.forName("org.firstinspires.ftc.teamcode.limelight.opmodes." + name);
            check(bench.isAnnotationPresent(Disabled.class), "camera/planner bench stays unavailable in active OpModes: " + name);
        }
    }

    private static List<Path> captured_paths(List<?> actions) throws Exception {
        List<Path> paths = new ArrayList<>();
        for (Object action : actions) for (Field field : action.getClass().getDeclaredFields()) {
            if (Path.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                paths.add((Path) field.get(action));
            }
        }
        return paths;
    }

    private static List<?> actions(Object object) throws Exception { return (List<?>) value(object, "actions"); }

    private static void invoke_build(Object auto) throws Exception {
        for (Class<?> type = auto.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method build = type.getDeclaredMethod("build_autonomous");
                build.setAccessible(true);
                build.invoke(auto);
                return;
            } catch (NoSuchMethodException missing) { }
        }
        throw new AssertionError("missing autonomous build hook");
    }

    private static Pose field_pose(Pose red, Alliance alliance) {
        return alliance == Alliance.BLUE ? new Pose(144 - red.x(), 144 - red.y(), red.heading() + Math.PI) : red;
    }

    private static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }

    private static final class Session {
        com.qualcomm.robotcore.eventloop.opmode.LinearOpMode opMode;
        boolean active, teleopStopped;
        int initUpdates, mechanismUpdates, workerFactories, workerConfigurations;
        int runtimeReads, runtimeResets, driveStops, shooterStops, intakeStops;
    }

    /** Uses a real registered static auto; hardware seams only replace devices. */
    private static final class StaticAuto extends FarMidPark {
        final Session session;
        StaticAuto(Session session) {
            this.session = session;
            gamepad1 = new Gamepad(); gamepad2 = new Gamepad(); telemetry = telemetry();
        }
        @Override protected void initialize_hardware() {
            set(this, "allHubs", new ArrayList<>());
            set(this, "alliance", Alliance.RED);
            follower = follower(session);
            intakeSubsystem = new Intake() {
                @Override public void off() { }
                @Override public void stop() { session.intakeStops++; }
            };
            shooterSubsystem = new Shooter() {
                @Override public void prepare_shot() { }
                @Override public boolean ready_to_feed() { return true; }
                @Override public void update(double x, double y, boolean valid) {
                    check(valid && value_unchecked(StaticAuto.this, "workers") == null,
                            "active static auto mechanisms receive odometry with no worker owner");
                    session.mechanismUpdates++;
                    set(StaticAuto.this, "stopRequested", true);
                }
                @Override public void stop_shooting() { }
                @Override public void stop() { session.shooterStops++; }
            };
            set(this, "flowerIntakeSubsystem", new FlowerIntake() { @Override public void up() { } });
        }
        @Override protected CompetitionWorkers create_workers() {
            session.workerFactories++; throw new AssertionError("active autonomous must not construct optional workers");
        }
        @Override protected void configure_workers(CompetitionWorkers workers) {
            session.workerConfigurations++; throw new AssertionError("active autonomous must not configure optional workers");
        }
        @Override protected Butine.Params parking_parameters() { throw new AssertionError("active auto must not set up Butine parking"); }
        @Override protected Pose parking_pose() { throw new AssertionError("active auto must keep authored parking"); }
        @Override public double getRuntime() {
            if (session == null) return 0;
            session.runtimeReads++; throw new AssertionError("static auto must not use the optional match timer");
        }
        @Override public void resetRuntime() {
            if (session != null) { session.runtimeResets++; throw new AssertionError("static auto must not reset the optional match timer"); }
        }
        @Override public void waitForStart() {
            session.active = true;
            super.waitForStart();
        }
    }

    private static final class DormantCalls extends AbstractAuto {
        @Override protected Pose starting_pose() { return Pose.zero(); }
        @Override protected void build_autonomous() {
            queue_collection(null, null, Double.NaN);
            queue_park();
        }
    }

    private static final class StaticTeleOp extends AbstractTeleOp {
        final Session session;
        StaticTeleOp(Session session) {
            this.session = session;
            gamepad1 = new Gamepad(); gamepad2 = new Gamepad(); telemetry = telemetry();
        }
        @Override protected void initialize_hardware() {
            follower = follower(session);
            currentPose = follower.pose();
            alliance = Alliance.RED; robotState = RobotState.INIT;
        }
        @Override protected void on_init() {
            check(!request_collection(null, null, Double.NaN) && !request_relocalization()
                            && !assistance_owns_drive(),
                    "disabled TeleOp assistance requests have no effect");
        }
        @Override protected void on_start() { session.active = true; }
        @Override protected void op_mode_loop() {
            check(workers == null, "active TeleOp loop has no optional worker owner");
            session.mechanismUpdates++;
            set(this, "stopRequested", true);
        }
        @Override protected void op_mode_stop() { session.teleopStopped = true; }
        @Override protected CompetitionWorkers create_workers() {
            session.workerFactories++; throw new AssertionError("active TeleOp must not construct optional workers");
        }
        @Override protected void configure_workers(CompetitionWorkers workers) {
            session.workerConfigurations++; throw new AssertionError("active TeleOp must not configure optional workers");
        }
        @Override protected void set_limelight_video_enabled(boolean enabled) { throw new AssertionError("active TeleOp must not enable optional video"); }
    }

    private static Follower follower(Session session) {
        Localizer localizer = new Localizer() {
            MotionState motion = MotionState.zero().withPose(new Pose(59.25, 9, Math.PI / 2));
            @Override public void setPose(Pose pose) { motion = motion.withPose(pose); }
            @Override public Pose pose() { return motion.pose(); }
            @Override public MotionState state() { return motion; }
            @Override public void reset() { motion = MotionState.zero(); }
            @Override public void update() {
                if (!session.active && ++session.initUpdates >= 2) set(session.opMode, "isStarted", true);
            }
        };
        Drivetrain drive = new Drivetrain() {
            @Override public void drive(DrivePowers powers, boolean manual) { }
            @Override public double maxScaling(DrivePowers a, DrivePowers b) { return 1; }
            @Override public void stop() { session.driveStops++; }
            @Override public void stop(boolean brake) { stop(); }
            @Override public double interpolateVelocity(double f, double s, double t) { return 0; }
            @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
        };
        return new Follower(localizer, drive, new Foresight(Constants.foresightConfig));
    }

    private static Telemetry telemetry() {
        return (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(), new Class<?>[] {Telemetry.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
    }

    private static boolean flag(Object object, String name) throws Exception { return (Boolean) value(object, name); }

    private static Object value_unchecked(Object object, String name) {
        try { return value(object, name); } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static Object value(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object object, String name, Object value) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException missing) { }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("missing fixture field " + name);
    }

    private static void check(boolean condition, String detail) {
        checks++; if (!condition) throw new AssertionError(detail);
    }
}

