package org.firstinspires.ftc.teamcode.core.hardware;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Proxy;

/** Standalone fault-injection checks with SDK hardware test doubles. */
public final class FiniteValueChecks {
    private static final double[] INVALID_VALUES = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    private static int checks;

    public static void main(String[] args) {
        check(SensorReadings.are_valid(0, -1, Double.MAX_VALUE), "accept finite values");
        for (double invalid : INVALID_VALUES) {
            check(!SensorReadings.is_valid(invalid), "reject nonfinite value");
            check(!SensorReadings.are_valid(1, invalid, 2), "reject whole invalid sample");
            check_sample(invalid);
            check_motor(invalid);
            check_servo(invalid);
        }
        check_velocity_sampling();
        System.out.println("Passed " + checks + " finite-value boundary checks");
    }

    private static void check_sample(double invalid) {
        SensorReadings.Sample sample = new SensorReadings.Sample();
        check(!sample.is_valid(), "unsampled value is unavailable");
        close(sample.get_value(), 0);
        check(!sample.update(invalid), "invalid first sample rejected");
        close(sample.get_value(), 0);
        check(sample.update(12.5) && sample.is_valid(), "sample recovers");
        close(sample.get_value(), 12.5);
        check(!sample.update(invalid) && !sample.is_valid(), "invalid new sample marks held value unavailable");
        close(sample.get_value(), 12.5);
        check(sample.update(-2) && sample.is_valid(), "fresh finite sample clears fault");
        close(sample.get_value(), -2);
        sample.reset();
        check(!sample.is_valid(), "reset clears sample validity");
        close(sample.get_value(), 0);
    }

    private static void check_motor(double invalid) {
        MotorDouble hardware = new MotorDouble();
        CachedMotor motor = new CachedMotor(hardware.motor, 0.002);
        check(!motor.set_power(invalid), "invalid first power rejected");
        close(hardware.power, 0);
        check(hardware.powerWrites == 1, "invalid first power writes stop");
        check(motor.set_power(0.5), "finite power accepted after fault");
        close(hardware.power, 0.5);
        check(!motor.set_power(invalid), "invalid active power rejected");
        close(hardware.power, 0);
        check(motor.set_power(0.001), "small finite power accepted");
        motor.invalidate();
        motor.set_power(0.001);
        int beforeStop = hardware.powerWrites;
        check(!motor.set_power(invalid), "invalid low power rejected");
        check(hardware.powerWrites == beforeStop + 1, "stop bypasses epsilon cache");
        close(hardware.power, 0);
        check(motor.set_power(Double.MAX_VALUE), "huge finite positive power accepted");
        close(hardware.power, 1);
        check(motor.set_power(-Double.MAX_VALUE), "huge finite negative power accepted");
        close(hardware.power, -1);
        check(!motor.set_power(invalid), "nonfinite power rejected before clipping");
        close(hardware.power, 0);
        check(motor.set_power(0.5), "power cache recovers");
        close(hardware.power, 0.5);
        int beforeUnchanged = hardware.powerWrites;
        motor.set_power(0.5001);
        check(hardware.powerWrites == beforeUnchanged, "finite unchanged power remains cached");

        check(!motor.set_velocity(invalid), "invalid first velocity rejected");
        close(hardware.velocityCommand, 0);
        check(motor.set_velocity(1250), "finite velocity accepted");
        close(hardware.velocityCommand, 1250);
        check(!motor.set_velocity(invalid), "invalid active velocity rejected");
        close(hardware.velocityCommand, 0);
        check(motor.set_velocity(900), "velocity recovers");
        close(hardware.velocityCommand, 900);
        int beforePowerMode = hardware.powerWrites;
        motor.set_power(0.5);
        check(hardware.powerWrites == beforePowerMode + 1, "velocity mode invalidates power cache");
        close(hardware.power, 0.5);
        motor.set_velocity(900);
        motor.set_power(0);
        close(hardware.power, 0);
        check(hardware.invalidWrites == 0, "motor hardware never receives nonfinite commands");
    }

    private static void check_servo(double invalid) {
        double[] position = {Double.NaN};
        int[] writes = {0};
        Servo servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[]{Servo.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("setPosition")) {
                        position[0] = (double) values[0];
                        writes[0]++;
                    }
                    return null;
                });
        CachedServo cached = new CachedServo(servo, 0.002);
        check(!cached.set_position(invalid) && writes[0] == 0, "invalid first servo command sends nothing");
        check(cached.set_position(0.4), "finite servo command accepted after fault");
        close(position[0], 0.4);
        check(!cached.set_position(invalid) && writes[0] == 1, "invalid servo command holds position");
        close(position[0], 0.4);
        check(cached.set_position(0.4001) && writes[0] == 1, "invalid command does not poison servo cache");
        check(cached.set_position(0.7), "servo recovers");
        close(position[0], 0.7);
        check(cached.set_position(Double.MAX_VALUE), "huge finite servo command accepted");
        close(position[0], 1);
        check(cached.set_position(-Double.MAX_VALUE), "negative finite servo command accepted");
        close(position[0], 0);
        int beforeInvalid = writes[0];
        cached.invalidate();
        check(!cached.set_position(invalid) && writes[0] == beforeInvalid, "invalid servo command after cache reset held");
        check(cached.set_position(0.5) && writes[0] == beforeInvalid + 1, "finite servo command after reset written");
        close(position[0], 0.5);
    }

    private static void check_velocity_sampling() {
        MotorDouble hardware = new MotorDouble();
        CachedMotor motor = new CachedMotor(hardware.motor, 0.002);
        SensorReadings.Sample sample = motor.get_velocity_sample();
        check(!sample.is_valid() && hardware.velocityReads == 0, "cached accessor never reads hardware");
        hardware.velocityReading = 1200;
        check(motor.sample_velocity() == sample && sample.is_valid(), "sampling reuses one cached sample");
        close(sample.get_value(), 1200);
        check(hardware.velocityReads == 1, "sampling reads velocity exactly once");
        hardware.velocityReading = Double.NaN;
        check(motor.get_velocity_sample() == sample && sample.is_valid(), "cached accessor retains last sample validity");
        check(hardware.velocityReads == 1, "readiness and telemetry accessor does not resample");
        for (double invalid : INVALID_VALUES) {
            hardware.velocityReading = invalid;
            int beforeRead = hardware.velocityReads;
            check(motor.sample_velocity() == sample && !sample.is_valid(), "nonfinite velocity marks cached sample unavailable");
            check(hardware.velocityReads == beforeRead + 1, "faulty velocity sampled only once");
            close(sample.get_value(), 1200);
        }
        hardware.velocityReading = 1000;
        check(motor.sample_velocity() == sample && sample.is_valid(), "finite velocity sample recovers");
        close(sample.get_value(), 1000);
    }

    private static void close(double actual, double expected) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-9, "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class MotorDouble {
        private double power, velocityCommand, velocityReading;
        private int powerWrites, velocityReads, invalidWrites;
        private final DcMotorEx motor = (DcMotorEx) Proxy.newProxyInstance(
                DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class}, (proxy, method, values) -> {
                    switch (method.getName()) {
                        case "setPower":
                            power = (double) values[0];
                            powerWrites++;
                            if (!Double.isFinite(power)) invalidWrites++;
                            return null;
                        case "setVelocity":
                            velocityCommand = (double) values[0];
                            if (!Double.isFinite(velocityCommand)) invalidWrites++;
                            return null;
                        case "getVelocity":
                            velocityReads++;
                            return velocityReading;
                        default:
                            return null;
                    }
                });
    }
}
