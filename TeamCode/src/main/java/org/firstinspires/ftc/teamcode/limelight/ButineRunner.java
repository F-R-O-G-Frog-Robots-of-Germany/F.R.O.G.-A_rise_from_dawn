package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Solve and drive one collection run. The caller updates localization and owns intake/scoring. */
public final class ButineRunner {
    public interface Drive {
        VisionPose pose();
        boolean is_busy();
        void follow(Leg leg, double speedScale);
        void stop();
        void set_pose(VisionPose pose);
    }

    public enum State { IDLE, PLANNING, DRIVING, COMPLETE, ABORTED }

    public static final class Leg {
        public final VisionPose from, target;
        public final int pollenId;
        public final double predictedS, swingDeg;
        public final boolean returning;

        public Leg(VisionPose from, VisionPose target, int pollenId, double predictedS,
                   double swingDeg, boolean returning) {
            this.from = from;
            this.target = target;
            this.pollenId = pollenId;
            this.predictedS = predictedS;
            this.swingDeg = swingDeg;
            this.returning = returning;
        }
    }

    public static final class Stats {
        public int runsStarted, runsCompleted, planned, collected, legsTimedOut, legsNeverBusy;
        public int legsMissed, solves, replans;
        public double solveUsTotal, predictedS, lastLegPredictedS, lastLegActualS;
    }

    private static final double ARM_TIMEOUT_S = 0.4;
    private static final double PICKUP_RADIUS_IN = 5.0;
    private static final double DEPOT_RADIUS_IN = 6.0;
    private static final double DEPOT_HEADING_TOLERANCE_RAD = Math.toRadians(8);
    private static final int MAX_REPLANS = 1;
    private final PollenMap map;
    private final Drive drive;
    private final ButineWorker planner;
    private final Stats stats = new Stats();
    private final ArrayList<Integer> unreachableIds = new ArrayList<>();
    private final ArrayList<VisionPose> unreachablePositions = new ArrayList<>();
    private List<Leg> legs = Collections.emptyList();
    private Butine.Plan lastPlan;
    private Butine.Params params;
    private VisionPose depot;
    private State state = State.IDLE;
    private int legIndex, replansThisRun, pickupsThisRun;
    private long legStartedNanos, runStartedNanos;
    private long pickupReachedNanos;
    private double speedScale = 1.0;
    private boolean dispatched, armed;
    private boolean waitingPickup;
    private String lastReport = "idle";
    private long planningRequest;
    private boolean planningReplan;
    private Butine.Params planningParams;

    public ButineRunner(PollenMap map, Drive drive) {
        this(map, drive, null);
    }

    /** Optional CPU-2 planner; start/stop its lifecycle in the caller's init/finally. */
    public ButineRunner(PollenMap map, Drive drive, ButineWorker planner) {
        if (map == null || drive == null) throw new IllegalArgumentException("map and drive are required");
        this.map = map;
        this.drive = drive;
        this.planner = planner;
    }

    /** A depot is already mirrored into the same real field frame as camera observations. */
    public boolean run_butine(VisionPose depot, Butine.Params parameters, double speedScale) {
        if (is_busy() || drive.is_busy()) return false;
        if (planner != null && !planner.is_running()) {
            lastReport = "planner is not running";
            return false;
        }
        if (depot == null || !depot.is_finite() || parameters == null
                || !Double.isFinite(speedScale) || speedScale <= 0 || speedScale > 1) {
            throw new IllegalArgumentException("finite depot, parameters and speed scale in (0,1] are required");
        }
        VisionPose start = drive.pose();
        if (start == null || !start.is_finite()) throw new IllegalStateException("drive pose is invalid");
        this.depot = depot;
        this.params = new Butine.Params(parameters);
        this.speedScale = speedScale;
        this.params.kinematics.cruiseSpeedInS *= speedScale;
        replansThisRun = pickupsThisRun = 0;
        runStartedNanos = System.nanoTime();
        if (!solve_from(start, false)) {
            state = State.IDLE;
            lastReport = "no fitting collection run";
            return false;
        }
        if (planner == null) stats.runsStarted++;
        state = planner == null ? State.DRIVING : State.PLANNING;
        return true;
    }

    public boolean run_butine(VisionPose depot) { return run_butine(depot, new Butine.Params(), 1.0); }

    /** Reuse the previous scoring pose, or use the live robot pose on the first call. */
    public boolean run_butine() { return run_butine(depot == null ? drive.pose() : depot); }

    private boolean solve_from(VisionPose start, boolean replanning) {
        Butine.Params local = new Butine.Params(params);
        if (replanning) {
            local.remainingS = Math.max(0, params.remainingS - (System.nanoTime() - runStartedNanos) / 1e9);
            if (local.capacity > 0) local.capacity = Math.max(1, local.capacity - pickupsThisRun);
        }
        ArrayList<Pollen> candidates = new ArrayList<>();
        for (Pollen pollen : map.snapshot()) {
            boolean excluded = unreachableIds.contains(pollen.id);
            for (VisionPose failed : unreachablePositions) {
                if (pollen.position.distance_to(failed) <= PICKUP_RADIUS_IN) { excluded = true; break; }
            }
            if (!excluded) candidates.add(pollen);
        }
        if (planner != null) {
            if (!planner.is_running()) return false;
            state = State.ABORTED;
            drive.stop();
            planningRequest = planner.request_plan(candidates, start, depot, local, 0);
            if (planningRequest == 0) return false;
            planningReplan = replanning; planningParams = local;
            legs = Collections.emptyList(); legIndex = 0;
            dispatched = armed = waitingPickup = false;
            stats.planned = 0; stats.predictedS = 0; lastPlan = null;
            state = State.PLANNING; lastReport = "waiting for planner request " + planningRequest;
            return true;
        }
        lastPlan = Butine.solve(candidates, start, depot, local);
        stats.solves++;
        stats.solveUsTotal += lastPlan.solveUs;
        return install_plan(start, local);
    }

    private boolean install_plan(VisionPose start, Butine.Params local) {
        stats.planned = 0;
        stats.predictedS = 0;
        if (lastPlan.fittingRuns <= 0 || lastPlan.runs[0].count <= 0) {
            legs = Collections.emptyList();
            legIndex = 0;
            dispatched = armed = waitingPickup = false;
            return false;
        }
        legs = build_run_legs(lastPlan.runs[0], start, depot, local.kinematics);
        stats.planned = lastPlan.runs[0].count;
        stats.predictedS = 0;
        for (Leg leg : legs) stats.predictedS += leg.predictedS;
        legIndex = 0;
        dispatched = armed = waitingPickup = false;
        return true;
    }

    /** Poll publication only; solver, map mutation and drive ownership never overlap. */
    private void update_planning() {
        VisionPose here = drive.pose();
        if (here == null || !here.is_finite() || drive.is_busy()
                || (System.nanoTime() - runStartedNanos) / 1e9 >= params.remainingS) {
            planner.cancel_pending(); drive.stop(); state = State.ABORTED;
            lastReport = "planning cancelled: motion, invalid pose or expired match time";
            return;
        }
        ButineWorker.Result result = planner.latest_result();
        if (result == null || result.requestId != planningRequest) {
            if (!planner.is_running() || planner.stats().latestRequestId != planningRequest) {
                drive.stop(); state = State.ABORTED;
                lastReport = "planner stopped or request superseded";
            }
            return;
        }
        stats.solves++;
        if (result.plan != null) stats.solveUsTotal += result.plan.solveUs;
        if (result.error != null) {
            state = State.ABORTED; drive.stop();
            lastReport = "planner failed: " + result.error;
            return;
        }
        lastPlan = result.plan;
        if (lastPlan == null || lastPlan.fittingRuns == 0 || lastPlan.runs[0].count == 0) {
            finish_empty_plan(here, "no fitting collection run");
            return;
        }
        if (!result.executable(here, 0, 1.0, Math.toRadians(8)) || !targets_still_present(lastPlan.runs[0])) {
            finish_empty_plan(here, "planning result rejected: moved start, changed targets or elapsed deadline");
            return;
        }
        if (!install_plan(here, planningParams)) {
            finish_empty_plan(here, "no fitting collection run");
            return;
        }
        double remaining = params.remainingS - (System.nanoTime() - runStartedNanos) / 1e9;
        if (stats.predictedS + Math.max(0, params.scoreOverheadS) > remaining) {
            finish_empty_plan(here, "planned run no longer fits after start-pose correction");
            return;
        }
        if (!planningReplan) stats.runsStarted++;
        state = State.DRIVING;
        lastReport = "planner request " + planningRequest + " accepted after " + result.solveMs + " ms";
    }

    private boolean targets_still_present(Butine.Run run) {
        List<Pollen> current = map.snapshot();
        double gate = Math.min(PICKUP_RADIUS_IN, map.config.matchDistanceIn);
        if (!Double.isFinite(gate) || gate < 0) return false;
        for (int i = 0; i < run.count; i++) {
            boolean found = false;
            for (Pollen pollen : current) {
                if (pollen.id == run.ids[i] && pollen.position.distance_to(run.poses[i]) <= gate) {
                    found = true; break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void finish_empty_plan(VisionPose here, String reason) {
        planner.cancel_pending();
        stats.planned = 0; stats.predictedS = 0;
        if (planningReplan) {
            legs = Collections.singletonList(home_leg(here, depot, params.kinematics));
            legIndex = 0; dispatched = armed = waitingPickup = false;
            state = State.DRIVING;
        } else {
            legs = Collections.emptyList(); state = State.IDLE;
        }
        lastReport = reason;
    }

    public static List<Leg> build_run_legs(Butine.Run run, VisionPose depot, Butine.Kinematics k) {
        return build_run_legs(run, depot, depot, k);
    }

    /** Each pickup is a separate line so every pollen is visited, followed by linear heading home. */
    public static List<Leg> build_run_legs(Butine.Run run, VisionPose start, VisionPose depot,
                                          Butine.Kinematics k) {
        ArrayList<Leg> out = new ArrayList<>(run.count + 1);
        VisionPose from = start;
        double heading = start.heading;
        double[] outgoing = new double[1];
        for (int i = 0; i < run.count; i++) {
            VisionPose observed = run.poses[i];
            double predicted = Butine.leg_time(heading, from, observed, k, outgoing) + k.pickupDwellS;
            VisionPose target = new VisionPose(observed.x, observed.y, outgoing[0]);
            double swing = Math.abs(VisionPose.wrap_pi(outgoing[0] - heading));
            out.add(new Leg(from, target, run.ids[i], predicted, Math.toDegrees(swing), false));
            from = target;
            heading = outgoing[0];
        }
        out.add(home_leg(from, depot, k));
        return Collections.unmodifiableList(out);
    }

    private static Leg home_leg(VisionPose from, VisionPose depot, Butine.Kinematics k) {
        double[] outgoing = new double[1];
        double predicted = Butine.leg_time(from.heading, from, depot, k, outgoing);
        double settle = Math.abs(VisionPose.wrap_pi(depot.heading - outgoing[0]));
        if (settle > k.turnDeadbandRad && k.omegaMaxRadS > 1e-9) {
            predicted += settle / k.omegaMaxRadS + k.turnSettleS;
        }
        return new Leg(from, depot, -1, predicted, Math.toDegrees(settle), true);
    }

    /** Call after follower.update(). Busy arming, timeouts and recovery never block the loop. */
    public void update() {
        if (!is_busy()) return;
        try {
            if (state == State.PLANNING) update_planning(); else update_run();
        } catch (RuntimeException exception) {
            if (planner != null) planner.cancel_pending();
            state = State.ABORTED;
            lastReport = "runner stopped after failure: " + exception;
            try { drive.stop(); }
            catch (RuntimeException stopFailure) { exception.addSuppressed(stopFailure); }
            throw exception;
        }
    }

    private void update_run() {
        VisionPose current = drive.pose();
        if (current == null || !current.is_finite()) {
            drive.stop();
            state = State.ABORTED;
            lastReport = "drive pose became invalid";
            return;
        }
        Leg leg = legs.get(legIndex);
        long now = System.nanoTime();
        if ((now - runStartedNanos) / 1e9 >= params.remainingS) {
            drive.stop();
            state = State.ABORTED;
            lastReport = "run exceeded available autonomous time";
            return;
        }
        if (waitingPickup) {
            if (!reached_target(leg)) {
                stats.legsMissed++;
                fail_leg(leg, "robot left pickup during dwell", now);
            } else if ((now - pickupReachedNanos) / 1e9 >= params.kinematics.pickupDwellS) {
                settle_leg(leg, true, now);
            }
            return;
        }
        if (!dispatched) {
            legStartedNanos = now;
            dispatched = true;
            armed = false;
            if (drive.pose().distance_to(leg.target) < 1e-3 && (!leg.returning
                    || Math.abs(VisionPose.wrap_pi(drive.pose().heading - leg.target.heading)) < 0.01)) {
                complete_leg(leg, now);
            } else {
                drive.follow(leg, speedScale);
            }
            return;
        }
        double elapsed = (now - legStartedNanos) / 1e9;
        boolean busy = drive.is_busy();
        if (!armed) {
            if (busy) armed = true;
            else if (elapsed >= ARM_TIMEOUT_S) {
                if (reached_target(leg)) {
                    complete_leg(leg, now);
                    return;
                }
                // A coincident home leg is held in place while its heading settles.
                if (leg.returning && drive.pose().distance_to(leg.target) <= DEPOT_RADIUS_IN) {
                    armed = true;
                } else {
                    stats.legsNeverBusy++;
                    fail_leg(leg, "follower never became busy", now);
                    return;
                }
            } else return;
        }
        double deadline = ARM_TIMEOUT_S + Math.max(2.0, 2.5 * leg.predictedS + 1.5);
        if (elapsed >= deadline) {
            stats.legsTimedOut++;
            fail_leg(leg, "leg timed out", now);
        } else if (!busy) {
            if (reached_target(leg)) complete_leg(leg, now);
            else if (leg.returning && drive.pose().distance_to(leg.target) <= DEPOT_RADIUS_IN) return;
            else {
                stats.legsMissed++;
                fail_leg(leg, "finished outside pickup/depot radius", now);
            }
        }
    }

    private void complete_leg(Leg leg, long now) {
        if (!leg.returning && params.kinematics.pickupDwellS > 0) {
            drive.stop();
            waitingPickup = true;
            pickupReachedNanos = now;
            lastReport = "pickup dwell at leg " + legIndex;
        } else settle_leg(leg, true, now);
    }

    private boolean reached_target(Leg leg) {
        VisionPose pose = drive.pose();
        double radius = leg.returning ? DEPOT_RADIUS_IN : PICKUP_RADIUS_IN;
        return pose.distance_to(leg.target) <= radius && (!leg.returning
                || Math.abs(VisionPose.wrap_pi(pose.heading - leg.target.heading)) <= DEPOT_HEADING_TOLERANCE_RAD);
    }

    private void settle_leg(Leg leg, boolean reached, long now) {
        stats.lastLegPredictedS = leg.predictedS;
        stats.lastLegActualS = (now - legStartedNanos) / 1e9;
        lastReport = "leg " + legIndex + " predicted " + leg.predictedS + " s, actual "
                + stats.lastLegActualS + " s";
        if (reached && !leg.returning) {
            pickupsThisRun++;
            if (remove_collected_track(leg)) stats.collected++;
        }
        if (leg.returning) {
            stats.runsCompleted++;
            state = State.COMPLETE;
        } else {
            legIndex++;
            dispatched = armed = waitingPickup = false;
        }
    }

    private boolean remove_collected_track(Leg leg) {
        List<Pollen> observed = map.snapshot();
        VisionPose reached = drive.pose();
        Pollen replacement = null;
        double matching = map.config.matchDistanceIn;
        double nearest = Double.isFinite(matching) ? Math.min(PICKUP_RADIUS_IN, Math.max(0, matching))
                : LimelightGeometry.TRACK_MATCH_DISTANCE_IN;
        for (Pollen pollen : observed) {
            double targetDistance = pollen.position.distance_to(leg.target);
            if (pollen.position.distance_to(reached) > PICKUP_RADIUS_IN) continue;
            if (pollen.id == leg.pollenId && targetDistance <= PICKUP_RADIUS_IN) return map.remove(pollen.id);
            if (targetDistance <= nearest) { replacement = pollen; nearest = targetDistance; }
        }
        return replacement != null && map.remove(replacement.id);
    }

    private void fail_leg(Leg leg, String reason, long now) {
        drive.stop();
        stats.lastLegPredictedS = leg.predictedS;
        stats.lastLegActualS = (now - legStartedNanos) / 1e9;
        lastReport = reason;
        if (leg.returning) {
            state = State.ABORTED;
            return;
        }
        // Failed pickups remain visible in the map. Exclude their locations separately,
        // including new IDs created when the camera observes that unreachable spot again.
        if (unreachablePositions.size() >= PollenMap.HARD_MAX_TRACKS) {
            unreachableIds.remove(0);
            unreachablePositions.remove(0);
        }
        unreachableIds.add(leg.pollenId);
        unreachablePositions.add(leg.target);
        if (replansThisRun < MAX_REPLANS && (params.capacity <= 0 || pickupsThisRun < params.capacity)) {
            replansThisRun++;
            stats.replans++;
            if (solve_from(drive.pose(), true)) return;
        }
        legs = Collections.singletonList(home_leg(drive.pose(), depot, params.kinematics));
        legIndex = 0;
        dispatched = armed = waitingPickup = false;
        state = State.DRIVING;
    }

    public boolean is_busy() { return state == State.PLANNING || state == State.DRIVING; }
    public State state() { return state; }
    public Stats stats() { return stats; }
    public Butine.Plan last_plan() { return lastPlan; }
    public VisionPose last_depot() { return depot; }
    public List<Leg> legs() { return legs; }
    public int leg_count() { return legs.size(); }
    public String last_report() { return lastReport; }
    public List<Integer> unreachable_ids() { return Collections.unmodifiableList(unreachableIds); }
    public void clear_unreachable() { unreachableIds.clear(); unreachablePositions.clear(); }

    public void stop() {
        if (planner != null) planner.cancel_pending();
        if (is_busy()) state = State.ABORTED;
        drive.stop();
    }

    public void telemetry(Telemetry telemetry) {
        telemetry.addData("Butine", "%s leg %d/%d", state, legIndex, legs.size());
        telemetry.addData("Butine runs", "%d done / %d started", stats.runsCompleted, stats.runsStarted);
        telemetry.addData("Butine pollen", "%d collected, %d planned", stats.collected, stats.planned);
        telemetry.addData("Butine faults", "%d timeout, %d never busy, %d missed",
                stats.legsTimedOut, stats.legsNeverBusy, stats.legsMissed);
        telemetry.addData("Butine solve", "%d solves, %.2f ms", stats.solves, stats.solveUsTotal / 1000);
        telemetry.addData("Butine leg", lastReport);
    }
}
