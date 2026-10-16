package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Standalone JVM checks for the tuner's dual-motor commands and gamepad toggle. */
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
        System.out.println("Passed " + checks + " shooter motor tuner checks");
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

    private static void call(Object object, String name) throws Exception {
        Method method = ShooterPidfTuner.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(object);
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
}
