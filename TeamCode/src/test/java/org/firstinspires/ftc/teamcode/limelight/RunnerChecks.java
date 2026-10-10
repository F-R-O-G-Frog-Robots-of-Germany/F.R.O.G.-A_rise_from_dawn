package org.firstinspires.ftc.teamcode.limelight;

import java.lang.reflect.Field;
import java.util.List;

/** Hardware-free runner checks. Advance private monotonic deadlines without sleeping. */
public final class RunnerChecks {
    private static int checks;

    private static final class FakeDrive implements ButineRunner.Drive {
        VisionPose pose = new VisionPose(20, 20, 0);
        ButineRunner.Leg leg;
        boolean busy, decline;
        int stopped, dispatched;
        public VisionPose pose() { return pose; }
        public boolean is_busy() { return busy; }
        public void follow(ButineRunner.Leg leg, double scale) {
            this.leg = leg;
            dispatched++;
            busy = !decline;
        }
        public void stop() { busy = false; stopped++; }
        public void set_pose(VisionPose pose) { this.pose = pose; }
        void arrive() { pose = leg.target; busy = false; }
    }

    public static void main(String[] args) throws Exception {
        converter_matches_planner();
        successful_collection_and_heading();
        timeout_preserves_failed_pollen();
        never_busy_and_invalid_pose();
        cancelled_run_and_deadline();
        System.out.println("Runner checks passed: " + checks);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void advance(ButineRunner runner, String field, double seconds) throws Exception {
        Field timer = ButineRunner.class.getDeclaredField(field);
        timer.setAccessible(true);
        timer.setLong(runner, System.nanoTime() - (long) (seconds * 1e9));
    }

    private static Butine.Params params() {
        Butine.Params params = new Butine.Params();
        params.capacity = 1;
        params.lookaheadRuns = 1;
        params.computeBudgetMs = 1000;
        return params;
    }

    private static void converter_matches_planner() {
        VisionPose start = new VisionPose(50, 35, 0.7);
        VisionPose depot = new VisionPose(20, 20, 2.1);
        VisionPose[] targets = {new VisionPose(45, 50, 0), new VisionPose(90, 75, 0)};
        Butine.Kinematics k = new Butine.Kinematics();
        k.pickupDwellS = 0.15;
        double expected = Butine.run_time(start, depot, targets, targets.length, k);
        Butine.Run run = new Butine.Run(new int[]{4, 7}, targets, expected, true);
        List<ButineRunner.Leg> legs = ButineRunner.build_run_legs(run, start, depot, k);
        require(legs.size() == 3, "Every pollen is a separate path followed by home");
        require(legs.get(0).from == start, "Converter begins at live pose rather than depot");
        require(!legs.get(0).returning && !legs.get(1).returning && legs.get(2).returning,
                "Only the last leg uses linear return heading");
        require(Math.abs(legs.get(0).target.heading - Math.atan2(15, -5)) < 1e-9,
                "Pickup heading points along travel");
        require(legs.get(2).target.heading == depot.heading, "Return ends at the scoring heading");
        double predicted = 0;
        for (ButineRunner.Leg leg : legs) predicted += leg.predictedS;
        require(Math.abs(predicted - expected) < 1e-9, "Converter and planner agree including dwell and final settle");
    }

    private static void successful_collection_and_heading() {
        PollenMap map = new PollenMap();
        int id = map.inject(new VisionPose(40, 20, 0), 1, 10);
        FakeDrive drive = new FakeDrive();
        ButineRunner runner = new ButineRunner(map, drive);
        VisionPose depot = drive.pose;
        require(runner.run_butine(depot, params(), 1), "One pollen produces an active run");
        require(!runner.run_butine(depot), "A second request cannot replace an active run");
        runner.update();
        require(drive.dispatched == 1 && map.snapshot().size() == 1,
                "Dispatch does not declare a pickup");
        runner.update();
        drive.arrive();
        runner.update();
        require(map.snapshot().isEmpty() && runner.stats().collected == 1,
                "A reached pollen is removed as a drive-over pickup");
        require(runner.unreachable_ids().isEmpty(), "Success is never marked unreachable");
        runner.update();
        runner.update();
        drive.pose = new VisionPose(depot.x, depot.y, Math.PI);
        drive.busy = false;
        runner.update();
        require(runner.is_busy(), "Return waits for scoring heading even at the correct position");
        drive.pose = depot;
        runner.update();
        require(runner.state() == ButineRunner.State.COMPLETE && runner.stats().runsCompleted == 1,
                "Return at position and heading completes exactly one run");
        require(runner.legs().get(0).pollenId == id, "Persistent pollen ID survives conversion");
    }

    private static void timeout_preserves_failed_pollen() throws Exception {
        PollenMap map = new PollenMap();
        VisionPose pollenPose = new VisionPose(40, 20, 0);
        int id = map.inject(pollenPose, 1, 10);
        FakeDrive drive = new FakeDrive();
        ButineRunner runner = new ButineRunner(map, drive);
        VisionPose depot = drive.pose;
        runner.run_butine(depot, params(), 1);
        runner.update();
        runner.update();
        advance(runner, "legStartedNanos", 15);
        runner.update();
        require(runner.stats().legsTimedOut == 1 && runner.stats().collected == 0,
                "Timeout never claims collected pollen");
        require(map.snapshot().size() == 1 && map.snapshot().get(0).id == id,
                "Unreached pollen remains in map");
        require(runner.unreachable_ids().contains(id) && runner.stats().replans == 1,
                "A failed target is separately excluded and replanning is bounded");
        runner.update();
        require(!runner.is_busy(), "Recovery returns home when no reachable target remains");
        require(!runner.run_butine(depot, params(), 1), "Repeated run cannot livelock on failed target");
        map.remove(id);
        map.inject(pollenPose, 1, 10);
        require(!runner.run_butine(depot, params(), 1), "Exclusion survives camera assigning a new ID to same location");
        runner.clear_unreachable();
        require(runner.run_butine(depot, params(), 1), "Caller can explicitly retry after clearing exclusions");
        runner.stop();
    }

    private static void never_busy_and_invalid_pose() throws Exception {
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 20, 0), 1, 10);
        FakeDrive drive = new FakeDrive();
        drive.decline = true;
        ButineRunner runner = new ButineRunner(map, drive);
        runner.run_butine(drive.pose, params(), 1);
        runner.update();
        advance(runner, "legStartedNanos", 0.5);
        runner.update();
        require(runner.stats().legsNeverBusy == 1 && runner.stats().collected == 0,
                "Rejected dispatch is reported without claiming pickup");
        require(map.snapshot().size() == 1, "Never-busy path leaves map intact");
        drive.pose = null;
        runner.update();
        require(runner.state() == ButineRunner.State.ABORTED && drive.stopped > 0,
                "Invalid localization aborts and stops hardware");
    }

    private static void cancelled_run_and_deadline() throws Exception {
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 20, 0), 1, 10);
        FakeDrive drive = new FakeDrive();
        ButineRunner runner = new ButineRunner(map, drive);
        runner.run_butine(drive.pose, params(), 1);
        runner.update();
        runner.stop();
        require(runner.state() == ButineRunner.State.ABORTED && runner.stats().collected == 0,
                "Driver cancellation stops without removing the target");
        runner.run_butine(drive.pose, params(), 1);
        advance(runner, "runStartedNanos", 31);
        runner.update();
        require(runner.state() == ButineRunner.State.ABORTED && !drive.busy,
                "Available autonomous time bounds the entire run");
    }
}
