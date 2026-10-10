package org.firstinspires.ftc.teamcode.limelight;

import java.lang.reflect.Field;
import java.util.ArrayList;

/** Boundary and fault traces, using monotonic timer injection instead of sleeps. */
public final class RunnerAuditChecks {
    private static int checks;
    private static final VisionPose HOME = new VisionPose(20, 20, 0);

    private static final class Drive implements ButineRunner.Drive {
        VisionPose pose = HOME;
        ButineRunner.Leg leg;
        boolean busy, failFollow, failStop;
        int dispatches, stops;
        public VisionPose pose() { return pose; }
        public boolean is_busy() { return busy; }
        public void follow(ButineRunner.Leg leg, double scale) {
            dispatches++; this.leg = leg;
            if (failFollow) throw new IllegalStateException("dispatch failed");
            busy = true;
        }
        public void stop() {
            busy = false; stops++;
            if (failStop) throw new IllegalStateException("stop failed");
        }
        public void set_pose(VisionPose pose) { this.pose = pose; }
        void arrive() { pose = leg.target; busy = false; }
    }

    public static void main(String[] args) throws Exception {
        request_boundaries(); dwell_and_redetection(); failures(); reach_boundaries(); exclusion_capacity();
        System.out.println("Runner audit checks passed: " + checks);
    }

    private static Butine.Params parameters() {
        Butine.Params p = new Butine.Params();
        p.capacity = 1; p.lookaheadRuns = 1; p.computeBudgetMs = 0;
        return p;
    }

    private static PollenMap populated() {
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 20, 0));
        return map;
    }

    private static void request_boundaries() {
        Drive drive = new Drive();
        ButineRunner empty = new ButineRunner(new PollenMap(), drive);
        require(!empty.run_butine(new VisionPose(80, 80, 0)), "empty map declines run away from depot");
        require(empty.state() == ButineRunner.State.IDLE && empty.stats().runsCompleted == 0,
                "empty map cannot signal ready to score");
        ButineRunner runner = new ButineRunner(populated(), drive);
        for (double scale : new double[]{0, -1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            reject(() -> runner.run_butine(HOME, parameters(), scale), "reject invalid speed " + scale);
        }
        for (VisionPose depot : new VisionPose[]{null, new VisionPose(Double.NaN, 0, 0),
                new VisionPose(0, 0, Double.POSITIVE_INFINITY)}) {
            reject(() -> runner.run_butine(depot, parameters(), 1), "reject invalid depot");
        }
        Butine.Params expired = parameters(); expired.remainingS = 0;
        require(!runner.run_butine(HOME, expired, 1) && drive.dispatches == 0, "expired deadline does not drive");
        drive.busy = true;
        require(!runner.run_butine(HOME), "occupied drive rejects collection");
        drive.busy = false;
        require(runner.run_butine(HOME, parameters(), .5), "half speed accepted");
        Butine.Kinematics half = new Butine.Kinematics(); half.cruiseSpeedInS = 36;
        double expected = Butine.run_time(HOME, runner.last_plan().runs[0].poses, 1, half);
        require(Math.abs(runner.last_plan().runs[0].timeS - expected) < 1e-9,
                "half speed changes predicted cruise");
        runner.stop();
    }

    private static void dwell_and_redetection() throws Exception {
        PollenMap map = populated(); Drive drive = new Drive();
        ButineRunner runner = new ButineRunner(map, drive);
        Butine.Params p = parameters(); p.kinematics.pickupDwellS = .25;
        runner.run_butine(HOME, p, 1); runner.update(); runner.update();
        drive.arrive(); runner.update();
        require(map.snapshot().size() == 1 && runner.is_busy(), "arrival starts dwell without premature removal");
        runner.update(); require(map.snapshot().size() == 1, "dwell waits across loop ticks");
        advance(runner, "pickupReachedNanos", .3); runner.update();
        require(map.snapshot().isEmpty() && runner.stats().collected == 1, "elapsed dwell removes pickup once");
        runner.update(); runner.update(); drive.arrive(); runner.update();
        require(runner.state() == ButineRunner.State.COMPLETE, "dwell does not prevent home completion");

        map = populated(); drive = new Drive(); runner = new ButineRunner(map, drive);
        runner.run_butine(HOME, parameters(), 1); runner.update(); runner.update();
        map.clear(); int nearby = map.inject(new VisionPose(41, 20, 0));
        int neighbor = map.inject(new VisionPose(44, 20, 0));
        drive.arrive(); runner.update();
        require(map.snapshot().size() == 1 && map.snapshot().get(0).id == neighbor,
                "new nearby identity removed while a separate neighbor survives");
        require(!map.remove(nearby) && runner.stats().collected == 1, "replacement counted exactly once");
        runner.stop();

        map = populated(); drive = new Drive(); runner = new ButineRunner(map, drive);
        runner.run_butine(HOME, p, 1); runner.update(); runner.update(); drive.arrive(); runner.update();
        drive.pose = HOME; runner.update();
        require(map.snapshot().size() == 1 && runner.stats().legsMissed == 1,
                "leaving during dwell retains uncollected target");
        runner.stop();
    }

    private static void failures() {
        for (boolean stopFails : new boolean[]{false, true}) {
            PollenMap map = populated(); Drive drive = new Drive();
            drive.failFollow = true; drive.failStop = stopFails;
            ButineRunner runner = new ButineRunner(map, drive);
            runner.run_butine(HOME, parameters(), 1);
            RuntimeException caught = null;
            try { runner.update(); } catch (RuntimeException exception) { caught = exception; }
            require(caught != null && runner.state() == ButineRunner.State.ABORTED,
                    "dispatch exception aborts before another tick");
            require(drive.stops == 1 && map.snapshot().size() == 1, "exception attempts stop without claiming pickup");
            require(caught.getSuppressed().length == (stopFails ? 1 : 0), "secondary stop failure preserves original cause");
        }
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(populated(), drive);
        runner.run_butine(HOME, parameters(), 1); drive.pose = new VisionPose(0, 0, Double.NaN);
        runner.update(); require(runner.state() == ButineRunner.State.ABORTED && drive.stops == 1,
                "invalid heading aborts without dispatch");
    }

    private static void reach_boundaries() throws Exception {
        for (double offset : new double[]{5, 5.0001}) {
            Drive drive = new Drive(); PollenMap map = populated();
            ButineRunner runner = new ButineRunner(map, drive);
            runner.run_butine(HOME, parameters(), 1); runner.update(); runner.update();
            drive.pose = new VisionPose(drive.leg.target.x + offset, drive.leg.target.y, 0);
            drive.busy = false; runner.update();
            require(runner.stats().collected == (offset == 5 ? 1 : 0), "five inch pickup boundary " + offset);
            runner.stop();
        }
        for (double angle : new double[]{7.9999, 8.0001}) {
            Drive drive = new Drive(); ButineRunner runner = new ButineRunner(populated(), drive);
            runner.run_butine(HOME, parameters(), 1); runner.update(); runner.update(); drive.arrive(); runner.update();
            runner.update(); runner.update();
            drive.pose = new VisionPose(HOME.x, HOME.y, Math.toRadians(angle)); drive.busy = false; runner.update();
            require((runner.state() == ButineRunner.State.COMPLETE) == (angle < 8), "eight degree depot boundary " + angle);
            if (angle > 8) {
                advance(runner, "legStartedNanos", 20); runner.update();
                require(runner.state() == ButineRunner.State.ABORTED, "unsettled depot eventually times out");
            }
            runner.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static void exclusion_capacity() throws Exception {
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(populated(), drive);
        Field idsField = ButineRunner.class.getDeclaredField("unreachableIds"); idsField.setAccessible(true);
        Field posesField = ButineRunner.class.getDeclaredField("unreachablePositions"); posesField.setAccessible(true);
        ArrayList<Integer> ids = (ArrayList<Integer>) idsField.get(runner);
        ArrayList<VisionPose> poses = (ArrayList<VisionPose>) posesField.get(runner);
        for (int i = 0; i < 64; i++) { ids.add(1000 + i); poses.add(new VisionPose(120, 120, 0)); }
        runner.run_butine(HOME, parameters(), 1); runner.update(); runner.update();
        advance(runner, "legStartedNanos", 20); runner.update();
        require(ids.size() == 64 && !ids.contains(1000) && ids.contains(0), "bounded exclusions keep newest failure");
        runner.clear_unreachable(); require(ids.isEmpty() && poses.isEmpty(), "clear releases both exclusion lists");
        runner.stop();
    }

    private static void advance(Object owner, String name, double seconds) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        field.setLong(owner, System.nanoTime() - (long) (seconds * 1e9));
    }
    private static void reject(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
