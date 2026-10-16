package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Velocity;
import com.pedropathing.paths.Path;
import java.util.Collections;
import java.util.Map;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** Standalone regression checks against the production configuration and real Pedro follower. */
public final class PathCompletionChecks {
    private static int checks;

    public static void main(String[] args) throws InterruptedException {
        for (Alliance alliance : Alliance.values()) {
            Pose start = PoseMirroring.mirror_if_blue(new Pose(15, 20, 0), alliance);
            Pose control = PoseMirroring.mirror_if_blue(new Pose(85, 20, 0), alliance);
            Pose end = PoseMirroring.mirror_if_blue(new Pose(85, 100, Math.PI / 2), alliance);
            check_path(Paths.line(start, end).linear(start, end), alliance + " line");
            check_path(Paths.curve(start, control, end).tangent(), alliance + " curve");
        }
        System.out.println("Passed " + checks + " production Pedro path-completion checks");
    }

    private static void check_path(Path path, String label) throws InterruptedException {
        PoseLocalizer localizer = new PoseLocalizer();
        Follower follower = new Follower(localizer, new RecordingDrive(),
                new Foresight(Constants.foresightConfig));
        follower.follow(path);
        Pose endpoint = follower.currentSegment().get(1);
        localizer.setPose(follower.currentSegment().get(0));
        follower.update(0.02);
        check(follower.following() && follower.isBusy(), label + " starts following");

        // This progress formerly triggered endpoint holding with a 0.998 tolerance.
        localizer.setPose(follower.currentSegment().get(0.003));
        follower.update(0.02);
        Thread.sleep((long) Math.ceil(Constants.foresightConfig.timeoutConstraint.get()) + 40);
        follower.update(0.02);
        follower.update(0.02);
        check(follower.following(), label + " early progress stays FOLLOW beyond endpoint timeout");
        check(follower.isBusy(), label + " early progress is not successful completion");
        check(follower.parametricCompletion() < 0.01, label + " early progress remains near start");

        for (double progress : new double[]{0.5, 0.997}) {
            localizer.setPose(follower.currentSegment().get(progress));
            follower.update(0.02);
            check(follower.following() && follower.isBusy(), label + " t=" + progress + " keeps following");
            check(Math.abs(follower.parametricCompletion() - progress) < 0.0001,
                    label + " retains actual curve progress at t=" + progress);
        }

        localizer.setPose(endpoint);
        follower.update(0.02);
        follower.update(0.02);
        follower.update(0.02);
        check(follower.holding(), label + " reaches endpoint hold");
        check(!follower.isBusy(), label + " stationary exact endpoint completes");
        check(Math.hypot(follower.pose().x() - endpoint.x(), follower.pose().y() - endpoint.y()) < 1e-9,
                label + " completion preserves measured pose");
        follower.stop();
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class PoseLocalizer implements Localizer {
        private Pose pose = Pose.zero();

        @Override public void setPose(Pose pose) { this.pose = pose; }
        @Override public MotionState state() { return MotionState.ofVelocity(pose, Velocity.zero()); }
        @Override public void update() { }
        @Override public void reset() { pose = Pose.zero(); }
    }

    private static final class RecordingDrive implements Drivetrain {
        @Override public void drive(DrivePowers powers, boolean manual) { }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { }
        @Override public void stop(boolean brake) { }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
        @Override public double interpolateVelocity(double forward, double strafe, double heading) { return 75; }
    }
}
