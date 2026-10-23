package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.control.ShooterSelection;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Standalone JVM checks for the tuner's dual-motor commands and gamepad toggle. */
public final class ShooterMotorTunerChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        tuner.gamepad1 = new Gamepad();
        tuner.gamepad2 = new Gamepad();
        Gamepad buttons = new Gamepad();
        double[] velocity = {0, 0};
        double[][] pidf = {new double[4], new double[4]};
        set(tuner, "shooterMotorLeft", motor(velocity, pidf, 0));
        set(tuner, "shooterMotorRight", motor(velocity, pidf, 1));
        set(tuner, "validTargets", true);
        call(tuner, "apply_pidf");
        for (int i = 0; i < 4; i++) close(pidf[0][i], pidf[1][i]);

        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        buttons.start = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        double target = 4000 * Shooter.ENCODER_TICKS_PER_REV / 60.0;
        close(velocity[0], target); close(velocity[1], target);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], target);
        buttons.start = false;
        buttons.x = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], 0);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], 0);
        buttons.x = false;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        buttons.x = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], target);

        command(tuner, false);
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "validTargets", false);
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "validTargets", true);
        buttons.start = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        input_boundaries();
        System.out.println("Passed " + checks + " shooter motor tuner checks");
    }

    private static void input_boundaries() throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        tuner.gamepad1 = new Gamepad();
        tuner.gamepad2 = new Gamepad();
        Gamepad one = new Gamepad(), two = new Gamepad();
        one.start = true;
        two.y = true;
        two.dpad_up = true;
        tuner.gamepad1.copy(one);
        tuner.gamepad2.copy(two);
        one.start = false;
        two.y = false;
        two.dpad_up = false;
        tuner.gamepad1.copy(one);
        tuner.gamepad2.copy(two);
        call(tuner, "start_controls");
        call(tuner, "update_controls");
        call(tuner, "update_model_controls");
        close((boolean) get(tuner, "shooterEnabled") ? 1 : 0, 0);
        close((boolean) get(tuner, "distanceModelEnabled") ? 1 : 0, 0);
        close((double) get(tuner, "manualHoodAngle"), Shooter.DEFAULT_HOOD_ANGLE_DEG);

        one.start = true;
        two.y = true;
        tuner.gamepad1.copy(one);
        tuner.gamepad2.copy(two);
        call(tuner, "start_controls");
        call(tuner, "update_controls");
        call(tuner, "update_model_controls");
        close((boolean) get(tuner, "shooterEnabled") ? 1 : 0, 1);
        close((boolean) get(tuner, "distanceModelEnabled") ? 1 : 0, 1);
        two.dpad_up = true;
        tuner.gamepad2.copy(two);
        call(tuner, "update_model_controls");
        two.y = false;
        tuner.gamepad2.copy(two);
        call(tuner, "update_model_controls");
        two.y = true;
        tuner.gamepad2.copy(two);
        call(tuner, "update_model_controls");
        close((boolean) get(tuner, "distanceModelEnabled") ? 1 : 0, 0);
        close((double) get(tuner, "manualHoodAngle"), Shooter.DEFAULT_HOOD_ANGLE_DEG);
        two.dpad_up = false;
        tuner.gamepad2.copy(two);
        call(tuner, "update_model_controls");
        two.dpad_up = true;
        tuner.gamepad2.copy(two);
        call(tuner, "update_model_controls");
        close((double) get(tuner, "manualHoodAngle"), Shooter.DEFAULT_HOOD_ANGLE_DEG + 1);

        Shooter shooter = new Shooter();
        shooter.set_bypass_enabled(false);
        ShooterSelection selection = new ShooterSelection();
        Telemetry telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
                new Class<?>[]{Telemetry.class}, (proxy, method, values) -> null);
        Gamepad input = new Gamepad(), snapshot = new Gamepad();
        snapshot.right_bumper = true;
        snapshot.dpad_up = true;
        input.copy(snapshot);
        selection.update(input, shooter, telemetry);
        snapshot.x = true;
        input.copy(snapshot);
        selection.update(input, shooter, telemetry);
        close(shooter.get_fixed_rpm(), Shooter.STANDARD_RPM);
        close(shooter.get_fixed_hood_angle(), Shooter.DEFAULT_HOOD_ANGLE_DEG);
        selection.update(input, shooter, telemetry);
        close(shooter.is_bypass_enabled() ? 1 : 0, 1);
        snapshot.right_bumper = false;
        snapshot.dpad_up = false;
        input.copy(snapshot);
        selection.update(input, shooter, telemetry);
        snapshot.right_bumper = true;
        snapshot.dpad_up = true;
        input.copy(snapshot);
        selection.update(input, shooter, telemetry);
        close(shooter.get_fixed_rpm(), Shooter.STANDARD_RPM + 100);
        close(shooter.get_fixed_hood_angle(), Shooter.DEFAULT_HOOD_ANGLE_DEG + 1);
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
        field.setAccessible(true);
        return field.get(object);
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
