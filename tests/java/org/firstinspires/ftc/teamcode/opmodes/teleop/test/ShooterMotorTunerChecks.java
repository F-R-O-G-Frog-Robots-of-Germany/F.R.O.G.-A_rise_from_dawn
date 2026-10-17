package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Standalone JVM checks for the tuner's motor controls and applied hood commands. */
public final class ShooterMotorTunerChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        tuner.gamepad1 = new Gamepad();
        double[] velocity = {0, 0};
        double[][] pidf = {new double[4], new double[4]};
        set(tuner, "shooterMotorLeft", motor(velocity, pidf, 0));
        set(tuner, "shooterMotorRight", motor(velocity, pidf, 1));
        set(tuner, "validTargets", true);
        call(tuner, "apply_pidf");
        for (int i = 0; i < 4; i++) close(pidf[0][i], pidf[1][i]);

        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        tuner.gamepad1.start = true;
        call(tuner, "update_controls");
        command(tuner, true);
        double target = 4000 * Shooter.ENCODER_TICKS_PER_REV / 60.0;
        close(velocity[0], target); close(velocity[1], target);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], target);
        tuner.gamepad1.start = false;
        tuner.gamepad1.x = true;
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], 0);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], 0);
        tuner.gamepad1.x = false;
        call(tuner, "update_controls");
        tuner.gamepad1.x = true;
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], target);

        command(tuner, false);
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "validTargets", false);
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "validTargets", true);
        tuner.gamepad1.start = true;
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        hood_commands();
        System.out.println("Passed " + checks + " shooter motor tuner checks");
    }

    private static void hood_commands() throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        int[] writes = {0};
        double[] position = {Double.NaN};
        Servo servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(),
                new Class<?>[]{Servo.class}, (proxy, method, values) -> {
                    if (method.getName().equals("setPosition")) {
                        position[0] = (double) values[0];
                        writes[0]++;
                    }
                    return null;
                });
        set(tuner, "hoodServo", servo);
        check(!(boolean) call(tuner, "hood_settled"), "uncommanded hood is not settled");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!command_hood(tuner, invalid), "invalid first hood angle rejected");
            check(writes[0] == 0 && !(boolean) get(tuner, "hoodCommanded"),
                    "invalid first angle sends no command");
            check((long) get(tuner, "lastHoodCommandNanos") == 0,
                    "invalid first angle does not start settling timer");
        }

        double middleAngle = (Shooter.HOOD_MIN_ANGLE_DEG + Shooter.HOOD_MAX_ANGLE_DEG) / 2;
        check(command_hood(tuner, middleAngle), "first finite hood angle accepted");
        check(writes[0] == 1 && (boolean) get(tuner, "hoodCommanded"),
                "first finite angle writes the raw servo");
        close(position[0], Shooter.hood_angle_to_servo_position(middleAngle));
        close((double) get(tuner, "appliedHoodPosition"), position[0]);
        close((double) get(tuner, "appliedHoodAngle"), middleAngle);

        long settledAt = System.nanoTime() - (Shooter.HOOD_SETTLE_TIME_MS + 1) * 1_000_000L;
        set(tuner, "lastHoodCommandNanos", settledAt);
        check((boolean) call(tuner, "hood_settled"), "elapsed applied command is settled");
        check(command_hood(tuner, middleAngle), "unchanged hood angle remains accepted");
        check(writes[0] == 1 && (long) get(tuner, "lastHoodCommandNanos") == settledAt,
                "unchanged angle neither writes nor restarts settling timer");

        double anglePerThreshold = Shooter.HOOD_POSITION_EPSILON
                * (Shooter.HOOD_MAX_ANGLE_DEG - Shooter.HOOD_MIN_ANGLE_DEG)
                / Math.abs(Shooter.HOOD_SERVO_AT_MAX_ANGLE - Shooter.HOOD_SERVO_AT_MIN_ANGLE);
        for (double fraction : new double[]{0.25, 0.5, 0.75}) {
            check(command_hood(tuner, middleAngle + fraction * anglePerThreshold),
                    "subepsilon hood change remains accepted");
            check(writes[0] == 1 && (long) get(tuner, "lastHoodCommandNanos") == settledAt,
                    "subepsilon change neither writes nor restarts settling timer");
            close((double) get(tuner, "appliedHoodAngle"), middleAngle);
            close((double) get(tuner, "appliedHoodPosition"), position[0]);
            check((boolean) call(tuner, "hood_settled"), "skipped change keeps settled state");
        }

        double movedAngle = middleAngle + 1.25 * anglePerThreshold;
        long beforeCommand = System.nanoTime();
        check(command_hood(tuner, movedAngle), "cumulative threshold crossing accepted");
        long afterCommand = System.nanoTime();
        check(writes[0] == 2, "cumulative movement is compared with last applied command");
        close(position[0], Shooter.hood_angle_to_servo_position(movedAngle));
        close((double) get(tuner, "appliedHoodPosition"), position[0]);
        close((double) get(tuner, "appliedHoodAngle"), movedAngle);
        long commandedAt = (long) get(tuner, "lastHoodCommandNanos");
        check(commandedAt >= beforeCommand && commandedAt <= afterCommand,
                "accepted movement restarts settling timer when servo is written");

        set(tuner, "lastHoodCommandNanos", settledAt);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!command_hood(tuner, invalid), "invalid active hood angle rejected");
            check(writes[0] == 2 && (long) get(tuner, "lastHoodCommandNanos") == settledAt,
                    "invalid active angle retains applied command and settling timer");
            close((double) get(tuner, "appliedHoodAngle"), movedAngle);
            close((double) get(tuner, "appliedHoodPosition"), position[0]);
        }

        check(command_hood(tuner, -Double.MAX_VALUE), "finite angle below range accepted");
        check(writes[0] == 3, "lower endpoint is written once");
        close(position[0], Shooter.HOOD_SERVO_AT_MIN_ANGLE);
        close((double) get(tuner, "appliedHoodAngle"), Shooter.HOOD_MIN_ANGLE_DEG);
        set(tuner, "lastHoodCommandNanos", settledAt);
        check(command_hood(tuner, Shooter.HOOD_MIN_ANGLE_DEG - 1), "repeated lower endpoint accepted");
        check(writes[0] == 3 && (long) get(tuner, "lastHoodCommandNanos") == settledAt,
                "angles clipping to same lower endpoint do not restart settling");
        check(command_hood(tuner, Double.MAX_VALUE), "finite angle above range accepted");
        check(writes[0] == 4, "upper endpoint is written once");
        close(position[0], Shooter.HOOD_SERVO_AT_MAX_ANGLE);
        close((double) get(tuner, "appliedHoodAngle"), Shooter.HOOD_MAX_ANGLE_DEG);
        set(tuner, "lastHoodCommandNanos", settledAt);
        check(command_hood(tuner, Shooter.HOOD_MAX_ANGLE_DEG + 1), "repeated upper endpoint accepted");
        check(writes[0] == 4 && (long) get(tuner, "lastHoodCommandNanos") == settledAt,
                "angles clipping to same upper endpoint do not restart settling");
    }

    private static CachedMotor motor(double[] velocity, double[][] pidf, int index) {
        return new CachedMotor((DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[]{DcMotorEx.class}, (proxy, method, values) -> {
                    if (method.getName().equals("setVelocity")) velocity[index] = (double) values[0];
                    if (method.getName().equals("setVelocityPIDFCoefficients")) {
                        for (int i = 0; i < 4; i++) pidf[index][i] = (double) values[i];
                    }
                    return null;
                }), 0.002);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = ShooterPidfTuner.class.getDeclaredField(name);
        field.setAccessible(true); field.set(object, value);
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = ShooterPidfTuner.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(object);
    }

    private static Object call(Object object, String name) throws Exception {
        Method method = ShooterPidfTuner.class.getDeclaredMethod(name);
        method.setAccessible(true); return method.invoke(object);
    }

    private static boolean command_hood(ShooterPidfTuner tuner, double angle) throws Exception {
        set(tuner, "commandedHoodAngle", angle);
        return (boolean) call(tuner, "command_hood");
    }

    private static void command(ShooterPidfTuner tuner, boolean valid) throws Exception {
        Method method = ShooterPidfTuner.class.getDeclaredMethod("command_flywheels", boolean.class);
        method.setAccessible(true); method.invoke(tuner, valid);
    }

    private static void close(double actual, double expected) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1e-9) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
