package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Random;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;
import static org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.*;

/** Differential behavior checks and hardware-call assertions for the regular-control benchmark. */
public final class PerformanceRegularMechanismChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        motor_policies();
        hood_policies_and_settling();
        encoder_consumers();
        sensor_schedules_and_validity();
        target_and_shot_models();
        production_differential_sequence();
        active_shot_faults();
        System.out.println("Passed " + checks + " regular mechanism checks");
    }

    private static Config optimized() {
        Config config = new Config();
        config.powerPolicy = config.servoPolicy = WritePolicy.EPSILON;
        config.shareReads = config.reuseTarget = true;
        config.staggerRanges = false;
        config.rangeIntervalNanos = 300_000_000L;
        config.shotMode = ShotMode.REUSE;
        return config;
    }

    private static void motor_policies() {
        Motor hardware = new Motor();
        BenchmarkMotor motor = new BenchmarkMotor(hardware.proxy(), .002);
        motor.configure(WritePolicy.RAW);
        motor.set_power(.3); motor.set_power(.3);
        check(hardware.writes == 2, "raw power repeats reach SDK interface");
        motor.configure(WritePolicy.EXACT_CHANGE);
        int before = hardware.writes;
        motor.set_power(.3); motor.set_power(.3); motor.set_power(.300001);
        check(hardware.writes == before + 2, "exact change forwards distinct command only");
        motor.configure(WritePolicy.EPSILON);
        motor.set_power(.3);
        before = hardware.writes;
        motor.set_power(.3005); motor.set_power(.301); motor.set_power(.3015);
        check(hardware.writes == before, "small changes accumulate against applied power");
        motor.set_power(.3021);
        check(hardware.writes == before + 1, "accumulated motor delta crosses epsilon");
        motor.set_power(.0001); motor.set_power(0);
        same(hardware.power, 0, "tiny power still permits exact zero stop");
        motor.set_power(.4);
        before = hardware.writes;
        check(!motor.set_power_if_valid(Double.NaN), "invalid active-shot command rejected");
        check(hardware.writes == before && hardware.power == .4, "invalid retained command does not write transient stop");
        check(!motor.set_power(Double.POSITIVE_INFINITY), "invalid non-active command rejected");
        same(hardware.power, 0, "invalid non-active command safely stops");
        motor.invalidate(); before = hardware.writes; motor.set_power(0);
        check(hardware.writes == before + 1, "mode invalidation forwards repeated zero");
    }

    private static void hood_policies_and_settling() throws Exception {
        Rig rig = new Rig(true);
        Config config = optimized(); config.servoPolicy = WritePolicy.RAW;
        invoke(rig.shooter, "configure", config);
        invoke(rig.shooter, "set_hood_angle", 45.0);
        settled(rig.shooter);
        long timestamp = (long) get(rig.shooter, "lastHoodCommandNanos");
        int before = rig.hoodWrites;
        invoke(rig.shooter, "update_hood"); invoke(rig.shooter, "update_hood");
        check(rig.hoodWrites == before + 2, "raw identical hood commands reach SDK");
        check((long) get(rig.shooter, "lastHoodCommandNanos") == timestamp,
                "raw identical hood commands retain settling timestamp");
        check((boolean) invoke(rig.shooter, "hood_ready"), "raw repeated commands cannot indefinitely block feeding");
        config.servoPolicy = WritePolicy.EPSILON;
        invoke(rig.shooter, "configure", config);
        invoke(rig.shooter, "set_hood_angle", 45.0);
        before = rig.hoodWrites;
        invoke(rig.shooter, "adjust_hood_angle", .1);
        invoke(rig.shooter, "adjust_hood_angle", .1);
        check(rig.hoodWrites == before, "hood epsilon suppresses accumulated subthreshold changes");
        invoke(rig.shooter, "adjust_hood_angle", .3);
        check(rig.hoodWrites == before + 1, "hood accumulation crosses actual applied-position threshold");
        check(!(boolean) invoke(rig.shooter, "hood_ready"), "actual hood movement restarts settling gate");
        config.servoPolicy = WritePolicy.EXACT_CHANGE;
        invoke(rig.shooter, "configure", config);
        before = rig.hoodWrites;
        invoke(rig.shooter, "adjust_hood_angle", .0001);
        check(rig.hoodWrites == before + 1, "exact hood policy forwards small distinct position");
    }

    private static void encoder_consumers() throws Exception {
        Rig rig = new Rig(true);
        Config config = optimized();
        invoke(rig.shooter, "configure", config);
        invoke(rig.shooter, "update", 9.0, 9.0, true);
        int before = rig.left.reads;
        invoke(rig.shooter, "ready_to_feed"); invoke(rig.shooter, "get_current_rpm");
        invoke(rig.shooter, "velocity_valid");
        check(rig.left.reads == before, "shared-read consumers reuse one hardware sample");
        config.shareReads = false;
        invoke(rig.shooter, "configure", config);
        before = rig.left.reads;
        invoke(rig.shooter, "ready_to_feed"); invoke(rig.shooter, "get_current_rpm");
        invoke(rig.shooter, "velocity_valid");
        check(rig.left.reads == before + 3, "uncached three consumers each read real encoder");
        rig.left.velocity = Double.NaN;
        check(!(boolean) invoke(rig.shooter, "velocity_valid"), "uncached consumer observes fresh invalid encoder");
        check(Double.isFinite((double) invoke(rig.shooter, "get_current_rpm")), "invalid encoder retains last finite numeric RPM");
    }

    private static void sensor_schedules_and_validity() throws Exception {
        Rig rig = new Rig(true);
        Config config = optimized(); config.rangeIntervalNanos = 10_000_000_000L;
        invoke(rig.intake, "configure", config);
        check((boolean) invoke(rig.intake, "sensor_readings_valid"), "first paired finite samples valid");
        int low = rig.lowerReads, high = rig.upperReads;
        invoke(rig.intake, "loaded"); invoke(rig.intake, "sensor_readings_valid");
        check(rig.lowerReads == low && rig.upperReads == high, "P5 suppresses premature paired rereads");
        rig.lower = Double.NaN;
        set(rig.intake, "nextPairAt", 0L);
        check(!(boolean) invoke(rig.intake, "sensor_readings_valid"), "fresh invalid range invalidates latest pair");
        SensorReadings.Sample sample = (SensorReadings.Sample) get(rig.intake, "lowerDistance");
        same(sample.get_value(), 12, "invalid range retains last finite distance");
        check(!sample.is_valid(), "retained range value cannot masquerade as current validity");
        config.rangeIntervalNanos = 0;
        invoke(rig.intake, "configure", config);
        low = rig.lowerReads; high = rig.upperReads;
        invoke(rig.intake, "loaded"); invoke(rig.intake, "loaded");
        check(rig.lowerReads == low + 2 && rig.upperReads == high + 2, "P5 off rereads both devices for each consumer");
        rig.lower = 12;
        config.rangeIntervalNanos = 10_000_000_000L; config.staggerRanges = true;
        invoke(rig.intake, "configure", config);
        low = rig.lowerReads; high = rig.upperReads;
        check(!(boolean) invoke(rig.intake, "sensor_readings_valid"), "stagger begins without pretending delayed upper sample exists");
        check(rig.lowerReads == low + 1 && rig.upperReads == high, "P6 initially reads only lower device");
        long lowerDeadline = (long) get(rig.intake, "nextLowerAt");
        long upperDeadline = (long) get(rig.intake, "nextUpperAt");
        check(lowerDeadline - upperDeadline == config.rangeIntervalNanos / 2, "P6 preserves equal period with half-period phase");
        set(rig.intake, "nextUpperAt", 0L);
        check((boolean) invoke(rig.intake, "sensor_readings_valid"), "delayed finite upper sample completes valid pair");
        check(rig.lowerReads == low + 1 && rig.upperReads == high + 1, "staggered upper read does not reread lower");
    }

    private static void target_and_shot_models() throws Exception {
        Rig rig = new Rig(true);
        Config config = optimized();
        invoke(rig.shooter, "configure", config);
        invoke(rig.shooter, "update_target_hive", 9.0, 9.0, true);
        Object first = invoke(rig.shooter, "get_target_hive");
        invoke(rig.shooter, "update_target_hive", 9.0, 9.0, true);
        check(first == invoke(rig.shooter, "get_target_hive"), "P8 reuses unchanged target object");
        config.reuseTarget = false;
        invoke(rig.shooter, "configure", config);
        invoke(rig.shooter, "update_target_hive", 9.0, 9.0, true);
        first = invoke(rig.shooter, "get_target_hive");
        invoke(rig.shooter, "update_target_hive", 9.0, 9.0, true);
        check(first != invoke(rig.shooter, "get_target_hive"), "P8 off actually recalculates identical target");
        for (ShotMode mode : ShotMode.values()) {
            config.shotMode = mode;
            invoke(rig.shooter, "configure", config);
            for (double distance : new double[]{-1, 0, 1, 72, 144, Double.NaN, Double.MAX_VALUE}) {
                invoke(rig.shooter, "update_shot_solution", distance);
                Shooter.ShotSolution reference = Shooter.calculate_shot_solution(distance);
                check((boolean) get(rig.shooter, "reusableShotValid") == reference.valid, "P9 pair validity matches production " + mode);
                same((double) get(rig.shooter, "reusableShotRpm"), reference.rpm, "P9 RPM matches production " + mode);
                same((double) get(rig.shooter, "reusableShotHood"), reference.hoodAngleDegrees, "P9 hood matches production " + mode);
            }
            invoke(rig.shooter, "update_shot_solution", 72.0);
            long calculated = (long) invoke(rig.shooter, "shot_calculations");
            invoke(rig.shooter, "update_shot_solution", 72.0);
            check((long) invoke(rig.shooter, "shot_calculations") == calculated + (mode == ShotMode.CACHE_UNCHANGED ? 0 : 1),
                    "P9 selected allocation/reuse/cache policy actually executes " + mode);
        }
    }

    private static void production_differential_sequence() throws Exception {
        Rig reference = new Rig(false), benchmark = new Rig(true);
        Random random = new Random(20261016L);
        for (int step = 0; step < 60; step++) {
            int action = random.nextInt(9);
            double rpm = step % 11 == 0 ? Double.NaN : step % 3 == 0 ? 0 : step % 5 == 0 ? 3500 : 3000;
            boolean loaded = step % 7 == 0;
            boolean validPose = step % 13 != 0;
            for (Rig rig : new Rig[]{reference, benchmark}) {
                rig.left.velocity = rpm * Shooter.ENCODER_TICKS_PER_REV / 60;
                rig.lower = rig.upper = loaded ? -1 : 24;
                set(rig.intake, "sensorReadingsInitialized", false);
                switch (action) {
                    case 0: invoke(rig.intake, "collect"); break;
                    case 1: invoke(rig.intake, "toggle_score_mode"); break;
                    case 2: invoke(rig.shooter, "prepare_shot"); break;
                    case 3: invoke(rig.shooter, "shoot"); break;
                    case 4: invoke(rig.shooter, "stop_shooting"); break;
                    case 5: invoke(rig.shooter, "set_bypass_enabled", step % 2 == 0); break;
                    case 6: invoke(rig.shooter, "set_target_rpm", step % 2 == 0 ? 3000.0 : 4000.0); break;
                    case 7: invoke(rig.shooter, "set_hood_angle", step % 2 == 0 ? 45.0 : 50.0); break;
                    default: invoke(rig.intake, "reverse"); break;
                }
                settled(rig.shooter);
                invoke(rig.shooter, "update", validPose ? 9.0 : Double.NaN, 9.0, validPose);
            }
            for (String getter : new String[]{"get_state", "shot_in_progress", "target_available", "velocity_valid", "ready_to_feed"})
                check(invoke(reference.shooter, getter).toString().equals(invoke(benchmark.shooter, getter).toString()), "production state parity at " + step + " " + getter);
            for (String getter : new String[]{"get_target_rpm", "get_target_hood_angle", "get_current_rpm"})
                same((double) invoke(benchmark.shooter, getter), (double) invoke(reference.shooter, getter), "production numeric parity " + step + " " + getter);
            for (String getter : new String[]{"get_intake_power", "get_transfer_power"})
                same((double) invoke(benchmark.intake, getter), (double) invoke(reference.intake, getter), "production feed parity " + step + " " + getter);
            check(invoke(reference.intake, "full_load_held").equals(invoke(benchmark.intake, "full_load_held")), "production full-load latch parity " + step);
            same(benchmark.left.power, reference.left.power, "production flywheel output parity " + step);
        }
    }

    private static void active_shot_faults() throws Exception {
        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Rig rig = new Rig(true);
            invoke(rig.shooter, "update", 9.0, 9.0, true);
            invoke(rig.shooter, "shoot");
            check((boolean) invoke(rig.shooter, "shot_in_progress"), "settled ready shot latches before fault");
            double power = rig.left.power;
            int writes = rig.left.writes;
            rig.left.velocity = fault;
            invoke(rig.shooter, "set_target_rpm", 4000.0);
            invoke(rig.shooter, "set_hood_angle", 60.0);
            invoke(rig.shooter, "set_bypass_enabled", false);
            invoke(rig.shooter, "update", Double.NaN, 0.0, false);
            check((boolean) invoke(rig.shooter, "shot_in_progress"), "active shot survives invalid pose and encoder");
            same((double) invoke(rig.intake, "get_intake_power"), 1, "active invalid shot keeps intake");
            same((double) invoke(rig.intake, "get_transfer_power"), 1, "active invalid shot keeps transfer");
            same(rig.left.power, power, "invalid active encoder retains accepted flywheel power");
            check(rig.left.writes == writes, "invalid active encoder cannot issue transient power stop");
            same((double) invoke(rig.shooter, "get_target_rpm"), 3000, "active shot defers target RPM edit");
            same((double) invoke(rig.shooter, "get_target_hood_angle"), 45, "active shot defers hood edit");
            invoke(rig.shooter, "stop_shooting"); invoke(rig.shooter, "shoot"); invoke(rig.shooter, "update");
            check(!(boolean) invoke(rig.shooter, "shot_in_progress"), "new shot cannot reuse invalid old latch");
            same((double) invoke(rig.intake, "get_transfer_power"), 0, "new invalid shot cannot feed");
            invoke(rig.shooter, "stop");
            same(rig.left.power, 0, "explicit stop overrides retained flywheel command");
        }
    }

    private static final class Motor {
        double velocity = 3000 * Shooter.ENCODER_TICKS_PER_REV / 60, power;
        int reads, writes;
        DcMotorEx proxy() {
            return (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class}, (p, m, a) -> {
                if (m.getName().equals("getVelocity")) { reads++; return velocity; }
                if (m.getName().equals("setPower")) { writes++; power = (double) a[0]; }
                return null;
            });
        }
    }

    private static final class Rig {
        final Object shooter, intake;
        final Motor left = new Motor(), right = new Motor();
        double lower = 12, upper = 24;
        int lowerReads, upperReads, hoodWrites;
        Rig(boolean benchmark) throws Exception {
            shooter = benchmark ? new BenchmarkShooter() : new Shooter();
            intake = benchmark ? new BenchmarkIntake() : new Intake();
            set(intake, "intakeMotor", wrapped(new Motor(), .01, benchmark));
            set(intake, "transferMotor", wrapped(new Motor(), .01, benchmark));
            set(shooter, "shooterMotorLeft", wrapped(left, .002, benchmark));
            set(shooter, "shooterMotorRight", wrapped(right, .002, benchmark));
            set(shooter, "intakeSubsystem", intake);
            set(shooter, "servoHood", Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[]{Servo.class}, (p, m, a) -> {
                if (m.getName().equals("setPosition")) hoodWrites++;
                return null;
            }));
            set(intake, "colorSensorLow", Proxy.newProxyInstance(ColorRangeSensor.class.getClassLoader(), new Class<?>[]{ColorRangeSensor.class}, (p, m, a) -> {
                if (m.getName().equals("getDistance")) { lowerReads++; return lower; }
                return null;
            }));
            set(intake, "colorSensorHigh", Proxy.newProxyInstance(ColorRangeSensor.class.getClassLoader(), new Class<?>[]{ColorRangeSensor.class}, (p, m, a) -> {
                if (m.getName().equals("getDistance")) { upperReads++; return upper; }
                return null;
            }));
            if (benchmark) { invoke(shooter, "configure", optimized()); invoke(intake, "configure", optimized()); }
            invoke(shooter, "set_alliance", Alliance.RED);
            invoke(shooter, "set_bypass_enabled", true);
            settled(shooter);
        }
    }

    private static Object wrapped(Motor motor, double epsilon, boolean benchmark) {
        return benchmark ? new BenchmarkMotor(motor.proxy(), epsilon) : new CachedMotor(motor.proxy(), epsilon);
    }

    private static void settled(Object shooter) throws Exception {
        set(shooter, "lastHoodCommandNanos", System.nanoTime() - 10_000_000_000L);
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < types.length; i++) {
                Class<?> boxed = types[i] == double.class ? Double.class : types[i] == boolean.class ? Boolean.class : types[i];
                if (!boxed.isInstance(arguments[i])) matches = false;
            }
            if (!matches) continue;
            method.setAccessible(true);
            try { return method.invoke(target, arguments); }
            catch (InvocationTargetException error) { throw new AssertionError(name, error.getCause()); }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static void same(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9 || Double.isNaN(actual) && Double.isNaN(expected), message + ": " + actual + " versus " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
