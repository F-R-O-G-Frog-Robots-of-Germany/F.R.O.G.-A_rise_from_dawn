package org.firstinspires.ftc.teamcode.limelight;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.api.Paths;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Velocity;
import com.pedropathing.paths.Path;
import java.util.ArrayList;
import java.util.List;

/** Pedro 3 adapter. All coordinates supplied here already belong to the real field frame. */
public final class PedroButineDrive implements ButineRunner.Drive {
    private static final double CONTINUOUS_END_TOLERANCE = 1e-6;
    private static final double ORIGIN_TOLERANCE_IN = 1e-6;
    private final Follower follower;
    private int[] segmentLegIndices = new int[0];
    private int finalLegIndex = -1;
    private int reportedLegIndex = -1;
    private double reportedFraction = Double.NaN;
    private Pose finalDepot;
    private boolean continuousRun;
    private boolean finalHoldIssued;
    private boolean holdOverride;
    private boolean previousHoldEnd;

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
        update_continuous_state();
        return (follower.following() || follower.holding()) && follower.isBusy();
    }

    @Override public boolean supports_continuous() {
        return follower.algorithm() instanceof Foresight;
    }

    @Override public void hold_pose(VisionPose pose) {
        stop();
        if (pose == null || !pose.is_finite()) throw new IllegalArgumentException("Finite hold pose required");
        follower.algorithm().reset();
        follower.hold(new Pose(pose.x, pose.y, pose.heading));
    }

    @Override public int path_index() {
        update_continuous_state();
        return continuousRun ? reportedLegIndex : -1;
    }

    @Override public double path_fraction() {
        update_continuous_state();
        return continuousRun ? reportedFraction : Double.NaN;
    }

    @Override public double speed_in_s() {
        Velocity velocity = follower.velocity();
        if (velocity == null) return Double.NaN;
        double speed = Math.hypot(velocity.vx, velocity.vy);
        return Double.isFinite(speed) ? speed : Double.NaN;
    }

    /** Dispatch the complete safe polyline once; only the depot is a stopping endpoint. */
    @Override public void follow_run(List<ButineRunner.Leg> legs, double speedScale) {
        stop();
        try {
            if (legs == null || legs.isEmpty() || !supports_continuous()
                    || !Double.isFinite(speedScale) || speedScale <= 0 || speedScale > 1) {
                throw new IllegalArgumentException("Nonempty continuous run, Foresight and speed scale in (0,1] required");
            }
            List<ButineRunner.Leg> snapshot = new ArrayList<>(legs);
            Pose live = require_live_pose();
            ButineField field = null;
            VisionPose previous = new VisionPose(live.x(), live.y(), live.heading());
            int lastMoving = -1;
            for (int i = 0; i < snapshot.size(); i++) {
                ButineRunner.Leg leg = snapshot.get(i);
                if (leg == null || leg.from == null || !leg.from.is_finite()
                        || leg.target == null || !leg.target.is_finite() || leg.field == null
                        || !Double.isFinite(leg.startSpeedInS) || leg.startSpeedInS < 0
                        || !Double.isFinite(leg.endSpeedInS) || leg.endSpeedInS < 0) {
                    throw new IllegalArgumentException("Finite run legs and nonnegative boundary speeds required");
                }
                if (field == null) field = leg.field;
                if (leg.field != field || (i > 0 && leg.from.distance_to(previous) > ORIGIN_TOLERANCE_IN)) {
                    throw new IllegalArgumentException("Run legs must share field geometry and connected origins");
                }
                if (leg.returning != (i == snapshot.size() - 1)) {
                    throw new IllegalArgumentException("A continuous run must finish with exactly one depot leg");
                }
                if (!field.segment_clear(previous, leg.target)) {
                    throw new IllegalStateException("Continuous run contains a blocked live-origin segment");
                }
                if (previous.distance_to(leg.target) > 0) lastMoving = i;
                previous = leg.target;
            }

            ButineRunner.Leg depotLeg = snapshot.get(snapshot.size() - 1);
            finalDepot = new Pose(depotLeg.target.x, depotLeg.target.y, depotLeg.target.heading);
            finalLegIndex = snapshot.size() - 1;
            Foresight foresight = (Foresight) follower.algorithm();
            double limit = Math.min(foresight.config.maxVelocityConstraint.get(), 72.0 * speedScale);
            if (!Double.isFinite(limit) || limit <= 0) {
                throw new IllegalStateException("Continuous run needs a finite positive velocity limit");
            }
            List<Path> paths = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            Pose from = live;
            for (int i = 0; i < snapshot.size(); i++) {
                ButineRunner.Leg leg = snapshot.get(i);
                Pose target = new Pose(leg.target.x, leg.target.y, leg.target.heading);
                if (from.distance(target) == 0) {
                    // Pedro 3 Line rejects zero length. Retain the original leg index in the mapping.
                    from = target;
                    continue;
                }
                boolean last = i == lastMoving;
                Path path = Paths.line(from, target);
                if (last) path = path.linear(from.heading(), finalDepot.heading());
                else if (leg.constantHeading) path = path.constant(leg.from.heading);
                else path = path.linear(from, target);
                // Predictive path skipping can cut obstacle corners; reach each validated vertex first.
                path = path.with(foresight.config.maxVelocityConstraint.at(limit),
                        foresight.config.pathSkip.at(false),
                        foresight.config.parametricTConstraint.at(CONTINUOUS_END_TOLERANCE),
                        foresight.config.brakeAtEnd.at(last),
                        foresight.config.coastDownToVelocity.at(last ? 0.0 : Math.min(limit, leg.endSpeedInS)));
                paths.add(path);
                indices.add(i);
                from = target;
            }
            segmentLegIndices = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) segmentLegIndices[i] = indices.get(i);
            continuousRun = true;
            previousHoldEnd = follower.holdEnd.get();
            holdOverride = true;
            // A path-scoped holdEnd modifier reverts before Pedro chooses its final HOLD/IDLE mode.
            follower.holdEnd.set(true);
            if (paths.isEmpty()) {
                issue_final_hold();
            } else {
                reportedLegIndex = segmentLegIndices[0];
                reportedFraction = 0;
                follower.follow(Paths.path(paths.toArray(new Path[0])));
            }
        } catch (RuntimeException failure) {
            stop();
            throw failure;
        }
    }

    @Override public void follow(ButineRunner.Leg leg, double speedScale) {
        if (leg == null || leg.target == null || !leg.target.is_finite() || leg.field == null
                || !Double.isFinite(speedScale) || speedScale <= 0 || speedScale > 1) {
            stop();
            throw new IllegalArgumentException("finite leg target and speed scale in (0,1] are required");
        }
        // Release the previous path's temporary constraints before choosing this leg's limit.
        stop();
        Pose from = follower.pose();
        if (from == null || !Double.isFinite(from.x()) || !Double.isFinite(from.y())
                || !Double.isFinite(from.heading())) {
            stop();
            throw new IllegalStateException("drive pose is invalid");
        }
        VisionPose live = new VisionPose(from.x(), from.y(), from.heading());
        if (!leg.field.segment_clear(live, leg.target)) {
            stop();
            throw new IllegalStateException("live-origin segment is blocked by the leg's field geometry");
        }
        Pose target = new Pose(leg.target.x, leg.target.y, leg.target.heading);
        if (leg.returning && from.distance(target) < 1e-3) {
            follower.algorithm().reset();
            follower.hold(target);
            return;
        }
        Path path = Paths.line(from, target);
        path = leg.returning ? path.linear(from, target)
                : leg.constantHeading ? path.constant(leg.from.heading) : path.tangent();
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
        finally {
            try { restore_hold_end(); }
            finally {
                continuousRun = false;
                finalHoldIssued = false;
                segmentLegIndices = new int[0];
                finalLegIndex = reportedLegIndex = -1;
                reportedFraction = Double.NaN;
                finalDepot = null;
                follower.drivetrain.stop();
            }
        }
    }

    private Pose require_live_pose() {
        Pose current = follower.pose();
        if (current == null || !Double.isFinite(current.x()) || !Double.isFinite(current.y())
                || !Double.isFinite(current.heading())) {
            throw new IllegalStateException("drive pose is invalid");
        }
        return current;
    }

    private void update_continuous_state() {
        if (!continuousRun || finalHoldIssued) return;
        int index = follower.pathIndex();
        if (follower.holding() || (follower.following() && index >= segmentLegIndices.length)) {
            issue_final_hold();
        } else if (follower.following() && index >= 0) {
            int bounded = Math.min(index, segmentLegIndices.length - 1);
            reportedLegIndex = Math.max(reportedLegIndex, segmentLegIndices[bounded]);
            double fraction = follower.parametricCompletion();
            reportedFraction = Double.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : Double.NaN;
        } else if (!follower.following()) {
            // An external stop must not silently restart the motors to finish this run.
            restore_hold_end();
            continuousRun = false;
        }
    }

    private void issue_final_hold() {
        follower.algorithm().reset();
        follower.hold(finalDepot);
        finalHoldIssued = true;
        reportedLegIndex = finalLegIndex;
        reportedFraction = 1;
        restore_hold_end();
    }

    private void restore_hold_end() {
        if (!holdOverride) return;
        follower.holdEnd.set(previousHoldEnd);
        holdOverride = false;
    }

    @Override public void set_pose(VisionPose pose) {
        if (pose == null || !pose.is_finite()) throw new IllegalArgumentException("finite pose is required");
        follower.setPose(new Pose(pose.x, pose.y, pose.heading));
    }
}
