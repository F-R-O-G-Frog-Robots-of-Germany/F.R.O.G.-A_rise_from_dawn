package org.firstinspires.ftc.teamcode.limelight;

import java.lang.reflect.Field;
import java.util.List;

/** Request identity and terminal evidence: idle and follower completion are never inferred success. */
public final class RunnerOutcomeChecks {
    private static int checks;
    private static final VisionPose HOME = new VisionPose(40, 40, Math.PI / 2);

    private static final class Drive implements ButineRunner.Drive {
        VisionPose pose = HOME;
        boolean busy, continuous;
        int index, dispatches;
        double speed;
        ButineRunner.Leg leg;
        List<ButineRunner.Leg> route;
        public VisionPose pose() { return pose; }
        public boolean is_busy() { return busy; }
        public void follow(ButineRunner.Leg value, double scale) {
            leg = value; busy = true; dispatches++;
        }
        public void stop() { busy = false; speed = 0; }
        public void set_pose(VisionPose value) { pose = value; }
        public boolean supports_continuous() { return continuous; }
        public void follow_run(List<ButineRunner.Leg> value, double scale) {
            route = value; index = 0; busy = true; dispatches++;
        }
        public int path_index() { return index; }
        public double speed_in_s() { return speed; }
        public void hold_pose(VisionPose value) { pose = value; busy = false; speed = 0; }
        void arrive() { pose = leg.target; busy = false; }
    }

    private static Butine.Params point_params() {
        Butine.Params p = new Butine.Params();
        p.field = ButineField.unrestricted(); p.capacity = 1; p.lookaheadRuns = 1;
        p.remainingS = 100; p.scoreOverheadS = 0; p.iterationCap = 0; p.localSearchPasses = 0;
        return p;
    }

    private static Butine.Params physical_params() {
        Butine.Params p = point_params();
        p.field = new ButineField(144, 18, 18, 1);
        return p;
    }

    private static PollenMap map() {
        PollenMap map = new PollenMap(); map.inject(new VisionPose(40, 70, 0)); return map;
    }

    private static void require(boolean condition, String detail) {
        checks++; if (!condition) throw new AssertionError(detail);
    }

    private static ButineRunner.Result result(ButineRunner runner, ButineRunner.Outcome outcome,
                                               ButineRunner.Reason reason) {
        ButineRunner.Result value = runner.last_result();
        require(value != null && value.attemptId == runner.last_attempt_id(), "result identifies its request");
        require(value.outcome == outcome && value.reason == reason,
                "expected " + outcome + "/" + reason + ", got " + value.outcome + "/" + value.reason);
        require(value.success() == (outcome == ButineRunner.Outcome.COMPLETE_AT_DEPOT), "only depot success permits continuation");
        return value;
    }

    private static void advance(ButineRunner runner, String name, double seconds) throws Exception {
        Field field = ButineRunner.class.getDeclaredField(name); field.setAccessible(true);
        field.setLong(runner, System.nanoTime() - (long) (seconds * 1e9));
    }

    private static void empty_and_infeasible() {
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(new PollenMap(), drive);
        require(!runner.run_butine(HOME, point_params(), 1), "fresh empty map declines without motion");
        result(runner, ButineRunner.Outcome.NO_TARGETS, ButineRunner.Reason.NO_TARGETS);
        require(drive.dispatches == 0 && !runner.intake_required(), "empty map does not invent exploration");
        runner = new ButineRunner(map(), drive); Butine.Params p = point_params(); p.remainingS = .001;
        require(!runner.run_butine(HOME, p, 1), "real targets with no fitting run decline");
        result(runner, ButineRunner.Outcome.NO_FEASIBLE_RUN, ButineRunner.Reason.NO_FEASIBLE_RUN);
        p.remainingS = 0;
        require(!runner.run_butine(HOME, p, 1), "expired request declines");
        result(runner, ButineRunner.Outcome.TIMED_OUT, ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED);
    }

    private static void refusal_keeps_active_identity() {
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(map(), drive);
        require(runner.run_butine(HOME, point_params(), 1), "first request accepted");
        long accepted = runner.last_attempt_id();
        require(runner.result(accepted) == null, "accepted request remains pending before actual arrival");
        require(!runner.run_butine(HOME, point_params(), 1), "second request while active refused");
        ButineRunner.Result refused = result(runner, ButineRunner.Outcome.REFUSED, ButineRunner.Reason.DRIVE_BUSY);
        require(refused.attemptId != accepted && runner.active_attempt_id() == accepted
                && runner.result(accepted) == null, "busy refusal cannot replace active request");
        runner.finish(ButineRunner.Outcome.CANCELLED, ButineRunner.Reason.TERMINAL_PARK_PREEMPTION, "park now");
        ButineRunner.Result cancelled = runner.result(accepted);
        require(cancelled != null && cancelled.reason == ButineRunner.Reason.TERMINAL_PARK_PREEMPTION,
                "active request receives its own explicit cancellation");
        runner.stop(); runner.request_stop();
        require(runner.result(accepted) == cancelled && runner.result(refused.attemptId) == refused,
                "cleanup preserves both active terminal evidence and refusal");
    }

    private static void successful_return_and_cleanup() {
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(map(), drive);
        runner.run_butine(HOME, point_params(), 1);
        runner.update(); runner.update(); drive.arrive(); runner.update();
        require(runner.last_result() == null && runner.is_busy(), "pickup arrival alone does not authorize scoring");
        runner.update(); runner.update(); drive.arrive(); runner.update();
        ButineRunner.Result complete = result(runner, ButineRunner.Outcome.COMPLETE_AT_DEPOT, ButineRunner.Reason.NONE);
        require(complete.contacts == 1 && !complete.endedByFullSensor && complete.finalPose.distance_to(HOME) == 0,
                "depot success retains contact and actual final pose evidence");
        runner.stop(); runner.finish(ButineRunner.Outcome.CANCELLED, ButineRunner.Reason.STOP_REQUESTED, "cleanup");
        require(runner.last_result() == complete, "terminal cleanup does not rewrite success as cancellation");
    }

    private static void partial_failed_run_never_scores() {
        Drive drive = new Drive(); PollenMap map = map(); map.inject(new VisionPose(40, 100, 0));
        Butine.Params p = point_params(); p.capacity = 2;
        ButineRunner runner = new ButineRunner(map, drive); runner.run_butine(HOME, p, 1);
        runner.update(); runner.update(); drive.arrive(); runner.update();
        runner.update(); runner.update(); drive.busy = false; runner.update();
        require(runner.is_busy() && runner.stats().collected == 1, "failed later pickup installs a safe partial-load return");
        for (int i = 0; i < 20 && runner.is_busy(); i++) {
            runner.update();
            if (drive.busy) { runner.update(); drive.arrive(); runner.update(); }
        }
        ButineRunner.Result failure = result(runner, ButineRunner.Outcome.NO_FEASIBLE_RUN, ButineRunner.Reason.PICKUP_FAILED);
        require(failure.contacts == 1 && !failure.success(), "partial load at depot does not turn failed collection into permission to shoot");
    }

    private static void full_sensor_requires_precise_arrival() {
        Drive drive = new Drive(); drive.continuous = true; drive.pose = new VisionPose(60, 40, HOME.heading);
        ButineRunner runner = new ButineRunner(map(), drive);
        require(runner.run_butine(HOME, physical_params(), 1), "physical run accepted");
        require(runner.return_to_depot_if_loaded(true), "full sensor immediately replaces collection with checked return");
        require(runner.last_result() == null && !runner.intake_required(), "full sensor is not itself scoring permission");
        runner.update(); runner.update();
        drive.index = drive.route.size() - 1; drive.busy = false;
        drive.pose = new VisionPose(HOME.x + .6, HOME.y, HOME.heading); runner.update();
        require(runner.last_result() == null, "imprecise position does not finish sensor return");
        drive.pose = new VisionPose(HOME.x, HOME.y, HOME.heading + Math.toRadians(3)); runner.update();
        require(runner.last_result() == null, "imprecise heading does not finish sensor return");
        drive.pose = HOME; drive.speed = 2; runner.update();
        require(runner.last_result() == null, "moving through depot does not finish sensor return");
        drive.speed = 0; runner.update();
        ButineRunner.Result complete = result(runner, ButineRunner.Outcome.COMPLETE_AT_DEPOT, ButineRunner.Reason.NONE);
        require(complete.contacts == 0 && complete.endedByFullSensor, "real full sensor allows success with zero visual contacts only after settling");
    }

    private static void failures_and_shutdown() throws Exception {
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(map(), drive);
        runner.run_butine(HOME, point_params(), 1); drive.pose = null; runner.update();
        result(runner, ButineRunner.Outcome.ABORTED, ButineRunner.Reason.INVALID_POSE);
        drive = new Drive(); drive.continuous = true;
        runner = new ButineRunner(map(), drive); runner.run_butine(HOME, physical_params(), 1);
        drive.pose = null; runner.return_to_depot_if_loaded(true);
        ButineRunner.Result invalidReturn = result(runner, ButineRunner.Outcome.ABORTED, ButineRunner.Reason.INVALID_POSE);
        require(invalidReturn.endedByFullSensor && invalidReturn.finalPose == null && !invalidReturn.success(),
                "full sensor cannot authorize a return using a cached localization");
        drive = new Drive(); drive.continuous = true; drive.pose = new VisionPose(60, 40, HOME.heading);
        runner = new ButineRunner(map(), drive); runner.run_butine(HOME, physical_params(), 1);
        runner.return_to_depot_if_loaded(true); runner.update(); runner.update();
        advance(runner, "legStartedNanos", 30); runner.update();
        ButineRunner.Result failedReturn = result(runner, ButineRunner.Outcome.TIMED_OUT, ButineRunner.Reason.FOLLOWER_TIMEOUT);
        require(failedReturn.endedByFullSensor && !failedReturn.success(), "loaded sensor cannot conceal failed depot return");
        drive = new Drive(); runner = new ButineRunner(map(), drive);
        Butine.Params invalid = point_params(); invalid.field = null;
        try { runner.run_butine(HOME, invalid, 1); throw new AssertionError("null field must fail"); }
        catch (IllegalArgumentException expected) { }
        result(runner, ButineRunner.Outcome.ABORTED, ButineRunner.Reason.INVALID_INPUT);
        drive = new Drive(); runner = new ButineRunner(map(), drive);
        runner.run_butine(HOME, point_params(), 1); advance(runner, "runStartedNanos", 101); runner.update();
        result(runner, ButineRunner.Outcome.TIMED_OUT, ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED);
        drive = new Drive(); runner = new ButineRunner(map(), drive);
        runner.run_butine(HOME, point_params(), 1); runner.request_stop();
        ButineRunner.Result stopped = result(runner, ButineRunner.Outcome.CANCELLED, ButineRunner.Reason.STOP_REQUESTED);
        runner.update(); runner.stop(); require(runner.last_result() == stopped && drive.dispatches == 0,
                "STOP invalidates request before any new dispatch and cleanup preserves reason");

        ButineWorker worker = new ButineWorker(); worker.preferredCore = -1;
        require(worker.start(), "asynchronous worker starts");
        try {
            drive = new Drive(); runner = new ButineRunner(map(), drive, worker);
            runner.run_butine(HOME, point_params(), 1);
            runner.finish(ButineRunner.Outcome.TIMED_OUT, ButineRunner.Reason.ACTION_TIMEOUT, "local timeout");
            ButineRunner.Result timed = result(runner, ButineRunner.Outcome.TIMED_OUT, ButineRunner.Reason.ACTION_TIMEOUT);
            for (int i = 0; i < 20; i++) { runner.update(); Thread.sleep(1); }
            require(runner.last_result() == timed && drive.dispatches == 0,
                    "late planner publication cannot resume a timed-out attempt");
        } finally { require(worker.stop(), "worker closes"); }
    }

    public static void main(String[] args) throws Exception {
        empty_and_infeasible(); refusal_keeps_active_identity(); successful_return_and_cleanup();
        partial_failed_run_never_scores(); full_sensor_requires_precise_arrival(); failures_and_shutdown();
        System.out.println("Runner outcome checks passed: " + checks);
    }
}
