package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.pedropathing.localization.Localizer;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
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
        int[] velocityWrites = {0, 0};
        boolean[] failNextWrite = {false, false};
        double[][] pidf = {new double[4], new double[4]};
        set(tuner, "shooterMotorLeft", motor(velocity, velocityWrites, failNextWrite, pidf, 0));
        set(tuner, "shooterMotorRight", motor(velocity, velocityWrites, failNextWrite, pidf, 1));
        set(tuner, "validTargets", true);
        call(tuner, "apply_pidf");
        for (int i = 0; i < 4; i++) close(pidf[0][i], pidf[1][i]);

        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        buttons.start = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        double target = (double) get(tuner, "commandedRpm") * Shooter.ENCODER_TICKS_PER_REV / 60.0;
        close(velocity[0], target); close(velocity[1], target);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], target);
        close(velocityWrites[0], 2); close(velocityWrites[1], 2);
        buttons.start = false;
        buttons.x = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], target); close(velocity[1], 0);
        close(velocityWrites[0], 2); close(velocityWrites[1], 3);
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
        close(velocityWrites[0], 3); close(velocityWrites[1], 5);
        set(tuner, "validTargets", false);
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "validTargets", true);
        buttons.start = true;
        tuner.gamepad1.copy(buttons);
        call(tuner, "update_controls");
        command(tuner, true);
        close(velocity[0], 0); close(velocity[1], 0);
        velocity_cache(tuner, velocity, velocityWrites, failNextWrite, target);
        input_boundaries();
        velocity_cache_invalidation();
        manual_tuning_without_pinpoint(false);
        manual_tuning_without_pinpoint(true);
        distance_rpm_without_hood();
        System.out.println("Passed " + checks + " shooter motor tuner checks");
    }

    private static void velocity_cache(ShooterPidfTuner tuner, double[] velocity, int[] writes,
                                       boolean[] failNextWrite, double target) throws Exception {
        set(tuner, "shooterEnabled", true);
        double rpm = (double) get(tuner, "targetRpm");
        set(tuner, "commandedRpm", rpm);
        command(tuner, true, 0);
        int leftWrites = writes[0], rightWrites = writes[1];
        command(tuner, true, 249_999_999L);
        close(writes[0], leftWrites); close(writes[1], rightWrites);
        long refresh = 250_000_000L;
        command(tuner, true, refresh);
        close(writes[0], ++leftWrites); close(writes[1], ++rightWrites);

        call(tuner, "apply_pidf");
        command(tuner, true, refresh + 1);
        close(writes[0], ++leftWrites); close(writes[1], ++rightWrites);

        set(tuner, "commandedRpm", rpm + 100.0);
        failNextWrite[0] = true;
        try {
            command(tuner, true, refresh + 2);
            throw new AssertionError("Expected motor write failure");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IllegalStateException)) throw e;
        }
        close(writes[0], ++leftWrites);
        set(tuner, "commandedRpm", rpm);
        command(tuner, true, refresh + 3);
        close(writes[0], ++leftWrites); close(writes[1], rightWrites);
        close(velocity[0], target); close(velocity[1], target);

        set(tuner, "commandedRpm", Double.NaN);
        if (command(tuner, true, refresh + 4)) throw new AssertionError("Invalid target accepted");
        close(velocity[0], 0); close(velocity[1], 0);
        set(tuner, "commandedRpm", rpm);
        command(tuner, true, refresh + 5);
        close(velocity[0], target); close(velocity[1], target);
    }

    private static void input_boundaries() throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        tuner.gamepad1 = new Gamepad();
        tuner.gamepad2 = new Gamepad();
        set(tuner, "localizer", Proxy.newProxyInstance(Localizer.class.getClassLoader(),
                new Class<?>[]{Localizer.class}, (proxy, method, values) -> null));
        set(tuner, "hoodServo", new CachedServo(servo(new int[1]), Shooter.HOOD_POSITION_EPSILON));
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

    private static void velocity_cache_invalidation() throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        int[] writes = {0, 0};
        for (int index = 0; index < writes.length; index++) {
            final int motorIndex = index;
            DcMotorEx hardware = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                    new Class<?>[]{DcMotorEx.class}, (proxy, method, values) -> {
                        if (method.getName().equals("setVelocity")) writes[motorIndex]++;
                        return null;
                    });
            set(tuner, index == 0 ? "shooterMotorLeft" : "shooterMotorRight", new CachedMotor(hardware, 0.002));
        }
        set(tuner, "shooterEnabled", true);
        set(tuner, "validTargets", true);
        command(tuner, true);
        command(tuner, true);
        close(writes[0], 1);
        close(writes[1], 1);
        call(tuner, "apply_pidf");
        command(tuner, true);
        close(writes[0], 2);
        close(writes[1], 2);
        command(tuner, true);
        close(writes[0], 2);
        close(writes[1], 2);
        command(tuner, false);
        close(writes[0], 3);
        close(writes[1], 3);
    }

    private static void manual_tuning_without_pinpoint(boolean withHood) throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        tuner.gamepad1 = new Gamepad();
        tuner.gamepad2 = new Gamepad();
        double[] velocity = new double[2];
        double[][] pidf = new double[2][4];
        DcMotorEx left = motor(velocity, pidf, 0).raw();
        DcMotorEx right = motor(velocity, pidf, 1).raw();
        int[] hoodWrites = new int[1];
        Servo hood = withHood ? servo(hoodWrites) : null;
        tuner.hardwareMap = new HardwareMap(null, null) {
            @Override public <T> T get(Class<? extends T> type, String name) {
                if (name.equals("shooterMotorLeft")) return type.cast(left);
                if (name.equals("shooterMotorRight")) return type.cast(right);
                throw new AssertionError("Unexpected required hardware " + name);
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                if (name.equals("servoHood")) return type.cast(hood);
                if (name.equals("pinpoint")) return null;
                throw new AssertionError("Unexpected optional hardware " + name);
            }
        };
        Class<?> opModeState = Class.forName("com.qualcomm.robotcore.eventloop.opmode.OpModeInternal");
        Field started = opModeState.getDeclaredField("isStarted");
        Field stopped = opModeState.getDeclaredField("stopRequested");
        started.setAccessible(true);
        stopped.setAccessible(true);
        int[] updates = new int[1];
        tuner.telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
                new Class<?>[]{Telemetry.class}, (proxy, method, values) -> {
                    if (!method.getName().equals("update")) return null;
                    if (++updates[0] == 1) {
                        started.setBoolean(tuner, true);
                    } else if (updates[0] == 2) {
                        Gamepad one = new Gamepad(), two = new Gamepad();
                        one.start = true;
                        one.y = true;
                        two.y = true;
                        two.a = true;
                        two.dpad_up = true;
                        tuner.gamepad1.copy(one);
                        tuner.gamepad2.copy(two);
                    } else {
                        close((double) get(tuner, "targetRpm"), 1100.0);
                        double target = 1100.0 * 28.0 / 60.0; // AndyMark encoder on the 1:1 flywheel drive.
                        close(velocity[0], target);
                        close(velocity[1], target);
                        close((boolean) get(tuner, "distanceModelEnabled") ? 1 : 0, 0);
                        if (!get(tuner, "captureMessage").equals("Sample capture requires Pinpoint and servoHood")) {
                            throw new AssertionError("Missing hardware must prevent sample capture");
                        }
                        stopped.setBoolean(tuner, true);
                    }
                    return true;
                });
        tuner.runOpMode();
        close(updates[0], 3);
        close(hoodWrites[0], withHood ? 2 : 0);
        close(velocity[0], 0);
        close(velocity[1], 0);
    }

    private static void distance_rpm_without_hood() throws Exception {
        ShooterPidfTuner tuner = new ShooterPidfTuner();
        set(tuner, "distanceModelEnabled", true);
        ((double[]) get(tuner, "hoodCoefficients"))[0] = Double.NaN;
        Shooter.HiveDistance hive = new Shooter.HiveDistance(new Shooter.HivePosition("test", 0, 0, 0), 40);
        Method update = ShooterPidfTuner.class.getDeclaredMethod("update_targets", Shooter.HiveDistance.class);
        update.setAccessible(true);
        close((boolean) update.invoke(tuner, hive) ? 1 : 0, 1);
        close((double) get(tuner, "commandedRpm"),
                Shooter.calculate_rpm(40, Shooter.DISTANCE_A, Shooter.DISTANCE_B, Shooter.DISTANCE_C));
    }

    private static Servo servo(int[] writes) {
        return (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[]{Servo.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("setPosition")) writes[0]++;
                    return null;
                });
    }

    private static CachedMotor motor(double[] velocity, double[][] pidf, int index) {
        return motor(velocity, new int[velocity.length], new boolean[velocity.length], pidf, index);
    }

    private static CachedMotor motor(double[] velocity, int[] writes, boolean[] failNextWrite,
                                     double[][] pidf, int index) {
        return new CachedMotor((DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[]{DcMotorEx.class}, (proxy, method, values) -> {
                    if (method.getName().equals("setVelocity")) {
                        velocity[index] = (double) values[0];
                        writes[index]++;
                        if (failNextWrite[index]) {
                            failNextWrite[index] = false;
                            throw new IllegalStateException("Simulated motor write failure");
                        }
                    }
                    if (method.getName().equals("getVelocity")) return velocity[index];
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
        command(tuner, valid, 0);
    }

    private static boolean command(ShooterPidfTuner tuner, boolean valid, long now) throws Exception {
        Method method = ShooterPidfTuner.class.getDeclaredMethod("command_flywheels", boolean.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(tuner, valid, now);
    }

    private static void close(double actual, double expected) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1e-9) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
