package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;
import static org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.*;

/** Standalone JVM checks of benchmark safety, equivalent inputs and cache correctness. */
public final class PerformanceWorkloadChecks {
    private static final Class<?> WORKLOAD;
    private static int checks;

    static {
        try { WORKLOAD = Class.forName(PerformanceComparison.class.getName() + "$Workload"); }
        catch (ClassNotFoundException error) { throw new ExceptionInInitializerError(error); }
    }

    public static void main(String[] args) throws Exception {
        policies_and_deadlines();
        equivalent_shot_model();
        shared_reads_and_range_validity();
        shadow_and_servo_safety();
        live_control_and_stop();
        cleanup_continues_after_failure();
        missing_devices();
        System.out.println("Passed " + checks + " performance workload checks");
    }

    private static void policies_and_deadlines() throws Exception {
        check(forward(WritePolicy.RAW, true, .1, .1, .002, true), "raw forwards equal values");
        check(!forward(WritePolicy.EXACT_CHANGE, true, .1, .1, .002, true), "exact skips equal values");
        check(forward(WritePolicy.EXACT_CHANGE, true, .100001, .1, .002, true), "exact forwards small change");
        check(forward(WritePolicy.EPSILON, false, 0, 0, .002, true), "first zero forwards");
        double applied = 0;
        for (int i = 1; i <= 19; i++) {
            double requested = i * .0001;
            check(!forward(WritePolicy.EPSILON, true, requested, applied, .002, true), "small changes accumulate against applied command");
        }
        check(forward(WritePolicy.EPSILON, true, .0021, applied, .002, true), "accumulated command crosses deadband");
        check(forward(WritePolicy.EPSILON, true, 0, .0001, .002, true), "exact motor stop bypasses epsilon");
        check(!forward(WritePolicy.EPSILON, true, 0, .0001, .002, false), "servo epsilon has no motor stop rule");
        for (WritePolicy policy : WritePolicy.values()) {
            for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
                check(!forward(policy, false, value, 0, .002, true), "nonfinite command rejected before forwarding");
        }
        Method next = method(WORKLOAD, "next_deadline", long.class, long.class, long.class);
        for (long completed = 100; completed < 1100; completed += 7) {
            long result = (long) next.invoke(null, 100L, completed, 30L);
            check(result > completed, "next deadline is in future without backlog");
            check((result - 100) % 30 == 0, "deadline retains trial origin");
            check(result - completed <= 30, "no additional period beyond next due");
        }
        check((long) next.invoke(null, 100L, 131L, 0L) == 131L, "zero interval remains every loop");
    }

    private static boolean forward(WritePolicy policy, boolean initialized, double value,
            double last, double epsilon, boolean zero) throws Exception {
        return (boolean) method(WORKLOAD, "forward", WritePolicy.class, boolean.class,
                double.class, double.class, double.class, boolean.class)
                .invoke(null, policy, initialized, value, last, epsilon, zero);
    }

    private static void equivalent_shot_model() throws Exception {
        Class<?> mutableType = Class.forName(WORKLOAD.getName() + "$MutableShot");
        Constructor<?> constructor = mutableType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object mutable = constructor.newInstance();
        double[] cases = {0, -0.0, .01, 24, 72, 144, 1000, 1e150, Double.MAX_VALUE,
                -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double distance : cases) {
            invoke(mutable, "calculate", distance);
            Shooter.ShotSolution reference = Shooter.calculate_shot_solution(distance);
            check((boolean) get(mutable, "valid") == reference.valid, "mutable result validity matches production");
            same((double) get(mutable, "rpm"), reference.rpm);
            same((double) get(mutable, "hood"), reference.hoodAngleDegrees);
        }
    }

    private static void shared_reads_and_range_validity() throws Exception {
        Fixture fixture = new Fixture();
        Object workload = workload(fixture.map);
        Config config = config();
        config.shareReads = false;
        invoke(workload, "prepare", config);
        invoke(workload, "read", System.nanoTime());
        for (MotorState motor : fixture.motors) {
            check(motor.positionReads == 3 && motor.velocityReads == 3, "baseline rereads for three consumers");
            motor.clear();
        }
        double baselineChecksum = (double) get(workload, "checksum");
        config.shareReads = true;
        invoke(workload, "prepare", config);
        invoke(workload, "read", System.nanoTime());
        for (MotorState motor : fixture.motors)
            check(motor.positionReads == 1 && motor.velocityReads == 1, "shared path reads each sensor once");
        same((double) get(workload, "checksum"), baselineChecksum);
        check((long) get(workload, "encoderPositionReads") == 4, "shared position API count");
        check((long) get(workload, "encoderVelocityReads") == 4, "shared velocity API count");
        check((boolean) get(workload, "lowerValid"), "fresh finite range valid");
        fixture.lower.distance = Double.NaN;
        invoke(workload, "read", System.nanoTime());
        check(!(boolean) get(workload, "lowerValid"), "invalid new range invalidates latest validity");
        same((double) get(workload, "lowerDistance"), 12);
        check((long) get(workload, "lowerInvalid") == 1, "invalid range counted");
        check((long) get(workload, "lowerAgeNanos") >= 0, "retained value age remains available");
        check(Double.isFinite((double) get(workload, "checksum")), "invalid sample cannot poison checksum");

        config.rangeIntervalNanos = 10_000_000_000L;
        config.staggerRanges = true;
        fixture.lower.distance = 12;
        fixture.lower.reads = fixture.upper.reads = 0;
        invoke(workload, "prepare", config);
        invoke(workload, "read", System.nanoTime());
        check(fixture.lower.reads == 1 && fixture.upper.reads == 0, "staggered high sensor starts half interval later");
        long nextLower = (long) get(workload, "nextLowerAt");
        long nextUpper = (long) get(workload, "nextUpperAt");
        check(nextLower - nextUpper == config.rangeIntervalNanos / 2, "fixed phase separation preserved");
        long warmedDeadline = (long) get(workload, "nextLowerAt");
        invoke(workload, "reset_counters");
        check((long) get(workload, "nextLowerAt") == warmedDeadline, "measurement reset retains warm-up schedule");
        check((boolean) get(workload, "lowerHasSample"), "measurement reset retains warmed sample");
        invoke(workload, "restore");
    }

    private static void shadow_and_servo_safety() throws Exception {
        Fixture fixture = new Fixture();
        Object workload = workload(fixture.map);
        Config config = config();
        config.powerPolicy = config.servoPolicy = WritePolicy.EXACT_CHANGE;
        invoke(workload, "prepare", config);
        for (int i = 0; i < 3; i++) invoke(workload, "write", 5_000_000_000L, true);
        for (MotorState motor : fixture.motors)
            check(motor.powerCalls == 0 && motor.velocityCalls == 0 && motor.modeCalls == 0,
                    "shadow cannot call motor SDK command methods");
        check(fixture.servo.writes == 0, "shadow cannot call servo SDK command methods");
        check((long) get(workload, "powerShadowForwarded") == 4, "shadow first motor command forwarded once");
        check((long) get(workload, "powerSkipped") == 8, "shadow exact duplicate commands skipped");
        check((long) get(workload, "velocityShadowForwarded") == 4, "shadow velocity policies measured separately");
        invoke(workload, "stop");
        for (MotorState motor : fixture.motors) check(motor.powerCalls == 0, "shadow cleanup does not start motor I/O");

        config.servoLive = true;
        config.commandProfile = CommandProfile.STEPS;
        invoke(workload, "prepare", config);
        invoke(workload, "write", 0L, false);
        check(fixture.servo.writes == 0, "unarmed servo cannot move");
        for (long elapsed = 0; elapsed < 4_000_000_000L; elapsed += 1_000_000_000L) {
            invoke(workload, "write", elapsed, true);
            check(fixture.servo.position >= .49 && fixture.servo.position <= .51, "live hood command stays inside captured envelope");
        }
        check(fixture.servo.writes == 4, "live servo step requests reach SDK");
        invoke(workload, "restore");
        same(fixture.servo.position, .5);
        fixture.servo.position = 0;
        Object unsafeCenter = workload(fixture.map);
        invoke(unsafeCenter, "prepare", config);
        int prior = fixture.servo.writes;
        invoke(unsafeCenter, "write", 0L, true);
        check(fixture.servo.writes == prior, "hood with unsafe captured center remains simulated");
        check(((String) invoke(unsafeCenter, "unavailable", config)).contains("initial SDK command outside"), "unsafe hood reports unavailable");
        invoke(unsafeCenter, "restore");
    }

    private static void live_control_and_stop() throws Exception {
        Fixture fixture = new Fixture();
        Object workload = workload(fixture.map);
        Config config = config();
        config.outputMode = OutputMode.POWER;
        config.powerPolicy = WritePolicy.EPSILON;
        config.commandProfile = CommandProfile.STEPS;
        invoke(workload, "prepare", config);
        for (MotorState motor : fixture.motors) motor.clear();
        invoke(workload, "write", 3_000_000_000L, true);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 1 && motor.velocityCalls == 0, "POWER mode exclusively calls setPower");
            same(motor.power, .1);
        }
        invoke(workload, "write", 4_000_000_000L, true);
        for (MotorState motor : fixture.motors) same(motor.power, 0);

        config.outputMode = OutputMode.VELOCITY;
        config.commandProfile = CommandProfile.CONSTANT;
        invoke(workload, "prepare", config);
        for (MotorState motor : fixture.motors) motor.clear();
        invoke(workload, "write", 0L, true);
        invoke(workload, "write", 1L, true);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 0 && motor.velocityCalls == 1, "VELOCITY target cache avoids power commands");
            same(motor.velocity, 50);
        }
        invoke(workload, "reset_counters");
        invoke(workload, "write", 2L, true);
        check((long) get(workload, "velocitySdkCalls") == 0, "reset retains warmed velocity target cache");
        invoke(workload, "write", 3L, false);
        for (MotorState motor : fixture.motors) {
            int n = motor.events.size();
            check(motor.events.get(n - 2).equals("V:0.0") && motor.events.get(n - 1).equals("P:0.0"),
                    "unarming forces zero velocity before potentially SDK-cached zero power");
        }
        invoke(workload, "write", 4L, true);
        check((long) get(workload, "velocitySdkCalls") == 4, "stop invalidates all target caches");
        invoke(workload, "restore");
        for (MotorState motor : fixture.motors) {
            check(motor.mode == DcMotor.RunMode.RUN_WITHOUT_ENCODER, "original motor mode restored");
            same(motor.power, 0);
        }
    }

    private static void cleanup_continues_after_failure() throws Exception {
        Fixture fixture = new Fixture();
        Object workload = workload(fixture.map);
        Config config = config();
        config.outputMode = OutputMode.VELOCITY;
        invoke(workload, "prepare", config);
        invoke(workload, "write", 0L, true);
        for (MotorState motor : fixture.motors) motor.clear();
        fixture.motors[0].failVelocityStop = true;
        boolean failed = false;
        try { invoke(workload, "stop"); } catch (RuntimeException expected) { failed = true; }
        check(failed, "stop propagates hardware failure after trying all devices");
        for (MotorState motor : fixture.motors)
            check(motor.velocityCalls == 1 && motor.powerCalls == 1, "one failure does not prevent other motor stop attempts");
        fixture.motors[0].failVelocityStop = false;
        fixture.motors[0].failMode = true;
        failed = false;
        try { invoke(workload, "restore"); } catch (RuntimeException expected) { failed = true; }
        check(failed, "restore propagates mode failure");
        for (int i = 1; i < fixture.motors.length; i++)
            check(fixture.motors[i].mode == DcMotor.RunMode.RUN_WITHOUT_ENCODER,
                    "mode restoration continues after first device failure");
    }

    private static void missing_devices() throws Exception {
        Object workload = workload(new FakeMap());
        Config config = config();
        invoke(workload, "prepare", config);
        invoke(workload, "read", System.nanoTime());
        invoke(workload, "compute", 123_000_000L);
        invoke(workload, "write", 123_000_000L, true);
        check(Double.isFinite((double) get(workload, "checksum")), "missing hardware still permits labeled synthetic computation");
        check((long) get(workload, "encoderPositionReads") == 0, "missing motor not recorded as a successful read");
        check((long) get(workload, "lowerAgeNanos") == -1, "missing range age explicitly unavailable");
        String unavailable = (String) invoke(workload, "unavailable", config);
        check(unavailable.contains("missing leftFront") && unavailable.contains("missing range"), "missing hardware visible in results");
        invoke(workload, "restore");
    }

    private static Config config() {
        Config config = new Config();
        config.bulkMode = LynxModule.BulkCachingMode.OFF;
        config.rangeIntervalNanos = 0;
        config.staggerRanges = false;
        return config;
    }

    private static Object workload(FakeMap map) throws Exception {
        Constructor<?> constructor = WORKLOAD.getDeclaredConstructor(HardwareMap.class);
        constructor.setAccessible(true);
        return constructor.newInstance(map);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            method.setAccessible(true);
            try { return method.invoke(target, arguments); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
                throw error;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void same(double actual, double expected) {
        check(actual == expected || Double.isNaN(actual) && Double.isNaN(expected), "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeMap extends HardwareMap {
        final Map<String, Object> named = new HashMap<>();
        FakeMap() { super(null, null); }
        @Override public <T> T tryGet(Class<? extends T> type, String name) {
            Object value = named.get(name);
            return type.isInstance(value) ? type.cast(value) : null;
        }
        @Override public <T> List<T> getAll(Class<? extends T> type) { return Collections.emptyList(); }
    }

    private static final class Fixture {
        final FakeMap map = new FakeMap();
        final MotorState[] motors = {new MotorState(), new MotorState(), new MotorState(), new MotorState()};
        final ServoState servo = new ServoState();
        final RangeState lower = new RangeState(12), upper = new RangeState(24);
        Fixture() {
            String[] names = {"leftFront", "leftRear", "rightFront", "rightRear"};
            for (int i = 0; i < motors.length; i++) map.named.put(names[i], proxy(DcMotorEx.class, motors[i]));
            map.named.put("servoHood", proxy(Servo.class, servo));
            map.named.put("colorSensorLow", proxy(ColorRangeSensor.class, lower));
            map.named.put("colorSensorHigh", proxy(ColorRangeSensor.class, upper));
        }
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class MotorState implements InvocationHandler {
        DcMotor.RunMode mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
        int positionReads, velocityReads, powerCalls, velocityCalls, modeCalls;
        double power, velocity;
        boolean failVelocityStop, failMode;
        final List<String> events = new ArrayList<>();
        void clear() { positionReads = velocityReads = powerCalls = velocityCalls = modeCalls = 0; events.clear(); }
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
            switch (method.getName()) {
                case "getMode": return mode;
                case "getDirection": return DcMotorSimple.Direction.FORWARD;
                case "getZeroPowerBehavior": return DcMotor.ZeroPowerBehavior.FLOAT;
                case "getCurrentPosition": positionReads++; return 100;
                case "getVelocity": velocityReads++; return 50.0;
                case "setMode":
                    modeCalls++;
                    if (failMode) throw new IllegalStateException("injected mode failure");
                    mode = (DcMotor.RunMode) arguments[0]; return null;
                case "setPower": powerCalls++; power = (double) arguments[0]; events.add("P:" + power); return null;
                case "setVelocity":
                    velocityCalls++; velocity = (double) arguments[0]; events.add("V:" + velocity);
                    if (velocity == 0 && failVelocityStop) throw new IllegalStateException("injected stop failure");
                    return null;
                default: return null;
            }
        }
    }

    private static final class ServoState implements InvocationHandler {
        double position = .5;
        int writes;
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("getPosition")) return position;
            if (method.getName().equals("setPosition")) { position = (double) arguments[0]; writes++; }
            return null;
        }
    }

    private static final class RangeState implements InvocationHandler {
        double distance;
        int reads;
        RangeState(double distance) { this.distance = distance; }
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("getDistance")) { reads++; return distance; }
            return null;
        }
    }
}
