package org.firstinspires.ftc.teamcode.core.control;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Verifies independent shutdown writes when an earlier mechanism or motor throws. */
public final class CompetitionShutdownChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        cleanup_aggregation();
        shooter_motor_failure();
        for (boolean stopFeed : new boolean[] { false, true }) intake_motor_failure(stopFeed);
        flower_servo_failure();
        robot_mechanism_failure();
        System.out.println("Competition shutdown checks passed: " + checks);
    }

    private static void cleanup_aggregation() {
        RuntimeException primary = new IllegalStateException("original");
        Error firstCleanup = new AssertionError("first cleanup");
        RuntimeException secondCleanup = new IllegalArgumentException("second cleanup");
        int[] ran = { 0 };
        OpModeCleanup.run(primary,
                () -> { ran[0]++; throw firstCleanup; },
                () -> { ran[0]++; throw secondCleanup; },
                () -> ran[0]++);
        check(ran[0] == 3, "every cleanup step runs after RuntimeException and Error");
        check(primary.getSuppressed().length == 2 && primary.getSuppressed()[0] == firstCleanup
                && primary.getSuppressed()[1] == secondCleanup, "original failure retains cleanup failures in order");
        Throwable thrown = failure(() -> OpModeCleanup.run(null,
                () -> { throw firstCleanup; },
                () -> { throw secondCleanup; },
                () -> ran[0]++));
        check(thrown == firstCleanup && firstCleanup.getSuppressed()[0] == secondCleanup,
                "ordinary shutdown surfaces its first failure and retains the rest");
        check(ran[0] == 4, "ordinary shutdown also attempts every step");
    }

    private static void shooter_motor_failure() throws Exception {
        Motor left = new Motor(), right = new Motor();
        int[] intakeStops = { 0 };
        Shooter shooter = new Shooter();
        set(shooter, "shooterMotorLeft", left.cached);
        set(shooter, "shooterMotorRight", right.cached);
        set(shooter, "intakeSubsystem", new Intake() {
            @Override public void stop() { intakeStops[0]++; }
        });
        left.fail = true;
        Throwable thrown = failure(shooter::stop);
        check(thrown == left.failure, "left shooter write failure is retained");
        check(left.zeroAttempts == 1 && right.zeroAttempts == 1 && right.power == 0,
                "right shooter motor receives zero after left motor write throws");
        check(intakeStops[0] == 1, "shooter stop still stops intake after a flywheel write fails");
        check(shooter.get_state() == Shooter.State.FLOAT, "shooting latch is cleared before shutdown writes");
    }

    private static void intake_motor_failure(boolean stopFeed) throws Exception {
        Motor intakeMotor = new Motor(), transferMotor = new Motor();
        Intake intake = new Intake();
        set(intake, "intakeMotor", intakeMotor.cached);
        set(intake, "transferMotor", transferMotor.cached);
        intake.request_shot();
        intakeMotor.fail = true;
        Throwable thrown = failure(stopFeed ? intake::stop_feed : intake::stop);
        check(thrown == intakeMotor.failure, "intake zero failure is retained");
        check(intakeMotor.zeroAttempts == 1 && transferMotor.zeroAttempts == 1 && transferMotor.power == 0,
                "transfer receives zero after intake write throws for " + (stopFeed ? "stop_feed" : "stop"));
        check(!intake.is_shooting_requested(), "shot request is cleared before potentially throwing motor writes");
    }

    private static void robot_mechanism_failure() throws Exception {
        Robot robot = new Robot();
        int[] stopped = { 0, 0, 0 };
        RuntimeException shooterFailure = new IllegalStateException("shooter stop failed");
        set(robot, "shooterSubsystem", new Shooter() {
            @Override public void stop() { stopped[0]++; throw shooterFailure; }
        });
        set(robot, "intakeSubsystem", new Intake() {
            @Override public void stop() { stopped[1]++; }
        });
        set(robot, "flowerIntakeSubsystem", new FlowerIntake() {
            @Override public void up() { stopped[2]++; }
        });
        check(failure(robot::stop_robot) == shooterFailure, "Robot retains the original mechanism shutdown fault");
        check(stopped[0] == 1 && stopped[1] == 1 && stopped[2] == 1,
                "Robot independently attempts shooter, intake and flower shutdown");
    }

    private static void flower_servo_failure() throws Exception {
        RuntimeException leftFailure = new IllegalStateException("left flower servo failed");
        int[] upAttempts = { 0, 0 };
        double[] positions = { 0, 0 };
        FlowerIntake flower = new FlowerIntake();
        for (int index = 0; index < 2; index++) {
            final int side = index;
            Servo servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[] { Servo.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("setPosition")) {
                            double requested = (double) args[0];
                            if (requested == 1) {
                                upAttempts[side]++;
                                if (side == 0) throw leftFailure;
                            }
                            positions[side] = requested;
                        }
                        if (method.getReturnType() == double.class) return positions[side];
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
            CachedServo cached = new CachedServo(servo, 0.005);
            cached.set_position(0);
            set(flower, side == 0 ? "flowerServoLeft" : "flowerServoRight", cached);
        }
        check(failure(flower::up) == leftFailure, "flower up retains the original left servo write failure");
        check(upAttempts[0] == 1 && upAttempts[1] == 1 && positions[1] == 1,
                "right flower servo receives UP after the left servo command throws");
    }

    private static final class Motor {
        final RuntimeException failure = new IllegalStateException("motor stop failed");
        final CachedMotor cached;
        double power;
        int zeroAttempts;
        boolean fail;
        Motor() {
            DcMotorEx motor = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                    new Class<?>[] { DcMotorEx.class }, (proxy, method, args) -> {
                        if (method.getName().equals("setPower")) {
                            double requested = (double) args[0];
                            if (requested == 0) zeroAttempts++;
                            if (fail && requested == 0) throw failure;
                            power = requested;
                        }
                        if (method.getReturnType() == double.class) return 0.0;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
            cached = new CachedMotor(motor, 0.01);
            cached.set_power(0.7);
        }
    }

    private static Throwable failure(Runnable operation) {
        try { operation.run(); }
        catch (Throwable thrown) { return thrown; }
        throw new AssertionError("expected shutdown failure");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
