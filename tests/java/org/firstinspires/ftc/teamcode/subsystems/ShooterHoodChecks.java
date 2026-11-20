package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** Standalone JVM regression checks; hardware interfaces are replaced with test doubles. */
public final class ShooterHoodChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        close(Shooter.hood_angle_to_servo_position(Shooter.HOOD_MIN_ANGLE_DEG), Shooter.HOOD_SERVO_AT_MIN_ANGLE);
        close(Shooter.hood_angle_to_servo_position(Shooter.HOOD_MAX_ANGLE_DEG), Shooter.HOOD_SERVO_AT_MAX_ANGLE);
        close(Shooter.hood_angle_to_servo_position(-1000), Shooter.HOOD_SERVO_AT_MIN_ANGLE);
        close(Shooter.hood_angle_to_servo_position(1000), Shooter.HOOD_SERVO_AT_MAX_ANGLE);
        check(Double.isNaN(Shooter.hood_angle_to_servo_position(Double.NaN)), "reject invalid angle");
        check(Double.isNaN(Shooter.calculate_shot_solution(-1).rpm), "reject negative distance");
        Shooter.ShotSolution invalid = Shooter.calculate_shot_solution(10, 0, 0, 3000, Double.NaN, 0, 45);
        check(Double.isNaN(invalid.rpm) && Double.isNaN(invalid.hoodAngleDegrees), "reject whole invalid pair");
        close(Shooter.calculate_hood_angle(10, 0, 0, 1000), Shooter.HOOD_MAX_ANGLE_DEG);
        close(Shooter.calculate_rpm(10, 0, 0, 1e6), Shooter.MAX_RPM);
        for (Alliance alliance : Alliance.values()) {
            String suffix = alliance == Alliance.RED ? "red" : "blue";
            for (double y : new double[]{9, 71.75, 72}) {
                Shooter.HiveDistance hive = Shooter.nearest_hive(9, y, alliance);
                check(hive.position.name.equals("Bottom " + suffix), "lower hive through y = 72");
                close(hive.distance, Math.hypot(hive.position.x - 9, hive.position.y - y));
            }
            for (double y : new double[]{Math.nextUp(72.0), 133}) {
                Shooter.HiveDistance hive = Shooter.nearest_hive(9, y, alliance);
                check(hive.position.name.equals("Top " + suffix), "upper hive above y = 72");
                close(hive.distance, Math.hypot(hive.position.x - 9, hive.position.y - y));
            }
        }
        Shooter.HivePosition validHive = new Shooter.HivePosition("Test", 1, 2, 3);
        check(validHive.valid && new Shooter.HiveDistance(validHive, 1).valid, "valid central hive result");
        check(!new Shooter.HiveDistance(null, 1).valid, "central hive check rejects missing position");
        for (double invalidDistance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!new Shooter.HiveDistance(validHive, invalidDistance).valid, "central hive check rejects invalid distance");
        }
        Shooter.HivePosition invalidHive = new Shooter.HivePosition("Invalid", Double.NaN, 2, 3);
        check(!invalidHive.valid && !new Shooter.HiveDistance(invalidHive, 1).valid,
                "hive position validity is checked once and reused");
        check(!Shooter.nearest_hive(59.25, 59.58, Alliance.RED).valid, "central hive check rejects zero aim direction");
        check(!Shooter.nearest_hive(9, 9, Alliance.RED, false).valid, "consume rejected pose validity");
        check(Double.isNaN(Shooter.nearest_hive(9, 9, null).distance), "reject absent alliance");
        check(Double.isNaN(Shooter.nearest_hive(Double.NaN, 9, Alliance.RED).distance), "reject invalid pose");
        check(!Shooter.calculate_shot_solution(10, Double.MAX_VALUE, 0, 0, 0, 0, 45).valid,
                "finite coefficient overflow rejects the pair before clipping");
        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!Shooter.calculate_shot_solution(fault).valid, "reject invalid model distance");
            check(Double.isNaN(Shooter.hood_angle_to_servo_position(fault)), "reject invalid hood input");
        }

        double[] position = {Double.NaN};
        int[] writes = {0};
        Servo servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[]{Servo.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("setPosition")) { position[0] = (double) values[0]; writes[0]++; }
                    return null;
                });
        double[] velocity = {Shooter.STANDARD_RPM * Shooter.ENCODER_TICKS_PER_REV / 60.0};
        double[] power = {0};
        double[] power2 = {0};
        int[] reads = {0};
        DcMotorEx motor = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getVelocity")) { reads[0]++; return velocity[0]; }
                    if (method.getName().equals("setPower")) {
                        power[0] = (double) values[0];
                        check(Double.isFinite(power[0]) && Math.abs(power[0]) <= 1, "only finite clipped power reaches motor");
                    }
                    return null;
                });
        FakeIntake intake = new FakeIntake();
        Shooter shooter = new Shooter();
        field(shooter, "servoHood", new CachedServo(servo, Shooter.HOOD_POSITION_EPSILON));
        field(shooter, "shooterMotorLeft", new CachedMotor(motor, 0.002));
        DcMotorEx motor2 = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getVelocity")) return velocity[0];
                    if (method.getName().equals("setPower")) {
                        power2[0] = (double) values[0];
                        close(power2[0], power[0]);
                    }
                    return null;
                });
        field(shooter, "shooterMotorRight", new CachedMotor(motor2, 0.002));
        field(shooter, "intakeSubsystem", intake);
        shooter.set_alliance(Alliance.RED);
        shooter.set_bypass_enabled(true);
        close(position[0], Shooter.hood_angle_to_servo_position(Shooter.DEFAULT_HOOD_ANGLE_DEG));
        check(!shooter.hood_ready(), "first command must settle");
        elapsed(shooter);
        check(shooter.hood_ready(), "settled command ready");
        int originalWrites = writes[0];
        shooter.set_hood_angle(Shooter.DEFAULT_HOOD_ANGLE_DEG);
        check(writes[0] == originalWrites && shooter.hood_ready(), "unchanged target neither writes nor restarts delay");
        shooter.adjust_hood_angle(1);
        check(writes[0] == originalWrites + 1, "INIT setter immediately writes servo");
        check(!shooter.hood_ready(), "movement restarts delay");
        shooter.shoot();
        shooter.update(9, 9);
        check(!intake.feedAllowed, "feed blocked during hood movement at correct RPM");
        elapsed(shooter);
        shooter.update(9, 9);
        check(intake.feedAllowed, "feed enabled at correct RPM after settling");
        int sampleReads = reads[0];
        shooter.ready_to_feed();
        shooter.hood_ready();
        shooter.get_current_rpm();
        check(reads[0] == sampleReads, "readiness and telemetry reuse the encoder sample");
        velocity[0] = 0;
        shooter.update(9, 9);
        check(intake.feedAllowed && shooter.shot_in_progress(), "RPM drop cannot interrupt an active shot");
        close(power2[0], 1);
        double lastRpm = shooter.get_current_rpm();
        double lastPower = power[0];
        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            velocity[0] = fault;
            int before = reads[0];
            shooter.update(9, 9);
            check(reads[0] == before + 1, "exactly one encoder read each update");
            check(intake.feedAllowed && !shooter.velocity_valid() && power[0] == lastPower,
                    "active shot retains feeding and previous finite power on invalid encoder sample");
            close(power2[0], lastPower);
            close(shooter.get_current_rpm(), lastRpm);
        }
        shooter.stop_shooting();
        shooter.prepare_shot();
        shooter.set_bypass_enabled(false);
        shooter.update(Double.NaN, 9);
        check(!intake.feedAllowed && !shooter.target_available(), "invalid model pose blocks a new shot");
        close(shooter.get_target_hood_angle(), Shooter.DEFAULT_HOOD_ANGLE_DEG + 1);
        check(writes[0] == originalWrites + 1, "invalid model holds last servo command");
        shooter.set_bypass_enabled(true);
        velocity[0] = Shooter.STANDARD_RPM * Shooter.ENCODER_TICKS_PER_REV / 60.0;
        shooter.shoot();
        shooter.update(Double.NaN, 9);
        check(intake.feedAllowed, "fixed mode can begin without valid localization");
        shooter.stop_shooting();
        shooter.prepare_shot();
        shooter.set_bypass_enabled(false);
        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double heldRpm = shooter.get_target_rpm(), heldHood = shooter.get_target_hood_angle();
            shooter.update(fault, 9);
            check(!shooter.target_available() && !intake.feedAllowed && power[0] == 0,
                    "invalid localization stops preparation and blocks a new shot");
            close(power2[0], 0);
            close(shooter.get_target_rpm(), heldRpm);
            close(shooter.get_target_hood_angle(), heldHood);
        }
        shooter.update(9, 9, true);
        elapsed(shooter);
        shooter.update(9, 9, true);
        check(shooter.ready_to_feed() && !intake.feedAllowed && shooter.velocity_valid(),
                "valid samples recover readiness without automatically requesting feed");
        shooter.shoot();
        shooter.update(9, 9, true);
        lastPower = power[0];
        field(shooter, "integralSum", Double.POSITIVE_INFINITY);
        shooter.update(9, 9, true);
        check(power[0] == lastPower && intake.feedAllowed && !shooter.ready_to_feed(),
                "active shot retains its accepted command after invalid PIDF output");
        close(power2[0], lastPower);
        shooter.update(9, 9, true);
        check(intake.feedAllowed && shooter.ready_to_feed(), "controller recovers without interrupting the shot");
        shooter.stop();
        close(power[0], 0);
        close(power2[0], 0);
        check_intake_samples();
        System.out.println("Passed " + checks + " hood checks");
    }

    private static void elapsed(Shooter shooter) throws Exception {
        field(shooter, "lastHoodCommandNanos", System.nanoTime() - (Shooter.HOOD_SETTLE_TIME_MS + 1) * 1_000_000L);
    }

    private static void field(Object shooter, String name, Object value) throws Exception {
        Field field = shooter.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(shooter, value);
    }

    private static void check_intake_samples() throws Exception {
        long[] now = {0};
        long spacing = 150_000_000L;
        Intake intake = new Intake(() -> now[0]);
        double[] lower = {Double.NaN}, upper = {Double.POSITIVE_INFINITY};
        int[] reads = {0, 0};
        long[] readNanos = {0, 0};
        ColorRangeSensor low = distance_sensor(lower, reads, 0, now, readNanos);
        ColorRangeSensor high = distance_sensor(upper, reads, 1, now, readNanos);
        field(intake, "colorSensorLow", low);
        field(intake, "colorSensorHigh", high);
        check(!intake.loaded() && !intake.sensor_readings_valid(), "invalid first intake samples cannot report loaded");
        close(intake.get_lower_distance().mm(), 0);
        close(intake.get_upper_distance().mm(), 0);
        check(reads[0] == 1 && reads[1] == 0, "first read only samples the lower sensor");
        now[0] += spacing - 1;
        check(!intake.sensor_readings_valid() && reads[1] == 0, "upper read waits its full offset");
        now[0]++;
        check(!intake.sensor_readings_valid() && reads[1] == 1, "upper read follows without renewing lower");
        lower[0] = 10; upper[0] = 20;
        now[0] += spacing;
        check(!intake.sensor_readings_valid(), "both sensors must recover");
        now[0] += spacing;
        check(intake.sensor_readings_valid(), "intake samples recover");
        close(intake.get_lower_distance().mm(), 10);
        close(intake.get_upper_distance().mm(), 20);
        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            lower[0] = fault;
            now[0] += spacing;
            check(!intake.loaded() && !intake.sensor_readings_valid(), "rejected intake sample is unavailable");
            close(intake.get_lower_distance().mm(), 10);
            now[0] += spacing;
            check(!intake.sensor_readings_valid(), "a valid upper sample cannot mask a lower fault");
        }
        intake.get_sensor_telemetry();
        check(reads[0] == 5 && reads[1] == 5, "intake telemetry does not reread samples");

        lower[0] = -10;
        upper[0] = -20;
        now[0] += spacing;
        check(!intake.loaded(), "an empty upper sample cannot report full");
        now[0] += spacing;
        check(intake.loaded(), "both fresh simulated occupied samples report full");
        now[0] += 400_000_000L;
        check(!intake.loaded() && !intake.sensor_readings_valid(), "a stall leaves the other sensor sample stale");
        check(reads[0] == 7 && reads[1] == 6, "missed deadlines cause only one read");
        now[0] += spacing;
        check(intake.loaded(), "both samples fresh again after the next read");

        now[0] += 260_000_000L;
        check(!intake.loaded(), "samples that are individually fresh but too far apart cannot latch full");
        now[0] += spacing;
        check(intake.loaded(), "the next on-time pair restores full detection");

        DcMotorEx transfer = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[]{DcMotorEx.class}, (proxy, method, values) -> null);
        field(intake, "transferMotor", new CachedMotor(transfer, 0.01));
        intake.transfer_forward();
        intake.stop_feed();
        check(!intake.loaded() && !intake.sensor_readings_valid(), "release discards the pre-shot upper sample");
        close(intake.get_transfer_power(), 0);
        now[0] += spacing;
        check(intake.loaded(), "release accepts a pair only once both reads are new");

        readNanos[0] = 210_000_000L;
        now[0] += spacing;
        int upperReads = reads[1];
        check(!intake.loaded() && !intake.sensor_readings_valid(), "a slow lower read can age out the upper sample");
        check(reads[1] == upperReads, "slow lower read does not immediately trigger the upper read");
    }

    private static ColorRangeSensor distance_sensor(double[] distance, int[] reads, int index,
                                                    long[] now, long[] readNanos) {
        return (ColorRangeSensor) Proxy.newProxyInstance(ColorRangeSensor.class.getClassLoader(),
                new Class<?>[]{ColorRangeSensor.class}, (proxy, method, values) -> {
                    if (method.getName().equals("getDistance")) {
                        reads[index]++;
                        now[0] += readNanos[index];
                        return distance[0];
                    }
                    return null;
                });
    }

    private static void close(double actual, double expected) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-9, "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeIntake extends Intake {
        private boolean feedAllowed, requested;
        @Override public void request_shot() { requested = true; }
        @Override public boolean is_shooting_requested() { return requested; }
        @Override public boolean is_scoring_requested() { return false; }
        @Override public boolean loaded() { return false; }
        @Override public void stop_feed() { requested = false; super.stop_feed(); }
        @Override public void update_feed(boolean feedAllowed, boolean loaded) { this.feedAllowed = feedAllowed; }
    }
}
