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
        default boolean supports_continuous() { return false; }
        default void follow_run(List<Leg> legs, double speedScale) {
            throw new UnsupportedOperationException("continuous collection is not supported by this drive");
        }
        default int path_index() { return -1; }
        default double path_fraction() { return Double.NaN; }
        default double speed_in_s() { return Double.NaN; }
        default void hold_pose(VisionPose pose) {
            throw new UnsupportedOperationException("pose holding is not supported by this drive");
        }
    }

    public enum State { IDLE, PLANNING, DRIVING, COMPLETE, ABORTED }

    public static final class Leg {
        public enum Kind { TRANSIT, PICKUP, DEPOT }
        public final VisionPose from, target;
        public final int pollenId;
        public final double predictedS, swingDeg;
        public final Kind kind;
        public final ButineField field;
        public final boolean returning;
        public final boolean intakeActive, constantHeading;
        public final double startSpeedInS, endSpeedInS;

        /** Legacy mathematical legs do not impose field geometry. */
        public Leg(VisionPose from, VisionPose target, int pollenId, double predictedS,
                   double swingDeg, boolean returning) {
            this(from, target, pollenId, predictedS, swingDeg,
                    returning ? Kind.DEPOT : Kind.PICKUP, ButineField.unrestricted());
        }

        public Leg(VisionPose from, VisionPose target, int pollenId, double predictedS,
                   double swingDeg, Kind kind, ButineField field) {
            this(from, target, pollenId, predictedS, swingDeg, kind, field,
                    kind == Kind.PICKUP, false, 0, 0);
        }

        public Leg(VisionPose from, VisionPose target, int pollenId, double predictedS,
                   double swingDeg, Kind kind, ButineField field, boolean intakeActive,
                   boolean constantHeading, double startSpeedInS, double endSpeedInS) {
            if (kind == null || field == null) throw new IllegalArgumentException("leg kind and field are required");
            this.from = from;
            this.target = target;
            this.pollenId = pollenId;
            this.predictedS = predictedS;
            this.swingDeg = swingDeg;
            this.kind = kind;
            this.field = field;
            this.returning = kind == Kind.DEPOT;
            this.intakeActive = intakeActive;
            this.constantHeading = constantHeading;
            this.startSpeedInS = startSpeedInS;
            this.endSpeedInS = endSpeedInS;
        }
    }

    public static final class Stats {
        public int runsStarted, runsCompleted, planned, collected, legsTimedOut, legsNeverBusy;
        public int legsMissed, solves, replans, sensorReturns;
        public double solveUsTotal, predictedS, lastLegPredictedS, lastLegActualS;
    }

    private static final double ARM_TIMEOUT_S = 0.4;
    private static final double PICKUP_RADIUS_IN = 5.0;
    private static final double DEPOT_RADIUS_IN = 6.0;
    public static final double TRANSIT_RADIUS_IN = 0.25;
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
    private ButineField field;
    private VisionPose depot;
    private State state = State.IDLE;
    private int legIndex, replansThisRun, pickupsThisRun;
    private long legStartedNanos, runStartedNanos;
    private long pickupReachedNanos;
    private double speedScale = 1.0;
    private boolean dispatched, armed;
    private boolean waitingPickup;
    private boolean recoveringHome;
    private String lastReport = "idle";
    private long planningRequest;
    private boolean planningReplan;
    private Butine.Params planningParams;
    private ButineIntake.Route continuousRoute;
    private VisionPose previousSweepPose;
    private int previousPathIndex;
    private boolean intakeEnabled;
    private boolean aligningCollection;
    private int continuousPathOffset;
    private boolean sensorReturnLatched;
    private final ArrayList<Integer> sweptIds = new ArrayList<>();
    private final ArrayList<VisionPose> sweptPositions = new ArrayList<>();
    private static final double PRECISE_DEPOT_RADIUS_IN = 0.5;
    private static final double PRECISE_DEPOT_HEADING_RAD = Math.toRadians(2);
    private static final double PRECISE_DEPOT_SPEED_IN_S = 1.0;

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
        if (start == null || !start.is_finite()) {
            drive.stop();
            throw new IllegalStateException("drive pose is invalid");
        }
        this.depot = depot;
        this.params = new Butine.Params(parameters);
        this.field = this.params.field;
        if (field == null) throw new IllegalArgumentException("field geometry is required");
        if (!field.is_unrestricted()) this.params.capacity = this.params.capacity <= 0
                ? Butine.POSSESSION_LIMIT : Math.min(this.params.capacity, Butine.POSSESSION_LIMIT);
        if (!field.is_clear(start) || !field.is_clear(depot)) {
            abort_run("start or depot is outside the permitted robot-center space");
            return false;
        }
        this.speedScale = speedScale;
        this.params.kinematics.cruiseSpeedInS *= speedScale;
        replansThisRun = pickupsThisRun = 0;
        continuousRoute = null; intakeEnabled = false; aligningCollection = false;
        continuousPathOffset = 0;
        sensorReturnLatched = false;
        sweptIds.clear(); sweptPositions.clear();
        recoveringHome = false;
        runStartedNanos = System.nanoTime();
        lastReport = "no fitting collection run";
        if (!solve_from(start, false)) {
            drive.stop();
            state = State.IDLE;
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
        if (!field.is_clear(start) || !field.is_clear(depot)) {
            lastReport = "cannot plan from a blocked start or depot";
            return false;
        }
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
        return install_plan(drive.pose(), local);
    }

    private boolean install_plan(VisionPose start, Butine.Params local) {
        stats.planned = 0;
        stats.predictedS = 0;
        if (lastPlan == null || lastPlan.field != field || local.field != field) {
            lastReport = "planning result used different field geometry";
            return false;
        }
        if (lastPlan.fittingRuns <= 0 || lastPlan.runs[0].count <= 0) {
            lastReport = lastPlan.rejectionReason == null || lastPlan.rejectionReason.isEmpty()
                    ? "no fitting collection run" : lastPlan.rejectionReason;
            legs = Collections.emptyList();
            legIndex = 0;
            dispatched = armed = waitingPickup = false;
            return false;
        }
        try {
            continuousRoute = lastPlan.runs[0].route;
            if (continuousRoute != null && !drive.supports_continuous()) {
                lastReport = "drive does not support continuous collection";
                return false;
            }
            if (continuousRoute != null && (start.distance_to(continuousRoute.waypoint(0)) > 0.25
                    || Math.abs(VisionPose.wrap_pi(start.heading - continuousRoute.waypoint(0).heading))
                    > PRECISE_DEPOT_HEADING_RAD)) {
                lastReport = "robot moved while the continuous route was being planned";
                return false;
            }
            legs = build_run_legs(lastPlan.runs[0], start, depot, local.kinematics, field);
        } catch (IllegalArgumentException unreachable) {
            lastReport = "planning result has no safe route: " + unreachable.getMessage();
            return false;
        }
        stats.planned = lastPlan.runs[0].count;
        stats.predictedS = 0;
        for (Leg leg : legs) stats.predictedS += leg.predictedS;
        if (!fits_remaining(stats.predictedS)) {
            legs = Collections.emptyList();
            stats.planned = 0; stats.predictedS = 0;
            lastReport = "planned run no longer fits after live-start correction";
            return false;
        }
        legIndex = 0;
        dispatched = armed = waitingPickup = false;
        recoveringHome = false;
        intakeEnabled = continuousRoute != null && continuousRoute.intake_active(0);
        previousSweepPose = start; previousPathIndex = 0;
        return true;
    }

    /** Poll publication only; solver, map mutation and drive ownership never overlap. */
    private void update_planning() {
        VisionPose here = drive.pose();
        if (!field.is_clear(here) || drive.is_busy()
                || (System.nanoTime() - runStartedNanos) / 1e9 >= params.remainingS) {
            planner.cancel_pending(); drive.stop(); state = State.ABORTED;
            lastReport = "planning cancelled: motion, invalid/blocked pose or expired match time";
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
        if (lastPlan != null && lastPlan.field != field) {
            abort_run("planning result used different field geometry");
            return;
        }
        if (lastPlan == null || lastPlan.fittingRuns == 0 || lastPlan.runs[0].count == 0) {
            finish_empty_plan(here, lastPlan != null && lastPlan.rejectionReason != null
                    && !lastPlan.rejectionReason.isEmpty()
                    ? lastPlan.rejectionReason : "no fitting collection run");
            return;
        }
        if (!result.executable(here, 0, 1.0, Math.toRadians(8)) || !targets_still_present(lastPlan.runs[0])) {
            finish_empty_plan(here, "planning result rejected: moved start, changed targets or elapsed deadline");
            return;
        }
        if (!install_plan(here, planningParams)) {
            finish_empty_plan(here, lastReport);
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
        if (planner != null) planner.cancel_pending();
        stats.planned = 0; stats.predictedS = 0;
        if (planningReplan) {
            install_return_route(here, reason);
        } else {
            legs = Collections.emptyList();
            drive.stop(); state = State.IDLE;
            lastReport = reason;
        }
    }

    public static List<Leg> build_run_legs(Butine.Run run, VisionPose depot, Butine.Kinematics k) {
        return build_run_legs(run, depot, depot, k);
    }

    /** Legacy mathematical overload; production dispatch supplies the plan's field explicitly. */
    public static List<Leg> build_run_legs(Butine.Run run, VisionPose start, VisionPose depot,
                                          Butine.Kinematics k) {
        return build_run_legs(run, start, depot, k, ButineField.unrestricted());
    }

    public static List<Leg> build_run_legs(Butine.Run run, VisionPose depot, Butine.Kinematics k,
                                          ButineField field) {
        return build_run_legs(run, depot, depot, k, field);
    }

    /** Expand the deterministic routes costed by the solver; dwell occurs only at real pickups. */
    public static List<Leg> build_run_legs(Butine.Run run, VisionPose start, VisionPose depot,
                                          Butine.Kinematics k, ButineField field) {
        if (run == null || k == null || field == null || !field.is_clear(start) || !field.is_clear(depot))
            throw new IllegalArgumentException("clear start/depot, run, kinematics and field are required");
        ArrayList<Leg> out = new ArrayList<>(run.count + 1);
        if (run.route != null) {
            ButineIntake.Route route = run.route;
            for (int i = 0; i < route.segment_count(); i++) {
                VisionPose from = i == 0 ? start : route.waypoint(i);
                VisionPose target = route.waypoint(i + 1);
                if (!field.segment_clear(from, target))
                    throw new IllegalArgumentException("continuous route segment is blocked");
                boolean last = i == route.segment_count() - 1;
                int pickupId = -1;
                for (int p = 0; p < run.count; p++)
                    if (route.pickup_segment(p) == i) { pickupId = run.ids[p]; break; }
                Leg.Kind kind = last ? Leg.Kind.DEPOT : pickupId >= 0 ? Leg.Kind.PICKUP : Leg.Kind.TRANSIT;
                out.add(new Leg(from, target, pickupId, route.segment_time_s(i), 0, kind, field,
                        route.intake_active(i), route.intake_active(i), route.segment_start_speed_in_s(i),
                        route.segment_end_speed_in_s(i)));
            }
            return Collections.unmodifiableList(out);
        }
        VisionPose from = start;
        for (int i = 0; i < run.count; i++) {
            from = append_route(out, from, run.poses[i], run.ids[i], Leg.Kind.PICKUP, k, field);
        }
        append_route(out, from, depot, -1, Leg.Kind.DEPOT, k, field);
        return Collections.unmodifiableList(out);
    }

    private static VisionPose append_route(List<Leg> out, VisionPose from, VisionPose destination,
                                           int pollenId, Leg.Kind finalKind, Butine.Kinematics k,
                                           ButineField field) {
        ButineField.Route route = field.route(from, destination);
        if (route == null) throw new IllegalArgumentException("blocked endpoint or unreachable destination");
        VisionPose previous = from;
        double heading = from.heading;
        double[] outgoing = new double[1];
        int count = Math.max(1, route.point_count());
        for (int i = 0; i < count; i++) {
            boolean last = i == count - 1;
            VisionPose point = route.point_count() == 0 ? destination : route.point(i);
            Leg.Kind kind = last ? finalKind : Leg.Kind.TRANSIT;
            double predicted = Butine.leg_time(heading, previous, point, k, outgoing);
            double swing = Math.abs(VisionPose.wrap_pi(outgoing[0] - heading));
            VisionPose target = new VisionPose(point.x, point.y,
                    kind == Leg.Kind.DEPOT ? destination.heading : outgoing[0]);
            if (kind == Leg.Kind.PICKUP) predicted += k.pickupDwellS;
            else if (kind == Leg.Kind.DEPOT) {
                double settle = Math.abs(VisionPose.wrap_pi(destination.heading - outgoing[0]));
                if (settle > k.turnDeadbandRad && k.omegaMaxRadS > 1e-9)
                    predicted += settle / k.omegaMaxRadS + k.turnSettleS;
                swing = settle;
            }
            out.add(new Leg(previous, target, kind == Leg.Kind.PICKUP ? pollenId : -1,
                    predicted, Math.toDegrees(swing), kind, field));
            previous = target;
            heading = target.heading;
        }
        return previous;
    }

    private boolean fits_remaining(double predictedS) {
        double remaining = params.remainingS - (System.nanoTime() - runStartedNanos) / 1e9;
        return Double.isFinite(predictedS) && predictedS >= 0
                && predictedS + (sensorReturnLatched ? 0 : Math.max(0, params.scoreOverheadS)) <= remaining;
    }

    /** A real full-load sensor overrides visual pickup estimates and the remaining collection path. */
    public boolean return_to_depot_if_loaded(boolean loaded) {
        if (!loaded || !is_busy() || sensorReturnLatched || recoveringHome) return false;
        sensorReturnLatched = true;
        intakeEnabled = false;
        int activeIndex = legIndex;
        if (continuousRoute != null && dispatched) {
            int reportedIndex = drive.path_index();
            if (reportedIndex >= 0 && reportedIndex + continuousPathOffset < legs.size())
                activeIndex = reportedIndex + continuousPathOffset;
        }
        if (state == State.DRIVING && dispatched && !aligningCollection && activeIndex < legs.size()
                && (continuousRoute != null ? !legs.get(activeIndex).intakeActive : legs.get(activeIndex).returning))
            return false;
        boolean wasPlanning = state == State.PLANNING;
        if (planner != null) planner.cancel_pending();
        try {
            drive.stop();
            VisionPose here = drive.pose();
            if (!field.is_clear(here)) {
                abort_run("full-load sensor: invalid or blocked live return origin");
                return true;
            }
            if (!drive.supports_continuous()) {
                continuousRoute = null;
                install_return_route(here, "full-load sensor");
            } else {
                ButineIntake.Route home = ButineIntake.return_route(here, depot, params);
                if (home == null || !fits_remaining(home.timeS)) {
                    abort_run("full-load sensor: no clear return fits the remaining time");
                    return true;
                }
                Butine.Run returning = new Butine.Run(new int[0], new VisionPose[0], home.timeS, false, home);
                List<Leg> returnLegs = build_run_legs(returning, here, depot, params.kinematics, field);
                if (returnLegs.isEmpty()) {
                    abort_run("full-load sensor: empty return route");
                    return true;
                }
                continuousRoute = home;
                legs = returnLegs;
                legIndex = continuousPathOffset = previousPathIndex = 0;
                previousSweepPose = here;
                dispatched = armed = waitingPickup = aligningCollection = false;
                recoveringHome = true;
                stats.predictedS = home.timeS;
                state = State.DRIVING;
                lastReport = "full-load sensor: replaced collection path with safe depot return";
            }
            if (state == State.DRIVING) {
                if (wasPlanning) stats.runsStarted++;
                stats.sensorReturns++;
            }
            return true;
        } catch (RuntimeException failure) {
            intakeEnabled = false;
            state = State.ABORTED;
            lastReport = "full-load return failed: " + failure;
            try { drive.stop(); }
            catch (RuntimeException stopFailure) { failure.addSuppressed(stopFailure); }
            throw failure;
        }
    }

    private double remaining_route_time(VisionPose here) {
        double total = 0;
        VisionPose previous = here;
        double[] heading = {here.heading};
        Butine.Kinematics k = params.kinematics;
        for (int i = legIndex; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            total += Butine.leg_time(heading[0], previous, leg.target, k, heading);
            if (leg.kind == Leg.Kind.PICKUP) total += k.pickupDwellS;
            else if (leg.kind == Leg.Kind.DEPOT) {
                double settle = Math.abs(VisionPose.wrap_pi(leg.target.heading - heading[0]));
                if (settle > k.turnDeadbandRad && k.omegaMaxRadS > 1e-9)
                    total += settle / k.omegaMaxRadS + k.turnSettleS;
            }
            previous = leg.target;
        }
        return total;
    }

    private void install_return_route(VisionPose here, String reason) {
        ArrayList<Leg> returning = new ArrayList<>();
        try {
            append_route(returning, here, depot, -1, Leg.Kind.DEPOT, params.kinematics, field);
        } catch (IllegalArgumentException unreachable) {
            abort_run(reason + "; no safe return: " + unreachable.getMessage());
            return;
        }
        double predicted = 0;
        for (Leg leg : returning) predicted += leg.predictedS;
        if (!fits_remaining(predicted)) {
            abort_run(reason + "; safe return no longer fits remaining time");
            return;
        }
        legs = Collections.unmodifiableList(returning);
        legIndex = 0; dispatched = armed = waitingPickup = false;
        stats.planned = 0; stats.predictedS = predicted;
        recoveringHome = true;
        state = State.DRIVING;
        lastReport = reason + "; routed return to depot";
    }

    private void abort_run(String reason) {
        intakeEnabled = false;
        if (planner != null) planner.cancel_pending();
        drive.stop();
        state = State.ABORTED;
        lastReport = reason;
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
        if (continuousRoute != null) { update_continuous(); return; }
        VisionPose current = drive.pose();
        if (!field.is_clear(current)) {
            abort_run("drive pose became invalid or entered forbidden robot-center space");
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
            if (leg.field != field || !field.segment_clear(current, leg.target)) {
                recover_dispatch(leg, current);
                return;
            }
            if (!fits_remaining(remaining_route_time(current))) {
                abort_run("remaining routed run no longer fits available autonomous time");
                return;
            }
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

    private void update_continuous() {
        VisionPose here = drive.pose();
        long now = System.nanoTime();
        if (!field.is_clear(here)) {
            abort_run("continuous drive pose is invalid or blocked");
            return;
        }
        if ((now - runStartedNanos) / 1e9 >= params.remainingS) {
            abort_run("continuous collection exceeded available time");
            return;
        }
        if (!dispatched) {
            double remainingPrediction = stats.predictedS;
            if (aligningCollection) remainingPrediction -= Math.min(legs.get(0).predictedS,
                    Math.max(0, (now - legStartedNanos) / 1e9));
            if (!field.segment_clear(here, legs.get(0).target) || !fits_remaining(remainingPrediction)) {
                abort_run("continuous route no longer fits the live start or deadline");
                return;
            }
            if (aligningCollection) {
                VisionPose target = legs.get(0).target;
                if (here.distance_to(target) <= 0.25
                        && Math.abs(VisionPose.wrap_pi(here.heading - target.heading)) <= Math.toRadians(1)
                        && Double.isFinite(drive.speed_in_s()) && drive.speed_in_s() <= PRECISE_DEPOT_SPEED_IN_S) {
                    aligningCollection = false; continuousPathOffset = 1;
                    if (!fits_remaining(Math.max(0, stats.predictedS - legs.get(0).predictedS))) {
                        abort_run("aligned route no longer fits the remaining time");
                        return;
                    }
                } else {
                    if ((now - legStartedNanos) / 1e9 > Math.max(2, 2.5 * legs.get(0).predictedS + 1.5))
                        abort_run("intake alignment at depot timed out");
                    return;
                }
            } else if (!legs.get(0).intakeActive && legs.size() > 1
                    && legs.get(0).from.distance_to(legs.get(0).target) < 1e-8
                    && Math.abs(VisionPose.wrap_pi(legs.get(0).from.heading - legs.get(0).target.heading)) > 1e-8) {
                intakeEnabled = false; aligningCollection = true; legStartedNanos = now;
                drive.hold_pose(legs.get(0).target);
                return;
            }
            previousSweepPose = here; previousPathIndex = continuousPathOffset;
            legStartedNanos = now; dispatched = true; armed = false;
            intakeEnabled = !sensorReturnLatched && legs.get(continuousPathOffset).intakeActive;
            drive.follow_run(legs.subList(continuousPathOffset, legs.size()), speedScale);
            return;
        }
        boolean busy = drive.is_busy();
        double elapsed = (now - legStartedNanos) / 1e9;
        if (busy) armed = true;
        int index = drive.path_index() + continuousPathOffset;
        if (index < previousPathIndex || index < 0 || index >= legs.size()) {
            abort_run("continuous follower returned invalid path progress");
            return;
        }
        if (!field.segment_clear(previousSweepPose, here)
                || !field.segment_clear(here, legs.get(index).target)) {
            abort_run("continuous movement left its clear route corridor");
            return;
        }
        if (intakeEnabled) {
            // The motor was enabled throughout this sampled interval; only actual poses establish contact.
            if (!legs.get(index).intakeActive && legs.get(previousPathIndex).intakeActive) {
                if (previousPathIndex + 1 != index) {
                    abort_run("missed continuous collection progress");
                    return;
                }
            }
            if (legs.get(previousPathIndex).intakeActive)
                collect_swept_pollen(previousSweepPose, here);
            if (state == State.ABORTED) return;
            // Keep pulling after first touch: the planned active segment includes the intake overshoot.
            if (!legs.get(index).intakeActive) intakeEnabled = false;
        }
        previousSweepPose = here; previousPathIndex = index; legIndex = index;
        if (!armed && !busy && elapsed >= ARM_TIMEOUT_S && !at_precise_depot()) {
            stats.legsNeverBusy++;
            abort_run("continuous follower never became busy");
        } else if (!busy && at_precise_depot()) {
            intakeEnabled = false;
            stats.lastLegPredictedS = stats.predictedS;
            stats.lastLegActualS = elapsed;
            if (pickupsThisRun == 0 && !sensorReturnLatched) {
                abort_run("returned without confirmed intake contacts");
            } else {
                stats.runsCompleted++; state = State.COMPLETE;
                lastReport = sensorReturnLatched ? "full-load return complete: depot settled"
                        : "continuous run complete: " + pickupsThisRun + " contacts, depot settled";
            }
        } else if (elapsed >= ARM_TIMEOUT_S + Math.max(3, 2.5 * stats.predictedS + 1.5)) {
            stats.legsTimedOut++;
            abort_run("continuous run or precise depot settling timed out");
        }
    }

    private boolean at_precise_depot() {
        VisionPose here = drive.pose();
        double speed = drive.speed_in_s();
        return here != null && here.is_finite() && here.distance_to(depot) <= PRECISE_DEPOT_RADIUS_IN
                && Math.abs(VisionPose.wrap_pi(here.heading - depot.heading)) <= PRECISE_DEPOT_HEADING_RAD
                && Double.isFinite(speed) && speed <= PRECISE_DEPOT_SPEED_IN_S;
    }

    private void collect_swept_pollen(VisionPose from, VisionPose to) {
        ArrayList<Pollen> contacts = new ArrayList<>();
        ArrayList<Double> fractions = new ArrayList<>();
        for (Pollen pollen : map.snapshot()) {
            double fraction = live_contact_fraction(from, to, pollen.position);
            if (!Double.isFinite(fraction)) continue;
            boolean duplicate = sweptIds.contains(pollen.id);
            for (VisionPose old : sweptPositions)
                if (old.distance_to(pollen.position) < 0.5) { duplicate = true; break; }
            if (duplicate) { map.remove(pollen.id); continue; }
            int slot = 0;
            while (slot < fractions.size() && (fractions.get(slot) < fraction
                    || (fractions.get(slot) == fraction && contacts.get(slot).id < pollen.id))) slot++;
            contacts.add(slot, pollen); fractions.add(slot, fraction);
        }
        if (pickupsThisRun + contacts.size() > params.capacity) {
            abort_run("new pollen contact would exceed the four-pollen possession limit");
            return;
        }
        for (Pollen pollen : contacts) {
            // Count contact even when camera expiry concurrently removed the old track.
            map.remove(pollen.id);
            sweptIds.add(pollen.id); sweptPositions.add(pollen.position);
            pickupsThisRun++; stats.collected++;
        }
    }

    private double live_contact_fraction(VisionPose from, VisionPose to, VisionPose pollen) {
        double rotation = VisionPose.wrap_pi(to.heading - from.heading);
        double exact = ButineIntake.contact_fraction(from, to, pollen, from.heading,
                continuousRoute.intakeWidthIn, continuousRoute.intakeForwardOffsetIn,
                continuousRoute.pollenRadiusIn);
        if (Math.abs(rotation) < 1e-8) return exact;
        // A turning/localization-error interval only claims contact at an actually interpolated mouth.
        int samples = Math.min(256, Math.max(2, (int) Math.ceil(from.distance_to(to) / .25
                + Math.abs(rotation) / .005)));
        for (int i = 0; i <= samples + 1; i++) {
            double t = i == samples + 1 ? exact : (double) i / samples;
            if (!Double.isFinite(t)) continue;
            VisionPose point = new VisionPose(from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t, from.heading + rotation * t);
            if (Double.isFinite(ButineIntake.contact_fraction(point, point, pollen, point.heading,
                    continuousRoute.intakeWidthIn, continuousRoute.intakeForwardOffsetIn,
                    continuousRoute.pollenRadiusIn))) return t;
        }
        return Double.NaN;
    }

    private void complete_leg(Leg leg, long now) {
        if (leg.kind == Leg.Kind.PICKUP && params.kinematics.pickupDwellS > 0) {
            drive.stop();
            waitingPickup = true;
            pickupReachedNanos = now;
            lastReport = "pickup dwell at leg " + legIndex;
        } else settle_leg(leg, true, now);
    }

    private boolean reached_target(Leg leg) {
        VisionPose pose = drive.pose();
        double radius = leg.kind == Leg.Kind.TRANSIT ? TRANSIT_RADIUS_IN
                : leg.returning ? DEPOT_RADIUS_IN : PICKUP_RADIUS_IN;
        return field.is_clear(pose) && pose.distance_to(leg.target) <= radius && (!leg.returning
                || Math.abs(VisionPose.wrap_pi(pose.heading - leg.target.heading)) <= DEPOT_HEADING_TOLERANCE_RAD);
    }

    private void settle_leg(Leg leg, boolean reached, long now) {
        stats.lastLegPredictedS = leg.predictedS;
        stats.lastLegActualS = (now - legStartedNanos) / 1e9;
        lastReport = "leg " + legIndex + " predicted " + leg.predictedS + " s, actual "
                + stats.lastLegActualS + " s";
        if (reached && leg.kind == Leg.Kind.PICKUP) {
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
        if (leg.returning || recoveringHome) {
            state = State.ABORTED;
            return;
        }
        // Failed pickups remain visible in the map. Exclude their locations separately,
        // including new IDs created when the camera observes that unreachable spot again.
        if (leg.kind == Leg.Kind.PICKUP) {
            if (unreachablePositions.size() >= PollenMap.HARD_MAX_TRACKS) {
                unreachableIds.remove(0);
                unreachablePositions.remove(0);
            }
            unreachableIds.add(leg.pollenId);
            unreachablePositions.add(leg.target);
        }
        if (replansThisRun < MAX_REPLANS && (params.capacity <= 0 || pickupsThisRun < params.capacity)) {
            replansThisRun++;
            stats.replans++;
            if (solve_from(drive.pose(), true)) return;
        }
        install_return_route(drive.pose(), reason);
    }

    private void recover_dispatch(Leg leg, VisionPose here) {
        drive.stop();
        if (leg.field != field) {
            abort_run("leg field geometry no longer matches the active plan");
            return;
        }
        if (replansThisRun >= MAX_REPLANS || recoveringHome) {
            abort_run("live-origin segment is blocked; reroute limit reached");
            return;
        }
        replansThisRun++;
        stats.replans++;
        boolean pickupAhead = false;
        for (int i = legIndex; i < legs.size(); i++)
            if (legs.get(i).kind == Leg.Kind.PICKUP) { pickupAhead = true; break; }
        if (pickupAhead && (params.capacity <= 0 || pickupsThisRun < params.capacity)
                && solve_from(here, true)) return;
        install_return_route(here, "live-origin segment blocked; rerouted safely");
    }

    public boolean is_busy() { return state == State.PLANNING || state == State.DRIVING; }
    /** Mechanism owners must apply this after update(), before their next motor output. */
    public boolean intake_required() {
        return state == State.DRIVING && !recoveringHome && !sensorReturnLatched
                && (continuousRoute == null || intakeEnabled);
    }
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
        intakeEnabled = false;
        if (planner != null) planner.cancel_pending();
        if (is_busy()) state = State.ABORTED;
        drive.stop();
    }

    public void telemetry(Telemetry telemetry) {
        telemetry.addData("Butine", "%s leg %d/%d", state, legIndex, legs.size());
        if (field != null) telemetry.addData("Butine clearance",
                "body %.1fx%.1f, gap %.1f, field %.1f, tracking %.1f, radius %.2f in",
                field.robotLengthIn, field.robotWidthIn, field.bodyClearanceIn,
                field.fieldReserveIn, field.trackingReserveIn, field.radiusIn);
        telemetry.addData("Butine runs", "%d done / %d started", stats.runsCompleted, stats.runsStarted);
        telemetry.addData("Butine pollen", "%d collected, %d planned", stats.collected, stats.planned);
        telemetry.addData("Butine faults", "%d timeout, %d never busy, %d missed",
                stats.legsTimedOut, stats.legsNeverBusy, stats.legsMissed);
        telemetry.addData("Butine solve", "%d solves, %.2f ms", stats.solves, stats.solveUsTotal / 1000);
        telemetry.addData("Butine leg", lastReport);
    }
}
