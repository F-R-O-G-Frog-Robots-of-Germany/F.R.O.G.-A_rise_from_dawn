package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** One production loop with real Pedro calculations and simulated localization/drive. */
public final class AutonomousPoseFaultChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Pose[] faults = {null,
                new Pose(Double.NaN, 10, 0), new Pose(Double.POSITIVE_INFINITY, 10, 0),
                new Pose(Double.NEGATIVE_INFINITY, 10, 0), new Pose(10, Double.NaN, 0),
                new Pose(10, Double.POSITIVE_INFINITY, 0), new Pose(10, Double.NEGATIVE_INFINITY, 0),
                new Pose(10, 10, Double.NaN), new Pose(10, 10, Double.POSITIVE_INFINITY),
                new Pose(10, 10, Double.NEGATIVE_INFINITY)};
        for (Pose fault : faults) for (boolean holding : new boolean[]{false, true}) {
            Harness auto = new Harness();
            if (holding) auto.follower.hold(new Pose(30, 10, 0));
            else auto.follower.follow(Paths.line(new Pose(10, 10, 0), new Pose(100, 10, 0)).constant(0));
            auto.follower.update(0.02);
            auto.follower.update(0.02);
            check(Math.abs(auto.drive.powers.forward()) > 0, "drive moves before the fault");
            auto.localizer.reported = fault;
            if (fault != null) auto.localizer.state = MotionState.zero().withPose(fault);
            try { update(auto); throw new AssertionError("fault must abort the loop"); }
            catch (RuntimeException stopped) {
                check(stopped.getClass().getSimpleName().equals("AutoStopped"), "reuse existing autonomous abort");
            }
            check(auto.follower.idle() && auto.drive.powers.forward() == 0
                    && auto.drive.powers.strafe() == 0 && auto.drive.powers.turn() == 0,
                    "fault clears FOLLOW/HOLD and explicitly zeros drive");
            check(auto.shooter.updates == 0, "fault aborts before shooter update");
        }
        Harness valid = new Harness();
        CountingPose pose = new CountingPose();
        valid.localizer.reported = pose;
        valid.localizer.state = MotionState.zero().withPose(pose);
        pose.headingReads = 0;
        update(valid);
        check(pose.headingReads == 1, "one pose validity check per normal loop");
        check(valid.localizer.updates == 1 && valid.shooter.updates == 1,
                "one localization update and a reused validity result for shooter");
        System.out.println("Passed " + checks + " autonomous pose-fault checks");
    }

    private static void update(Harness auto) throws Exception {
        Method method = General.class.getDeclaredMethod("update_follower_and_telemetry");
        method.setAccessible(true);
        try { method.invoke(auto); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException) throw (RuntimeException) failure.getCause();
            throw new AssertionError(failure.getCause());
        }
    }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }

    private static final class Harness extends AbstractAuto {
        final TestLocalizer localizer = new TestLocalizer();
        final TestDrive drive = new TestDrive();
        final TestShooter shooter = new TestShooter();
        Harness() {
            follower = new Follower(localizer, drive, new Foresight(Constants.foresightConfig));
            shooterSubsystem = shooter;
            telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(), new Class<?>[]{Telemetry.class},
                    (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
        }
        @Override protected Pose starting_pose() { return new Pose(10, 10, 0); }
        @Override protected void build_autonomous() { }
    }
    private static final class CountingPose extends Pose {
        int headingReads;
        CountingPose() { super(10, 10, 0); }
        @Override public double heading() { headingReads++; return super.heading(); }
    }
    private static final class TestLocalizer implements Localizer {
        Pose reported = new Pose(10, 10, 0);
        MotionState state = MotionState.zero().withPose(reported);
        int updates;
        @Override public void setPose(Pose pose) { reported = pose; state = MotionState.zero().withPose(pose); }
        @Override public Pose pose() { return reported; }
        @Override public MotionState state() { return state; }
        @Override public void update() { updates++; }
        @Override public void reset() { setPose(Pose.zero()); }
    }
    private static final class TestDrive implements Drivetrain {
        DrivePowers powers = DrivePowers.zero();
        @Override public void drive(DrivePowers powers, boolean manual) { this.powers = powers; }
        @Override public double maxScaling(DrivePowers a, DrivePowers b) { return 1; }
        @Override public void stop() { powers = DrivePowers.zero(); }
        @Override public void stop(boolean brake) { stop(); }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
        @Override public double interpolateVelocity(double forward, double strafe, double turn) { return 75; }
    }
    private static final class TestShooter extends Shooter {
        int updates;
        @Override public void update(double x, double y, boolean valid) {
            check(valid, "shooter receives cached valid pose result");
            updates++;
        }
    }
}
