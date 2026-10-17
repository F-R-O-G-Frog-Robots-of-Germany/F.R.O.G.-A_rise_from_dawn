package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import static org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.*;

/** Real Pedro math and SDK-signature motor proxies; transport timing needs the robot. */
public final class PerformanceRegularDriveChecks {
    private static int checks;

    public static void main(String[] args) {
        stock_math_and_isolation();
        policy_and_stops();
        restore_and_failures();
        failed_initialization();
        System.out.println("Passed " + checks + " regular performance drivetrain checks");
    }

    private static void stock_math_and_isolation() {
        MecanumConfig shared = Constants.drivetrainConfig;
        String flName = shared.frontLeftName.get();
        DcMotorSimple.Direction flDirection = shared.frontLeftDirection.get();
        double threshold = shared.powerThreshold.get();
        Fixture fixture = new Fixture();
        BenchmarkDrivetrain drive = new BenchmarkDrivetrain(fixture.map);
        near(drive.power_epsilon(), threshold, "Epsilon is captured from the real Pedro configuration");
        Config config = new Config();
        apply_baseline(config);
        drive.configure(config);
        Mecanum reference = new Mecanum(new Fixture().map, shared);
        Random random = new Random(91632026L);
        for (int sample = 0; sample < 200; sample++) {
            DrivePowers input = new DrivePowers(random.nextDouble() * 4 - 2,
                    random.nextDouble() * 4 - 2, random.nextDouble() * 4 - 2);
            boolean manual = sample % 2 == 0;
            drive.drive(input, manual);
            double[] expected = reference.computeWheelPowersUnnormalized(input);
            double largest = 1.0;
            for (double power : expected) largest = Math.max(largest, Math.abs(power));
            for (int wheel = 0; wheel < expected.length; wheel++) {
                near(fixture.motors[wheel].power, expected[wheel] / largest,
                        "Stock Pedro FL/FR/BL/BR mixing and normalization");
                check(Math.abs(fixture.motors[wheel].power) <= 1.0, "Power is normalized");
                check(fixture.motors[wheel].zero == (manual
                        ? DcMotor.ZeroPowerBehavior.BRAKE : DcMotor.ZeroPowerBehavior.FLOAT),
                        "Manual brake and nonmanual coast match Pedro");
            }
            DrivePowers delta = new DrivePowers(.02, -.03, .04);
            near(drive.maxScaling(input, delta), reference.maxScaling(input, delta), "Stock maxScaling");
            double theta = random.nextDouble() * Math.PI * 2;
            near(drive.interpolateVelocity(70, 59, theta), reference.interpolateVelocity(70, 59, theta),
                    "Stock velocity interpolation");
        }
        check(shared.frontLeftName.get().equals(flName) && shared.frontLeftDirection.get() == flDirection
                && shared.powerThreshold.get() == threshold, "Test drivetrain never mutates shared constants");
        check(drive.powerAttempts == 800 && drive.powerForwarded == 800 && drive.powerSkipped == 0,
                "Raw baseline forwards all four power calls each loop");
        check(drive.debug().containsKey("powerScale") && drive.debug().containsKey("leftFrontWheelPower"),
                "Debug data retains Pedro drivetrain keys");
        drive.restore();
        fixture.check_original();
    }

    private static void policy_and_stops() {
        Fixture fixture = new Fixture();
        BenchmarkDrivetrain drive = new BenchmarkDrivetrain(fixture.map);
        Config config = new Config();
        apply_baseline(config);
        drive.configure(config);
        fixture.clear_counts();
        for (int loop = 0; loop < 3; loop++) drive.drive(new DrivePowers(.25, 0, 0), true);
        for (MotorState motor : fixture.motors) check(motor.powerCalls == 3, "Raw repeats reach SDK");
        config.powerPolicy = WritePolicy.EXACT_CHANGE;
        drive.configure(config);
        fixture.clear_counts();
        drive.drive(new DrivePowers(.4, 0, 0), true);
        drive.drive(new DrivePowers(.4, 0, 0), true);
        drive.drive(new DrivePowers(.40001, 0, 0), true);
        for (MotorState motor : fixture.motors) check(motor.powerCalls == 2, "Exact suppresses only identical powers");
        config.powerPolicy = WritePolicy.EPSILON;
        drive.configure(config);
        fixture.clear_counts();
        double epsilon = drive.power_epsilon();
        check(epsilon > 0, "The robot has a positive configured Pedro power threshold");
        for (double fraction : new double[]{0, .25, .5, .75})
            drive.drive(new DrivePowers(.1 + epsilon * fraction, 0, 0), true);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 1, "Epsilon compares against last forwarded power");
            near(motor.power, .1, "Subthreshold changes do not move cache reference");
        }
        drive.drive(new DrivePowers(.1 + epsilon * 1.05, 0, 0), true);
        for (MotorState motor : fixture.motors) check(motor.powerCalls == 2, "Accumulated delta eventually forwards");
        check(drive.powerAttempts == 20 && drive.powerForwarded == 8 && drive.powerSkipped == 12,
                "Policy counters account for every attempted command");
        drive.configure(config);
        fixture.clear_counts();
        drive.drive(new DrivePowers(epsilon * .1, 0, 0), true);
        drive.drive(DrivePowers.zero(), true);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 2, "Exact zero bypasses epsilon for a tiny live power");
            near(motor.power, 0, "Exact stop reaches motor");
        }
        drive.stop();
        drive.stop(false);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 4, "Stop always writes SDK zero even with a zero cache");
            check(motor.zero == DcMotor.ZeroPowerBehavior.FLOAT, "stop(false) preserves requested coast");
        }
        drive.configure(config);
        fixture.clear_counts();
        drive.drive(new DrivePowers(epsilon * .1, 0, 0), true);
        drive.drive(new DrivePowers(-epsilon * .1, 0, 0), true);
        for (MotorState motor : fixture.motors) {
            check(motor.powerCalls == 2, "Pedro sign changes bypass the threshold even for a tiny delta");
            near(motor.power, -epsilon * .1, "Subthreshold reversal reaches the motor");
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            drive.drive(new DrivePowers(.3, 0, 0), true);
            drive.drive(new DrivePowers(invalid, 0, 0), true);
            for (MotorState motor : fixture.motors) near(motor.power, 0, "Invalid math stops every motor");
        }
        drive.restore();
    }

    private static void restore_and_failures() {
        Fixture fixture = new Fixture();
        BenchmarkDrivetrain drive = new BenchmarkDrivetrain(fixture.map);
        drive.drive(new DrivePowers(.3, 0, 0), true);
        for (MotorState motor : fixture.motors) motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
        drive.restore();
        fixture.check_original();
        for (MotorState motor : fixture.motors) near(motor.power, 0, "Restore leaves all motors stopped");

        fixture = new Fixture();
        drive = new BenchmarkDrivetrain(fixture.map);
        drive.drive(new DrivePowers(.3, 0, 0), true);
        fixture.motors[0].failZeroOnce = true;
        boolean stoppedWithError = false;
        try { drive.stop(); } catch (IllegalStateException expected) { stoppedWithError = true; }
        check(stoppedWithError, "A stop transport failure is reported");
        for (int i = 1; i < fixture.motors.length; i++)
            near(fixture.motors[i].power, 0, "One failed stop does not prevent other motors stopping");
        fixture.motors[0].failModeOnce = true;
        boolean restoredWithError = false;
        try { drive.restore(); } catch (IllegalStateException expected) { restoredWithError = true; }
        check(restoredWithError, "A mode restoration failure is reported");
        for (int i = 0; i < fixture.motors.length; i++) {
            MotorState motor = fixture.motors[i];
            check(motor.direction == motor.originalDirection && motor.zero == motor.originalZero,
                    "One mode failure still restores that motor's direction and zero behavior");
            if (i > 0) check(motor.mode == motor.originalMode, "Other motors still restore mode");
            near(motor.power, 0, "Restore retries the previously failed stop");
        }
    }

    private static void failed_initialization() {
        Fixture missing = new Fixture();
        missing.map.named.remove("rightRear");
        boolean missingRejected = false;
        try { new BenchmarkDrivetrain(missing.map); }
        catch (IllegalArgumentException expected) { missingRejected = true; }
        check(missingRejected, "A missing drive motor rejects initialization");
        for (MotorState motor : missing.motors)
            check(motor.powerCalls == 0 && motor.directionCalls == 0 && motor.zeroCalls == 0,
                    "All hardware is acquired before any mutation");

        Fixture partial = new Fixture();
        partial.motors[1].failDirectionOnce = true;
        boolean partialRejected = false;
        try { new BenchmarkDrivetrain(partial.map); }
        catch (IllegalStateException expected) { partialRejected = true; }
        check(partialRejected, "An injected Pedro initialization failure propagates");
        partial.check_original();
        for (MotorState motor : partial.motors) {
            check(motor.powerCalls > 0, "Failed construction stops every acquired drive motor");
            near(motor.power, 0, "Failed construction leaves zero power");
        }
    }

    private static final class FakeMap extends HardwareMap {
        final Map<String, Object> named = new HashMap<>();
        FakeMap() { super(null, null); }
        @Override public <T> T get(Class<? extends T> type, String name) {
            Object value = named.get(name);
            if (!type.isInstance(value)) throw new IllegalArgumentException("Missing test motor " + name);
            return type.cast(value);
        }
    }

    private static final class Fixture {
        final FakeMap map = new FakeMap();
        final MotorState[] motors = new MotorState[4];
        Fixture() {
            String[] names = {"leftFront", "rightFront", "leftRear", "rightRear"};
            for (int i = 0; i < names.length; i++) {
                motors[i] = new MotorState(i);
                map.named.put(names[i], Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                        new Class<?>[]{DcMotorEx.class}, motors[i]));
            }
        }
        void clear_counts() { for (MotorState motor : motors) motor.powerCalls = 0; }
        void check_original() {
            for (MotorState motor : motors) {
                check(motor.mode == motor.originalMode, "Original motor run mode is restored");
                check(motor.direction == motor.originalDirection, "Original motor direction is restored");
                check(motor.zero == motor.originalZero, "Original zero-power behavior is restored");
            }
        }
    }

    private static final class MotorState implements InvocationHandler {
        final DcMotor.RunMode originalMode;
        final DcMotorSimple.Direction originalDirection;
        final DcMotor.ZeroPowerBehavior originalZero;
        DcMotor.RunMode mode;
        DcMotorSimple.Direction direction;
        DcMotor.ZeroPowerBehavior zero;
        double power;
        int powerCalls, directionCalls, zeroCalls;
        boolean failZeroOnce, failModeOnce, failDirectionOnce;
        MotorState(int index) {
            mode = originalMode = index % 2 == 0 ? DcMotor.RunMode.RUN_USING_ENCODER : DcMotor.RunMode.RUN_TO_POSITION;
            direction = originalDirection = index < 2 ? DcMotorSimple.Direction.FORWARD : DcMotorSimple.Direction.REVERSE;
            zero = originalZero = index % 2 == 0 ? DcMotor.ZeroPowerBehavior.BRAKE : DcMotor.ZeroPowerBehavior.FLOAT;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getMode": return mode;
                case "getDirection": return direction;
                case "getZeroPowerBehavior": return zero;
                case "setMode":
                    if (failModeOnce) { failModeOnce = false; throw new IllegalStateException("Injected mode failure"); }
                    mode = (DcMotor.RunMode) args[0]; return null;
                case "setDirection":
                    directionCalls++;
                    if (failDirectionOnce) { failDirectionOnce = false; throw new IllegalStateException("Injected direction failure"); }
                    direction = (DcMotorSimple.Direction) args[0]; return null;
                case "setZeroPowerBehavior": zeroCalls++; zero = (DcMotor.ZeroPowerBehavior) args[0]; return null;
                case "setPower":
                    powerCalls++;
                    if (failZeroOnce && (double) args[0] == 0) {
                        failZeroOnce = false; throw new IllegalStateException("Injected stop failure");
                    }
                    power = (double) args[0]; return null;
                default: return null;
            }
        }
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= 1e-12, message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
