package org.firstinspires.ftc.teamcode.limelight;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Vector2D;
import com.pedropathing.math.Velocity;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.PathTracker;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Actual Pedro 3 adapter, compound paths, configuration modifiers and endpoint state transitions. */
public final class PedroContinuousChecks {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1e-9, message + ": " + expected + " != " + actual);
    }

    private static final class TestLocalizer implements Localizer {
        private MotionState state = MotionState.ofVelocity(new Pose(20, 20, 0.4), new Velocity(3, 4, 0));
        @Override public void setPose(Pose pose) { state = state.withPose(pose); }
        @Override public MotionState state() { return state; }
        @Override public void update() { }
        @Override public void reset() { }
    }

    private static final class TestDrivetrain implements Drivetrain {
        int stops;
        @Override public void drive(DrivePowers powers, boolean manual) { }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { stops++; }
        @Override public void stop(boolean manual) { stops++; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
        @Override public double interpolateVelocity(double forward, double lateral, double angle) { return forward; }
    }

    private static final class CountingFollower extends Follower {
        int follows;
        CountingFollower(Localizer localizer, Drivetrain drivetrain, Foresight algorithm) {
            super(localizer, drivetrain, algorithm);
        }
        @Override public void follow(Path path) { follows++; super.follow(path); }
    }

    private static Foresight algorithm() {
        return new Foresight(new ForesightConfig(config -> {
            config.headingFeedback.set(Controller.proportional(0));
            config.forwardTranslational.set(Controller.proportional(0));
            config.strafeTranslational.set(Controller.proportional(0));
            config.brake.set(Controller.proportional(0));
            config.coast.set(Controller.proportional(0));
            config.naturalForwardDeceleration.set(46.0);
            config.naturalStrafeDeceleration.set(79.0);
            config.maxAchievableForwardVelocity.set(75.0);
            config.maxAchievableStrafeVelocity.set(59.0);
            config.maxVelocityConstraint.set(75.0);
            config.maxDecelerationConstraint.set(79.0);
            config.headingBrakeCoefficients.set(Vector2D.zero());
            config.linearBrakeCoefficients.set(Matrix.diag(0.01, 0.01));
            config.quadraticBrakeCoefficients.set(Matrix.diag(0.00008, 0.00008));
        }));
    }

    private static VisionPose pose(double x, double y, double heading) { return new VisionPose(x, y, heading); }

    private static ButineRunner.Leg leg(VisionPose from, VisionPose target, ButineRunner.Leg.Kind kind,
                                        boolean intake, boolean constantHeading, double endSpeed) {
        return new ButineRunner.Leg(from, target, 1, 1, 0, kind, ButineField.unrestricted(),
                intake, constantHeading, 14, endSpeed);
    }

    /** Advance the real tracker directly to test handoffs without inventing robot motion or Pedro copies. */
    private static PathTracker tracker(Follower follower) throws Exception {
        Field member = Follower.class.getDeclaredField("pathTracker");
        member.setAccessible(true);
        return (PathTracker) member.get(follower);
    }

    public static void main(String[] args) throws Exception {
        TestLocalizer localizer = new TestLocalizer();
        TestDrivetrain drivetrain = new TestDrivetrain();
        Foresight algorithm = algorithm();
        CountingFollower follower = new CountingFollower(localizer, drivetrain, algorithm);
        PedroButineDrive drive = new PedroButineDrive(follower);
        follower.holdEnd.set(false);
        VisionPose start = pose(20, 20, 0.4), pickup = pose(30, 20, 0.4);
        VisionPose corner = pose(30, 30, 1.2), depot = pose(30, 30, 2);
        List<ButineRunner.Leg> legs = Arrays.asList(
                leg(start, start, ButineRunner.Leg.Kind.TRANSIT, true, true, 14),
                leg(start, pickup, ButineRunner.Leg.Kind.PICKUP, true, true, 14),
                leg(pickup, corner, ButineRunner.Leg.Kind.TRANSIT, false, false, 14),
                leg(corner, depot, ButineRunner.Leg.Kind.DEPOT, false, false, 0));
        drive.follow_run(legs, 1);
        require(follower.follows == 1, "whole run dispatches exactly one compound path");
        require(follower.currentPath().getSegments().size() == 2, "zero-length parts are filtered");
        require(drive.path_index() == 1, "first Pedro segment maps to its original leg");
        near(5, drive.speed_in_s(), "speed includes both translation axes");
        require(follower.holdEnd.get(), "hold override remains active during compound execution");
        require(!algorithm.config.pathSkip.get(), "predictive obstacle-corner skipping is disabled");
        require(!algorithm.config.brakeAtEnd.get(), "intermediate segment has no endpoint brake");
        near(14, algorithm.config.coastDownToVelocity.get(), "planned through-speed floor");
        near(0.4, follower.currentSegment().heading(0.5), "collection keeps its fixed intake heading");
        int dispatchStops = drivetrain.stops;
        tracker(follower).advance();
        require(drive.path_index() == 2, "next Pedro segment maps to its original leg");
        require(follower.follows == 1 && drivetrain.stops == dispatchStops,
                "handoff neither redispatches nor stops drivetrain output");
        require(algorithm.config.brakeAtEnd.get(), "last moving segment brakes for depot");
        near(0, algorithm.config.coastDownToVelocity.get(), "depot profile stops");
        near(2, follower.currentSegment().heading(1), "trailing zero-length depot preserves final heading");
        tracker(follower).advance();
        require(drive.path_index() == 3, "completed progress retains final original leg index");
        near(1, drive.path_fraction(), "completed progress is one");
        require(follower.holding(), "compound completion explicitly holds depot");
        require(!follower.holdEnd.get(), "original global hold flag is restored");
        require(algorithm.config.pathSkip.get(), "temporary path policy is restored");
        near(75, algorithm.config.maxVelocityConstraint.get(), "temporary speed constraint is restored");
        drive.stop();
        require(drive.path_index() == -1 && follower.idle(), "stop clears run progress and follower mode");

        localizer.setPose(new Pose(30, 30, 0));
        drive.follow_run(Collections.singletonList(leg(pose(30, 30, 0), depot,
                ButineRunner.Leg.Kind.DEPOT, false, false, 0)), 1);
        require(follower.holding() && drive.path_index() == 0, "single coincident depot uses angular hold");
        near(1, drive.path_fraction(), "coincident depot has complete path progress");
        drive.hold_pose(start);
        require(follower.holding() && drive.path_index() == -1, "alignment hold has no collection progress");

        localizer.setPose(new Pose(20, 20, 0.4));
        drive.follow_run(Arrays.asList(
                leg(start, pickup, ButineRunner.Leg.Kind.PICKUP, true, true, 14),
                leg(pickup, corner, ButineRunner.Leg.Kind.DEPOT, false, false, 0)), 1);
        follower.stop();
        require(drive.path_index() == -1 && follower.idle(), "external stop never restarts depot hold");
        require(!follower.holdEnd.get(), "external stop restores global hold flag");
        drive.follow_run(Arrays.asList(
                leg(start, pickup, ButineRunner.Leg.Kind.PICKUP, true, true, 0),
                leg(pickup, start, ButineRunner.Leg.Kind.DEPOT, false, false, 0)), 1);
        require(!algorithm.config.brakeAtEnd.get(), "physical reversal adds no endpoint hold");
        near(0, algorithm.config.coastDownToVelocity.get(), "physical reversal may require zero junction speed");
        drive.stop();

        VisionPose overshoot = pose(31, 20, 0.4);
        drive.follow_run(Arrays.asList(
                leg(start, pickup, ButineRunner.Leg.Kind.PICKUP, true, true, 14),
                leg(pickup, overshoot, ButineRunner.Leg.Kind.TRANSIT, true, true, 14),
                leg(overshoot, start, ButineRunner.Leg.Kind.DEPOT, false, false, 0)), 1);
        int beforeOvershoot = drivetrain.stops;
        int overshootDispatches = follower.follows;
        tracker(follower).advance();
        require(drive.path_index() == 1 && drivetrain.stops == beforeOvershoot
                        && follower.follows == overshootDispatches,
                "one-inch intake overshoot neither stops nor redispatches at contact");
        require(!algorithm.config.brakeAtEnd.get(), "intake overshoot retains its continuous drive policy");
        near(14, algorithm.config.coastDownToVelocity.get(), "overshoot preserves planned through speed");
        drive.stop();
        System.out.println("Pedro continuous checks passed: " + checks);
    }
}
