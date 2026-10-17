package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import static org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.*;

/** Real Pedro follower and benchmark mechanisms with simulated hardware; normal control parity. */
public final class PerformanceRegularControlChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        driving_axes_and_aim();
        trigger_edges_and_order();
        settings_edges_and_order();
        pause_and_resume();
        invalid_pose_stops();
        System.out.println("Passed " + checks + " regular control checks");
    }

    private static void driving_axes_and_aim() throws Exception {
        for (Alliance alliance : new Alliance[]{Alliance.RED, Alliance.BLUE}) {
            Rig rig = new Rig(alliance);
            double sign = alliance == Alliance.RED ? 1 : -1;
            rig.one.left_stick_y = -.6f;
            rig.tick();
            check(rig.power("forward") * sign > 0, "pushing forward has Main's inverted raw-Y sign");
            same(rig.power("strafe"), 0, "forward stick has no strafe");
            rig.one.left_stick_y = .6f;
            rig.tick();
            check(rig.power("forward") * sign < 0, "pulling back reverses forward command");
            rig.one.left_stick_y = 0;
            rig.one.left_stick_x = .6f;
            rig.tick();
            check(rig.power("strafe") * sign < 0, "strafe sign matches Main");
            rig.one.left_stick_x = 0;
            rig.one.right_stick_x = .6f;
            rig.tick();
            check(rig.power("turn") < 0, "turn sign matches Main for either alliance");
            rig.one.right_stick_x = .02f;
            rig.one.left_stick_y = -.02f;
            rig.one.left_stick_x = .02f;
            rig.tick();
            same(rig.power("forward"), 0, "forward deadband");
            same(rig.power("strafe"), 0, "strafe deadband");
            same(rig.power("turn"), 0, "turn deadband");
            rig.one.a = true;
            rig.tick();
            check((boolean) get(rig.robot, "aimingAtHive"), "A enters hive hold");
            check(!rig.intake.is_shooting_requested(), "aim alone never requests feed");
            rig.one.a = false;
            rig.tick();
            check((boolean) get(rig.robot, "aimingAtHive"), "releasing A preserves hold");
            rig.one.left_stick_y = -.2f;
            rig.tick();
            check(!(boolean) get(rig.robot, "aimingAtHive"), "driver movement cancels hive hold");
            check(rig.power("forward") * sign > 0, "movement applies on the cancellation tick");
        }
    }

    private static void trigger_edges_and_order() throws Exception {
        Rig rig = new Rig(Alliance.RED);
        rig.one.right_trigger = .04f;
        rig.tick();
        check(!rig.intake.is_shooting_requested(), "RT below press threshold does not shoot");
        rig.one.right_trigger = .1f;
        rig.tick();
        check(rig.intake.is_shooting_requested(), "RT crossing threshold requests shot");
        rig.tick();
        check(rig.intake.is_shooting_requested(), "holding RT retains shooting request");
        rig.one.right_trigger = .04f;
        rig.tick();
        check(!rig.intake.is_shooting_requested(), "RT release cancels shooting");
        check(!rig.intake.is_scoring_requested(), "RT release restores collect mode");
        same(rig.intake.get_intake_power(), 1, "RT release resumes collection");
        rig.one.left_trigger = .94f;
        rig.tick();
        check(!rig.intake.is_scoring_requested(), "LT partial press does not toggle score");
        rig.one.left_trigger = .96f;
        rig.tick();
        check(rig.intake.is_scoring_requested(), "LT full threshold toggles score");
        rig.tick();
        check(rig.intake.is_scoring_requested(), "held LT toggles once");
        rig.one.left_trigger = .5f;
        rig.tick();
        rig.one.left_trigger = 1;
        rig.tick();
        check(rig.intake.is_scoring_requested(), "partial LT release does not rearm full-press edge");
        rig.one.left_trigger = 0;
        rig.tick();
        rig.one.left_trigger = 1;
        rig.tick();
        check(!rig.intake.is_scoring_requested(), "complete LT release rearms next toggle");
        rig.one.left_trigger = 0;
        rig.one.right_trigger = 1;
        rig.tick();
        rig.one.left_trigger = 1;
        rig.one.right_trigger = 0;
        rig.tick();
        check(!rig.intake.is_scoring_requested(), "simultaneous LT press and RT release: collect wins as in Main");
        check(!rig.intake.is_shooting_requested(), "simultaneous transition stops shot");
        same(rig.intake.get_intake_power(), 1, "simultaneous transition collects");
    }

    private static void settings_edges_and_order() throws Exception {
        Rig rig = new Rig(Alliance.RED);
        rig.tick(); // Main's START guard first sees released configuration controls.
        check(!rig.shooter.is_bypass_enabled(), "distance mode default");
        rig.two.x = rig.two.right_bumper = true;
        rig.tick();
        check(rig.shooter.is_bypass_enabled(), "X enables fixed targets");
        same(rig.shooter.get_fixed_rpm(), 3000, "G2 bumper precedes X, so previous distance mode prevents RPM edit");
        rig.tick();
        same(rig.shooter.get_fixed_rpm(), 3000, "held settings buttons do not repeat");
        rig.two.x = rig.two.right_bumper = false;
        rig.tick();
        rig.two.left_bumper = true;
        rig.tick();
        same(rig.shooter.get_fixed_rpm(), 2900, "left bumper lowers fixed target");
        rig.two.left_bumper = false;
        rig.tick();
        rig.two.x = rig.two.right_bumper = true;
        rig.tick();
        check(!rig.shooter.is_bypass_enabled(), "second X edge restores distance targets");
        same(rig.shooter.get_fixed_rpm(), 3000, "bumper edits prior fixed mode before X toggles it off");
        rig.two.x = rig.two.right_bumper = false;
        rig.two.dpad_right = true;
        rig.tick();
        same(rig.localizer.pose().x(), 9, "G2 right pose reset x");
        same(rig.localizer.pose().y(), 9, "G2 right pose reset y");
        rig.two.dpad_right = false;
        rig.two.dpad_left = true;
        rig.tick();
        same(rig.localizer.pose().x(), 9, "G2 left pose reset x");
        same(rig.localizer.pose().y(), 133, "G2 left pose reset y");

        Rig init = new Rig(Alliance.RED);
        init.two.x = init.two.right_bumper = init.two.dpad_up = true;
        init.robot.init_loop(init.one, init.two);
        check(init.shooter.is_bypass_enabled(), "INIT X toggles fixed mode");
        same(init.shooter.get_fixed_rpm(), 3100, "INIT selection order applies RPM to newly enabled fixed mode");
        same(init.shooter.get_fixed_hood_angle(), 46, "INIT hood up adjustment");
        init.robot.init_loop(init.one, init.two);
        same(init.shooter.get_fixed_rpm(), 3100, "INIT held settings do not repeat");
        init.tick();
        same(init.shooter.get_fixed_rpm(), 3100, "START held settings guard preserves INIT targets");
        check(init.shooter.is_bypass_enabled(), "START held X cannot toggle selection again");
    }

    private static void pause_and_resume() throws Exception {
        Rig rig = new Rig(Alliance.RED);
        rig.one.left_stick_y = -.6f;
        rig.one.right_trigger = 1;
        rig.tick();
        rig.robot.pause();
        check(!rig.robot.controls_ready(), "pause requires neutral controls");
        same(rig.left.power, 0, "pause stops flywheel");
        same(rig.intake.get_intake_power(), 0, "pause stops intake");
        check(!rig.intake.is_shooting_requested(), "pause stops shooting request");
        for (int i = 0; i < 4; i++) {
            rig.tick();
            check(!rig.robot.controls_ready(), "held controls cannot rearm after pause");
            check(!rig.intake.is_shooting_requested(), "held trigger cannot restart shot after pause");
            same(rig.left.power, 0, "held controls retain stopped flywheel");
            same(rig.power("forward"), 0, "held controls retain stopped drivetrain");
        }
        rig.one.left_stick_y = 0;
        rig.one.right_trigger = 0;
        rig.tick();
        check(rig.robot.controls_ready(), "neutral controls rearm operation");
        check(!rig.intake.is_shooting_requested(), "neutral rearm does not synthesize trigger press");
        rig.one.right_trigger = 1;
        rig.tick();
        check(rig.intake.is_shooting_requested(), "fresh trigger edge shoots after neutral rearm");
    }

    private static void invalid_pose_stops() throws Exception {
        for (Pose invalid : new Pose[]{new Pose(Double.NaN, 9, 0), new Pose(9, 9, Double.POSITIVE_INFINITY)}) {
            Rig rig = new Rig(Alliance.RED);
            rig.one.left_stick_y = -.6f;
            rig.tick();
            check(rig.power("forward") > 0, "moves before localization fault");
            rig.localizer.setPose(invalid);
            set(rig.robot, "pose", invalid);
            rig.one.a = true;
            rig.tick();
            same(rig.power("forward"), 0, "invalid pose stops forward drive");
            same(rig.power("strafe"), 0, "invalid pose stops strafe drive");
            same(rig.power("turn"), 0, "invalid pose stops rotation");
            check(!(boolean) get(rig.robot, "aimingAtHive"), "invalid pose cannot begin aim");
        }
    }

    private static final class Rig {
        final RegularRobot robot = new RegularRobot();
        final Gamepad one = new Gamepad(), two = new Gamepad();
        final Config config = new Config();
        final BenchmarkIntake intake;
        final BenchmarkShooter shooter;
        final Motor left = new Motor(), right = new Motor();
        final TestLocalizer localizer = new TestLocalizer();
        final BenchmarkDrivetrain drivetrain;
        Rig(Alliance alliance) throws Exception {
            FakeMap map = new FakeMap();
            for (String name : new String[]{"leftFront", "rightFront", "leftRear", "rightRear"})
                map.values.put(name, new Motor().proxy());
            drivetrain = new BenchmarkDrivetrain(map);
            intake = (BenchmarkIntake) get(robot, "intake");
            shooter = (BenchmarkShooter) get(robot, "shooter");
            set(intake, "intakeMotor", new BenchmarkMotor(new Motor().proxy(), .01));
            set(intake, "transferMotor", new BenchmarkMotor(new Motor().proxy(), .01));
            ColorRangeSensor range = proxy(ColorRangeSensor.class, (p, m, a) ->
                    m.getName().equals("getDistance") ? 100.0 : default_value(m.getReturnType()));
            set(intake, "colorSensorLow", range);
            set(intake, "colorSensorHigh", range);
            set(shooter, "shooterMotorLeft", new BenchmarkMotor(left.proxy(), .002));
            set(shooter, "shooterMotorRight", new BenchmarkMotor(right.proxy(), .002));
            set(shooter, "intakeSubsystem", intake);
            Servo servo = proxy(Servo.class, (p, m, a) -> default_value(m.getReturnType()));
            set(shooter, "servoHood", servo);
            FlowerIntake flower = (FlowerIntake) get(robot, "flower");
            set(flower, "flowerServoLeft", servo);
            set(flower, "flowerServoRight", servo);
            set(robot, "alliance", alliance);
            shooter.set_alliance(alliance);
            set(robot, "drivetrain", drivetrain);
            Follower follower = new Follower(localizer, drivetrain, new Foresight(Constants.foresightConfig));
            set(robot, "follower", follower);
            set(robot, "pose", localizer.pose());
            robot.configure(config);
        }
        void tick() { robot.update(one, two, config); }
        double power(String axis) { return (double) drivetrain.debug().get(axis); }
    }

    private static final class TestLocalizer implements Localizer {
        MotionState state = MotionState.zero().withPose(new Pose(9, 9, 0));
        @Override public void setPose(Pose pose) { state = MotionState.zero().withPose(pose); }
        @Override public Pose pose() { return state.pose(); }
        @Override public MotionState state() { return state; }
        @Override public void update() { }
        @Override public void reset() { state = MotionState.zero(); }
    }

    private static final class FakeMap extends HardwareMap {
        final Map<String, Object> values = new HashMap<>();
        FakeMap() { super(null, null); }
        @Override public <T> T get(Class<? extends T> type, String name) { return type.cast(values.get(name)); }
        @Override public <T> List<T> getAll(Class<? extends T> type) { return Collections.emptyList(); }
    }

    private static final class Motor implements InvocationHandler {
        double power;
        DcMotorEx proxy() { return PerformanceRegularControlChecks.proxy(DcMotorEx.class, this); }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getMode": return DcMotor.RunMode.RUN_WITHOUT_ENCODER;
                case "getDirection": return DcMotorSimple.Direction.FORWARD;
                case "getZeroPowerBehavior": return DcMotor.ZeroPowerBehavior.FLOAT;
                case "getVelocity": return 3000.0 * BenchmarkShooter.ENCODER_TICKS_PER_REV / 60;
                case "setPower": power = (double) args[0]; return null;
                default: return default_value(method.getReturnType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object default_value(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void same(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-8, message + ": " + actual + " versus " + expected);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
