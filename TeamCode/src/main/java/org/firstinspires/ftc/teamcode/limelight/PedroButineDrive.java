package org.firstinspires.ftc.teamcode.limelight;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;

/** Pedro 3 adapter. All coordinates supplied here already belong to the real field frame. */
public final class PedroButineDrive implements ButineRunner.Drive {
    private final Follower follower;

    public PedroButineDrive(Follower follower) {
        if (follower == null) throw new IllegalArgumentException("follower is required");
        this.follower = follower;
    }

    @Override public VisionPose pose() {
        Pose pose = follower.pose();
        if (pose == null) return null;
        return new VisionPose(pose.x(), pose.y(), pose.heading());
    }

    @Override public boolean is_busy() {
        return (follower.following() || follower.holding()) && follower.isBusy();
    }

    @Override public void follow(ButineRunner.Leg leg, double speedScale) {
        if (leg == null || leg.target == null || !leg.target.is_finite()
                || !Double.isFinite(speedScale) || speedScale <= 0 || speedScale > 1) {
            throw new IllegalArgumentException("finite leg target and speed scale in (0,1] are required");
        }
        // Release the previous path's temporary constraints before choosing this leg's limit.
        follower.stop();
        Pose from = follower.pose();
        if (from == null || !Double.isFinite(from.x()) || !Double.isFinite(from.y())
                || !Double.isFinite(from.heading())) throw new IllegalStateException("drive pose is invalid");
        Pose target = new Pose(leg.target.x, leg.target.y, leg.target.heading);
        if (leg.returning && from.distance(target) < 1e-3) {
            follower.hold(target);
            return;
        }
        Path path = Paths.line(from, target);
        path = leg.returning ? path.linear(from, target) : path.tangent();
        // Pedro 3 uses velocity constraints rather than the old followPath(power) argument.
        if (follower.algorithm() instanceof Foresight) {
            Foresight foresight = (Foresight) follower.algorithm();
            double limit = Math.min(foresight.config.maxVelocityConstraint.get(), 72.0 * speedScale);
            path = path.with(foresight.config.maxVelocityConstraint.at(limit));
        } else if (speedScale < 1.0) {
            throw new IllegalStateException("speed scaling requires Pedro Foresight");
        }
        path = path.with(follower.holdEnd.at(leg.returning));
        follower.follow(path);
    }

    @Override public void stop() {
        try { follower.stop(); }
        finally { follower.drivetrain.stop(); }
    }

    @Override public void set_pose(VisionPose pose) {
        if (pose == null || !pose.is_finite()) throw new IllegalArgumentException("finite pose is required");
        follower.setPose(new Pose(pose.x, pose.y, pose.heading));
    }
}
