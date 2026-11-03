package org.firstinspires.ftc.teamcode.core.control;

import java.lang.reflect.Field;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineField;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/** Typed facade evidence and arbitration without constructing camera hardware or faking runner state. */
public final class CompetitionCollectionOutcomeChecks {
    private static final VisionPose START = new VisionPose(20, 20, 0);
    private static int checks;

    public static void main(String[] args) throws Exception {
        disabled_and_preflight_requests_are_terminal();
        unavailable_and_invalid_localization_are_distinct();
        busy_refusal_keeps_the_active_attempt();
        external_finish_preserves_its_cause();
        shutdown_records_stop_without_motor_commands();
        System.out.println("Competition collection outcome checks passed: " + checks);
    }

    private static void disabled_and_preflight_requests_are_terminal() {
        CompetitionWorkers workers = new CompetitionWorkers(null, null, false, false);
        require(workers.collection_attempt_id() == 0 && workers.collection_result() == null,
                "an unrequested collection has no invented result");
        require(!workers.request_collection(START, parameters(), 1), "disabled planner refuses collection");
        ButineRunner.Result refused = workers.collection_result();
        outcome(refused, ButineRunner.Outcome.REFUSED, ButineRunner.Reason.PLANNER_UNAVAILABLE);
        require(refused.attemptId == workers.collection_attempt_id() && !refused.success(),
                "disabled refusal has matching identity and cannot authorize a shot");
        long expired = workers.decline_collection(START, ButineRunner.Outcome.TIMED_OUT,
                ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED, "no collection allowance remains");
        require(expired != refused.attemptId && expired == workers.collection_attempt_id(),
                "preflight failure receives a new request identity");
        outcome(workers.collection_result(expired), ButineRunner.Outcome.TIMED_OUT,
                ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED);
        require(workers.collection_result(refused.attemptId) == refused,
                "later preflight failure does not rewrite previous immutable evidence");
        workers.finish_collection(ButineRunner.Outcome.CANCELLED, ButineRunner.Reason.STOP_REQUESTED, "cleanup");
        workers.cancel_drive(); workers.request_stop(); workers.stop();
        outcome(workers.collection_result(), ButineRunner.Outcome.TIMED_OUT,
                ButineRunner.Reason.MATCH_ALLOWANCE_EXPIRED);
        try {
            workers.decline_collection(START, ButineRunner.Outcome.COMPLETE_AT_DEPOT,
                    ButineRunner.Reason.NONE, "invalid success");
            throw new AssertionError("preflight reported success");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            workers.finish_collection(ButineRunner.Outcome.COMPLETE_AT_DEPOT,
                    ButineRunner.Reason.NONE, "invalid success");
            throw new AssertionError("external finish reported success");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    private static void unavailable_and_invalid_localization_are_distinct() throws Exception {
        try (Session session = new Session()) {
            require(!session.workers.request_collection(START, parameters(), 1),
                    "no camera frame declines collection without dispatch");
            outcome(session.workers.collection_result(), ButineRunner.Outcome.REFUSED,
                    ButineRunner.Reason.VISION_UNAVAILABLE);
            require(session.drive.stops == 0 && session.drive.dispatches == 0,
                    "unavailable camera does not interrupt independently owned drive");
            session.drive.position = null;
            require(!session.workers.request_collection(START, parameters(), 1),
                    "invalid odometry declines collection");
            ButineRunner.Result invalid = session.workers.collection_result();
            outcome(invalid, ButineRunner.Outcome.REFUSED, ButineRunner.Reason.INVALID_POSE);
            require(invalid.finalPose == null && session.drive.stops == 0,
                    "invalid pose is explicit and is not replaced by a cached pose");
            session.drive.failPose = true;
            require(!session.workers.request_collection(START, parameters(), 1),
                    "pose service fault is contained before collection starts");
            outcome(session.workers.collection_result(), ButineRunner.Outcome.REFUSED,
                    ButineRunner.Reason.SERVICE_FAILURE);
        }
    }

    private static void busy_refusal_keeps_the_active_attempt() throws Exception {
        try (Session session = new Session()) {
            long active = session.start_runner();
            int stops = session.drive.stops;
            require(session.workers.collection_result(active) == null,
                    "active planning has no terminal result merely because drive is idle");
            require(!session.workers.request_collection(START, parameters(), 1),
                    "second request is refused while the first is planning");
            ButineRunner.Result refused = session.workers.collection_result();
            outcome(refused, ButineRunner.Outcome.REFUSED, ButineRunner.Reason.DRIVE_BUSY);
            require(refused.attemptId != active && session.runner.active_attempt_id() == active
                            && session.workers.collection_result(active) == null,
                    "busy refusal has its own identity and does not replace the active attempt");
            long preflight = session.workers.decline_collection(START, ButineRunner.Outcome.NO_FEASIBLE_RUN,
                    ButineRunner.Reason.PARK_UNREACHABLE, "no checked depot-to-park route");
            require(preflight != active && preflight != refused.attemptId && session.workers.owns_drive()
                            && session.drive.stops == stops && session.drive.dispatches == 0,
                    "unstarted preflight failure neither cancels nor dispatches active work");
            session.workers.cancel_drive();
            ButineRunner.Result cancelled = session.workers.collection_result(active);
            outcome(cancelled, ButineRunner.Outcome.CANCELLED, ButineRunner.Reason.MANUAL_OVERRIDE);
            require(session.workers.collection_result(refused.attemptId) == refused
                            && session.workers.collection_attempt_id() == preflight,
                    "active cancellation preserves latest-request and refusal evidence separately");
            session.workers.finish_collection(ButineRunner.Outcome.TIMED_OUT,
                    ButineRunner.Reason.ACTION_TIMEOUT, "later cleanup");
            session.workers.request_stop();
            require(session.workers.collection_result(active) == cancelled,
                    "cleanup cannot overwrite the original terminal cause");
        }
    }

    private static void external_finish_preserves_its_cause() throws Exception {
        ButineRunner.Outcome[] outcomes = {
                ButineRunner.Outcome.ABORTED, ButineRunner.Outcome.ABORTED,
                ButineRunner.Outcome.TIMED_OUT, ButineRunner.Outcome.CANCELLED
        };
        ButineRunner.Reason[] reasons = {
                ButineRunner.Reason.VISION_UNAVAILABLE, ButineRunner.Reason.INVALID_POSE,
                ButineRunner.Reason.ACTION_TIMEOUT, ButineRunner.Reason.TERMINAL_PARK_PREEMPTION
        };
        for (int i = 0; i < outcomes.length; i++) {
            try (Session session = new Session()) {
                long attempt = session.start_runner();
                session.drive.position = new VisionPose(21, 20, 0);
                session.workers.finish_collection(outcomes[i], reasons[i], "typed external cause");
                ButineRunner.Result result = session.workers.collection_result(attempt);
                outcome(result, outcomes[i], reasons[i]);
                require(result.finalPose == session.drive.position && result.contacts == 0 && !result.success()
                                && !session.workers.owns_drive() && session.drive.stops > 0,
                        "external termination stores live pose, releases ownership and cannot imply successful return");
                session.workers.cancel_drive();
                session.workers.request_stop();
                require(session.workers.collection_result(attempt) == result,
                        "later manual and lifecycle cleanup leave typed evidence intact");
            }
        }
    }

    private static void shutdown_records_stop_without_motor_commands() throws Exception {
        try (Session session = new Session()) {
            long attempt = session.start_runner();
            int stops = session.drive.stops;
            session.workers.request_stop();
            outcome(session.workers.collection_result(attempt), ButineRunner.Outcome.CANCELLED,
                    ButineRunner.Reason.STOP_REQUESTED);
            require(session.drive.stops == stops && !session.workers.owns_drive(),
                    "shutdown invalidates planning immediately without issuing a motor command");
            session.runner.update();
            require(session.drive.dispatches == 0, "a planner publication cannot resume after shutdown");
            require(!session.workers.request_collection(START, parameters(), 1),
                    "a stopped facade refuses new collection");
            outcome(session.workers.collection_result(), ButineRunner.Outcome.REFUSED,
                    ButineRunner.Reason.STOP_REQUESTED);
        }
    }

    private static final class Session implements AutoCloseable {
        final Drive drive = new Drive();
        final Limelight camera = new Limelight();
        final PollenMap map = new PollenMap(camera);
        final CompetitionWorkers workers = new CompetitionWorkers(camera, map, drive, true);
        final ButineRunner runner;
        Session() throws Exception {
            Field field = CompetitionWorkers.class.getDeclaredField("collection");
            field.setAccessible(true);
            runner = (ButineRunner) field.get(workers);
            workers.planner().preferredCore = -1;
            require(workers.planner().start(), "real CPU-only planner starts without camera hardware");
            map.inject(new VisionPose(26, 20, 0));
        }
        long start_runner() {
            // Start accepted CPU work through its real public API. Camera readiness itself is covered
            // by the transport audit; no internal worker/runner state is manufactured here.
            require(runner.run_butine(START, parameters(), 1), "real collection enters asynchronous planning");
            return runner.active_attempt_id();
        }
        @Override public void close() { workers.stop(); }
    }

    private static final class Drive implements ButineRunner.Drive {
        VisionPose position = START;
        boolean failPose;
        int stops, dispatches;
        public VisionPose pose() {
            if (failPose) throw new IllegalStateException("injected pose service fault");
            return position;
        }
        public boolean is_busy() { return false; }
        public void follow(ButineRunner.Leg leg, double speedScale) { dispatches++; }
        public void stop() { stops++; }
        public void set_pose(VisionPose pose) { position = pose; }
    }

    private static Butine.Params parameters() {
        Butine.Params params = new Butine.Params();
        params.field = ButineField.unrestricted(); params.sweepIntake = false;
        params.capacity = 1; params.iterationCap = 0; params.localSearchPasses = 0;
        return params;
    }

    private static void outcome(ButineRunner.Result result, ButineRunner.Outcome outcome, ButineRunner.Reason reason) {
        require(result != null && result.outcome == outcome && result.reason == reason,
                "terminal result must report " + outcome + "/" + reason);
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
