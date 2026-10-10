package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Independent arithmetic and exhaustive small-field assignment oracle; no robot hardware. */
public final class PlannerAuditChecks {
    private static int checks, oracleFields, oracleStates, heuristicHaulGaps;
    private static double largestObservedSolveMs;
    private static final VisionPose DEPOT = new VisionPose(0, 0, 0);

    public static void main(String[] args) {
        calculated_examples();
        malformed_inputs_and_extreme_arithmetic();
        exhaustive_small_assignment_oracles();
        capacity_horizon_and_objective();
        large_maps_and_budgets();
        System.out.println("Planner audit passed: " + checks + " checks; " + oracleFields
                + " small-field exhaustive oracles; " + oracleStates + " oracle states; "
                + heuristicHaulGaps + " accepted heuristic haul gaps; max observed solve "
                + largestObservedSolveMs + " ms");
    }

    private static void check(boolean good, String message) {
        ++checks;
        if (!good) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= 1e-8 * Math.max(1, Math.abs(expected)),
                message + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable operation, String message) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException | NullPointerException expected) { rejected = true; }
        check(rejected, message);
    }

    private static Pollen item(int id, double x, double y) {
        return new Pollen(id, new VisionPose(x, y, 0), 1, 12, 10L);
    }

    private static Butine.Params fast_settings() {
        Butine.Params p = new Butine.Params();
        p.computeBudgetMs = 0; p.candidateCap = 30;
        return p;
    }

    // This model does not call any Butine cost helper or use its lookup tables.
    private static double independent_straight(double distance, Butine.Kinematics k) {
        if (distance <= 1e-9) return 0;
        double speed = Math.max(k.cruiseSpeedInS, 1e-9);
        double acceleration = Math.max(k.accelInS2, 1e-9);
        double deceleration = Math.max(k.decelInS2, 1e-9);
        double accelerateDistance = speed * speed / (2 * acceleration);
        double brakeDistance = speed * speed / (2 * deceleration);
        if (distance >= accelerateDistance + brakeDistance)
            return speed / acceleration + speed / deceleration
                    + (distance - accelerateDistance - brakeDistance) / speed;
        return Math.sqrt(2 * distance * (1 / acceleration + 1 / deceleration));
    }

    private static double angular_distance(double a, double b) {
        return Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
    }

    private static double independent_run(VisionPose start, VisionPose depot,
                                           List<VisionPose> members, Butine.Kinematics k) {
        if (members.isEmpty()) return 0;
        VisionPose previous = start;
        double heading = start.heading, seconds = 0;
        for (int i = 0; i <= members.size(); ++i) {
            VisionPose target = i < members.size() ? members.get(i) : depot;
            double distance = Math.hypot(target.x - previous.x, target.y - previous.y);
            if (distance >= .001) {
                double direction = Math.atan2(target.y - previous.y, target.x - previous.x);
                double translation = independent_straight(distance, k);
                double swing = angular_distance(direction, heading);
                double rotation = swing > k.turnDeadbandRad && k.omegaMaxRadS > 1e-9
                        ? swing / k.omegaMaxRadS + k.turnSettleS : 0;
                seconds += translation + k.turnCoupling * Math.max(0, rotation - translation);
                heading = direction;
            }
            if (i < members.size()) seconds += k.pickupDwellS;
            previous = target;
        }
        double settle = angular_distance(depot.heading, heading);
        if (settle > k.turnDeadbandRad && k.omegaMaxRadS > 1e-9)
            seconds += settle / k.omegaMaxRadS + k.turnSettleS;
        return seconds;
    }

    private static void calculated_examples() {
        Butine.Params p = fast_settings();
        p.kinematics.cruiseSpeedInS = 2; p.kinematics.accelInS2 = 2;
        p.kinematics.decelInS2 = 2; p.kinematics.omegaMaxRadS = 0;
        p.scoreOverheadS = 1; p.capacity = 3; p.lookaheadRuns = 1; p.remainingS = 9;
        List<Pollen> square = Arrays.asList(item(1, 2, 0), item(2, 2, 2), item(3, 0, 2));
        Butine.Plan plan = Butine.solve(square, DEPOT, p);
        // Four two-inch legs: each takes 1 s accelerating and 1 s braking, then 1 s scoring.
        near(8, plan.runs[0].timeS, "calculated square travel");
        near(9, plan.committedTimeS, "calculated square with scoring");
        check(plan.expectedCollected == 3, "exact deadline includes square haul");
        p.remainingS = 8.9;
        plan = Butine.solve(square, DEPOT, p);
        check(plan.expectedCollected < 3 && plan.committedTimeS <= 8.9, "just below deadline trims haul");
        validate_plan(plan, p);
        p.remainingS = 10; p.scoreOverheadS = 0;
        VisionPose actualStart = new VisionPose(2, 0, 0);
        plan = Butine.solve(Collections.singletonList(item(4, 2, 2)), actualStart, DEPOT, p);
        near(3 + Math.sqrt(2), plan.runs[0].timeS, "live-start two-leg arithmetic");
        validate_plan(plan, p);
        p.kinematics.pickupDwellS = .25;
        plan = Butine.solve(Collections.singletonList(item(5, 2, 0)), DEPOT, p);
        near(4.25, plan.runs[0].timeS, "pickup dwell counted once");
        p.remainingS = 0;
        check(Butine.solve(square, DEPOT, p).expectedCollected == 0, "expired clock collects nothing");
    }

    private static void malformed_inputs_and_extreme_arithmetic() {
        Butine.Params p = fast_settings();
        List<Pollen> valid = Collections.singletonList(item(1, 2, 0));
        rejects(() -> Butine.solve(valid, new VisionPose(Double.NaN, 0, 0), p), "reject NaN depot");
        rejects(() -> Butine.solve(valid, new VisionPose(0, 0, Double.POSITIVE_INFINITY), DEPOT, p), "reject infinite live heading");
        rejects(() -> Butine.leg_time(0, DEPOT, new VisionPose(Double.NaN, 1, 0), p.kinematics), "reject NaN calibration pose");
        rejects(() -> Butine.leg_time(Double.NaN, DEPOT, DEPOT, p.kinematics), "reject NaN calibration heading");
        rejects(() -> Butine.leg_time(0, DEPOT, DEPOT, p.kinematics, new double[0]), "reject empty heading buffer");
        rejects(() -> Butine.straight_time(Double.NaN, p.kinematics), "reject NaN distance");
        rejects(() -> Butine.run_time(DEPOT, new VisionPose[0], -1, p.kinematics), "reject negative calibration count");
        rejects(() -> new Butine.Run(new int[] {1}, new VisionPose[] {DEPOT}, Double.NaN, true), "reject NaN manually built run time");
        rejects(() -> new Butine.Run(new int[] {1, 1}, new VisionPose[] {DEPOT, DEPOT}, 0, true), "reject duplicate manually built run ids");
        rejects(() -> new Butine.Run(new int[0], new VisionPose[0], 0, true), "reject empty fitting manually built run");
        rejects(() -> new Butine.Run(new int[] {1}, new VisionPose[] {new VisionPose(0, 0, Double.NaN)}, 0, true),
                "reject NaN manually built run pose");
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Butine.Params badP = new Butine.Params(p); badP.remainingS = bad;
            final Butine.Params badTime = badP;
            rejects(() -> Butine.solve(valid, DEPOT, badTime), "reject nonfinite time budget");
            badP = new Butine.Params(p); badP.kinematics.cruiseSpeedInS = bad;
            final Butine.Params badK = badP;
            rejects(() -> Butine.solve(valid, DEPOT, badK), "reject nonfinite speed");
        }
        List<Pollen> malformed = Arrays.asList(null, item(1, 2, 0), item(1, 3, 0),
                new Pollen(2, new VisionPose(2, 0, Double.NaN), 1, 0, 0),
                new Pollen(3, new VisionPose(Double.POSITIVE_INFINITY, 0, 0), 1, 0, 0),
                new Pollen(4, new VisionPose(2, 0, 0), Double.NaN, 0, 0));
        Butine.Plan plan = Butine.solve(malformed, DEPOT, p);
        check(plan.candidatesConsidered == 1 && plan.expectedCollected == 1, "reject bad candidates and duplicate ids");
        validate_plan(plan, p);
        Butine.Kinematics huge = new Butine.Kinematics();
        huge.cruiseSpeedInS = huge.accelInS2 = huge.decelInS2 = 1e200;
        huge.omegaMaxRadS = 0;
        double tiny = Butine.straight_time(1, huge);
        check(Double.isFinite(tiny) && Math.abs(tiny / 2e-100 - 1) < 1e-12,
                "finite extreme triangular time expected 2e-100, got " + tiny);
        near(2, Butine.straight_time(1e200, huge), "large-distance exact ramp arithmetic");
        Butine.Params largeP = new Butine.Params(p); largeP.kinematics = huge;
        plan = Butine.solve(Collections.singletonList(item(8, 1e200, 0)), DEPOT, largeP);
        near(4, plan.runs[0].timeS, "large-coordinate finite round trip");
        rejects(() -> Butine.leg_time(0, new VisionPose(-1e308, 0, 0), new VisionPose(1e308, 0, 0), huge),
                "reject unrepresentable coordinate differences");
        Butine.Kinematics slow = new Butine.Kinematics(); slow.cruiseSpeedInS = 1e-9;
        rejects(() -> Butine.straight_time(1e308, slow), "reject unrepresentable motion time");
        for (double scale : new double[] {1e-9, 1e-4, 1, 1e4, 1e100, 1e200, 1e300}) {
            Butine.Kinematics k = new Butine.Kinematics();
            k.cruiseSpeedInS = k.accelInS2 = k.decelInS2 = scale;
            check(Double.isFinite(Butine.straight_time(12, k)), "finite supported scaling " + scale);
        }
        Butine.Params wrappedP = new Butine.Params(p);
        wrappedP.remainingS = 30;
        plan = Butine.solve(valid, new VisionPose(0, 0, 1e308), new VisionPose(0, 0, -1e308), wrappedP);
        check(Double.isFinite(plan.runs[0].timeS), "large finite headings are reduced before subtraction");
    }

    private static final class Oracle {
        final List<Pollen> items;
        final VisionPose start, depot;
        final Butine.Params p;
        final int capacity, horizon;
        int bestCollected = 0;
        int[] bestLengths;
        double bestObjective = Double.POSITIVE_INFINITY;

        Oracle(List<Pollen> items, VisionPose start, VisionPose depot, Butine.Params p) {
            this.items = items; this.start = start; this.depot = depot; this.p = p;
            capacity = p.capacity <= 0 ? items.size() : p.capacity;
            int clockHorizon = p.scoreOverheadS > 1e-9 ? Math.max(1, (int) (p.remainingS / p.scoreOverheadS)) : 3;
            horizon = p.capacity <= 0 ? 1 : Math.max(1, Math.min(3, Math.min(p.lookaheadRuns,
                    Math.min((items.size() + capacity - 1) / capacity, clockHorizon))));
            bestLengths = new int[horizon];
        }

        void solve() { runs(0, 0, 0, 0, 0, new int[horizon]); }

        void runs(int run, int used, int collected, double elapsed, double objective, int[] lengths) {
            ++oracleStates;
            if (better(collected, lengths, objective)) {
                bestCollected = collected; bestLengths = lengths.clone(); bestObjective = objective;
            }
            if (run == horizon) return;
            members(run, used, used, collected, elapsed, objective, lengths, new ArrayList<VisionPose>());
        }

        void members(int run, int usedBeforeRun, int used, int collected, double elapsed,
                     double objective, int[] lengths, ArrayList<VisionPose> route) {
            if (!route.isEmpty()) {
                double seconds = independent_run(run == 0 ? start : depot, depot, route, p.kinematics)
                        + Math.max(0, p.scoreOverheadS);
                if (elapsed + seconds <= p.remainingS + 1e-9) {
                    lengths[run] = route.size();
                    runs(run + 1, used, collected + route.size(), elapsed + seconds,
                            objective + Math.pow(Math.max(0, Math.min(1, p.discount)), run) * seconds, lengths);
                    lengths[run] = 0;
                }
            }
            if (route.size() == capacity) return;
            for (int c = 0; c < items.size(); ++c) {
                if ((used & (1 << c)) != 0) continue;
                route.add(items.get(c).position);
                members(run, usedBeforeRun, used | (1 << c), collected, elapsed, objective, lengths, route);
                route.remove(route.size() - 1);
            }
        }

        boolean better(int collected, int[] lengths, double objective) {
            if (collected != bestCollected) return collected > bestCollected;
            for (int r = 0; r < horizon; ++r)
                if (lengths[r] != bestLengths[r]) return lengths[r] > bestLengths[r];
            return objective < bestObjective - 1e-9;
        }
    }

    private static void exhaustive_small_assignment_oracles() {
        Random random = new Random(334455);
        for (int field = 0; field < 72; ++field) {
            int size = 1 + field % 6;
            List<Pollen> items = new ArrayList<>();
            for (int i = 0; i < size; ++i) items.add(item(i, 2 + random.nextDouble() * 55, 2 + random.nextDouble() * 55));
            VisionPose depot = new VisionPose(10, 8, -.4);
            VisionPose start = new VisionPose(field % 2 == 0 ? 10 : 30, 8, .9);
            Butine.Params p = fast_settings();
            p.capacity = field % 4 == 0 ? 0 : 1 + field % 3;
            p.lookaheadRuns = 1 + field % 3; p.discount = field % 2 == 0 ? .75 : 1;
            p.scoreOverheadS = .4; p.remainingS = field % 3 == 0 ? 2.6 : 4 + field % 7;
            p.kinematics.pickupDwellS = .1;
            if (field % 7 == 0) { p.computeBudgetMs = 1000; p.iterationCap = 5; p.stagnationLimit = 5; }
            Oracle oracle = new Oracle(items, start, depot, p);
            oracle.solve(); ++oracleFields;
            Butine.Plan plan = Butine.solve(items, start, depot, p);
            validate_plan(plan, p);
            check(plan.expectedCollected <= oracle.bestCollected, "heuristic cannot exceed exhaustive feasible haul");
            if (plan.expectedCollected < oracle.bestCollected) ++heuristicHaulGaps;
            if (plan.expectedCollected == oracle.bestCollected && plan.fittingRuns == 1 && size <= 5
                    && p.capacity >= size && p.computeBudgetMs > 0) {
                // With one complete short route and generous compute, the exact run permutation applies.
                near(oracle.bestObjective, plan.committedTimeS, "single exact route vs assignment oracle");
            }
        }
        List<Pollen> fixed = Arrays.asList(item(0, 8, 3), item(1, 4, 30), item(2, 31, 11),
                item(3, 44, 2), item(4, 20, 33), item(5, 3, 40));
        for (int capacity = 0; capacity <= 3; ++capacity) {
            for (int horizon = 1; horizon <= 3; ++horizon) {
                for (double remaining : new double[] {3, 30}) {
                    Butine.Params p = fast_settings();
                    p.capacity = capacity; p.lookaheadRuns = horizon; p.remainingS = remaining;
                    p.scoreOverheadS = .3; p.discount = .7;
                    p.computeBudgetMs = 1000; p.iterationCap = 4; p.stagnationLimit = 4;
                    Oracle oracle = new Oracle(fixed, DEPOT, DEPOT, p);
                    oracle.solve(); ++oracleFields;
                    Butine.Plan plan = Butine.solve(fixed, DEPOT, p);
                    validate_plan(plan, p);
                    check(plan.expectedCollected <= oracle.bestCollected, "full capacity/horizon cross-product feasible haul");
                    if (plan.expectedCollected < oracle.bestCollected) ++heuristicHaulGaps;
                }
            }
        }
        for (int size = 2; size <= 5; ++size) {
            List<Pollen> subset = fixed.subList(0, size);
            Butine.Params p = fast_settings();
            p.capacity = size; p.lookaheadRuns = 1; p.remainingS = 100;
            p.computeBudgetMs = 1000; p.iterationCap = 0;
            Oracle oracle = new Oracle(subset, DEPOT, DEPOT, p);
            oracle.solve(); ++oracleFields;
            Butine.Plan plan = Butine.solve(subset, DEPOT, p);
            check(plan.expectedCollected == size, "all short-route members fit");
            near(oracle.bestObjective, plan.committedTimeS, "exact short-route optimum vs independent exhaustive oracle");
            validate_plan(plan, p);
        }
    }

    private static void capacity_horizon_and_objective() {
        List<Pollen> items = Arrays.asList(item(1, 1, 1), item(2, 2, 1), item(3, 3, 1), item(4, 4, 1), item(5, 5, 1));
        Butine.Params p = fast_settings();
        p.capacity = 2; p.lookaheadRuns = 3; p.remainingS = 100; p.discount = .5;
        Butine.Plan plan = Butine.solve(items, DEPOT, p);
        check(plan.runCount == 3 && plan.runs[0].count == 2 && plan.runs[1].count == 2 && plan.runs[2].count == 1,
                "full early runs precede tail");
        validate_plan(plan, p);
        double objective = 0;
        for (int r = 0; r < plan.runCount; ++r) objective += Math.pow(.5, r) * (plan.runs[r].timeS + 2);
        near(objective, plan.objective, "discount applies to time and scoring only");
        p.discount = 0;
        plan = Butine.solve(items, DEPOT, p);
        check(plan.runs[0].count == 2, "zero discount does not turn off early haul priority");
        near(plan.runs[0].timeS + 2, plan.objective, "zero discount objective");
        for (int capacity : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 5, 30, Integer.MAX_VALUE}) {
            p.capacity = capacity;
            for (int horizon : new int[] {Integer.MIN_VALUE, 0, 1, 2, 3, Integer.MAX_VALUE}) {
                p.lookaheadRuns = horizon;
                plan = Butine.solve(items, DEPOT, p);
                validate_plan(plan, p);
                if (capacity <= 0) check(plan.uncapacitated && plan.runCount == 1, "unbounded ignores horizon");
            }
        }
        p.capacity = 2; p.lookaheadRuns = 3; p.scoreOverheadS = 40; p.remainingS = 50;
        check(Butine.solve(items, DEPOT, p).runCount == 1, "clock caps lookahead");
        p.scoreOverheadS = -10; p.remainingS = 100;
        plan = Butine.solve(items, DEPOT, p);
        validate_plan(plan, p);
    }

    private static void large_maps_and_budgets() {
        Random random = new Random(7766);
        List<Pollen> maximum = new ArrayList<>();
        for (int i = 0; i < 30; ++i) maximum.add(item(i, random.nextDouble() * 140, random.nextDouble() * 140));
        for (int i = 0; i < 32; ++i) {
            Butine.Params p = fast_settings();
            p.computeBudgetMs = i % 4 == 0 ? 15 : i % 4 == 1 ? .1 : i % 4 == 2 ? 0 : -1;
            p.capacity = i % 3 == 0 ? 0 : 5;
            p.remainingS = i % 2 == 0 ? 3 : 30;
            Butine.Plan plan = Butine.solve(maximum, DEPOT, p);
            validate_plan(plan, p);
            check(plan.candidatesConsidered == 30, "maximum candidate table");
            check(plan.iterations <= p.iterationCap, "iteration cap");
            if (p.computeBudgetMs <= 0) check(plan.iterations == 0 && plan.budgetExhausted, "zero optional search budget");
        }
        List<Pollen> large = new ArrayList<>();
        for (int i = 0; i < 4096; ++i) large.add(item(i, 70 + random.nextDouble() * 70, 70 + random.nextDouble() * 70));
        large.add(item(10000, 1, 0));
        Butine.Params p = fast_settings(); p.candidateCap = 1;
        Butine.Plan plan = Butine.solve(large, DEPOT, p);
        check(plan.candidatesConsidered == 1 && plan.runs[0].ids[0] == 10000, "scan entire large map with bounded retained candidates");
        p.candidateCap = Integer.MAX_VALUE;
        plan = Butine.solve(large, DEPOT, p);
        check(plan.candidatesConsidered == 30, "hard candidate ceiling");
        validate_plan(plan, p);
        p.candidateCap = 0;
        check(Butine.solve(maximum, DEPOT, p).candidatesConsidered == 1, "source minimum candidate cap retained");
    }

    private static void validate_plan(Butine.Plan plan, Butine.Params p) {
        largestObservedSolveMs = Math.max(largestObservedSolveMs, plan.solveUs / 1000);
        check(plan.runCount == plan.runs.length && plan.runCount <= 3, "plan bounded run count");
        check(plan.candidatesConsidered <= 30, "bounded candidates");
        check(Double.isFinite(plan.objective), "finite diagnostic objective");
        check(Double.isFinite(plan.solveUs) && plan.solveUs >= 0, "finite measured runtime");
        check(Double.isFinite(plan.committedTimeS) && plan.committedTimeS <= p.remainingS + 1e-8, "finite feasible committed time");
        Set<Integer> ids = new HashSet<>();
        int collected = 0, fitting = 0;
        double committed = 0;
        boolean blocked = false;
        for (int r = 0; r < plan.runCount; ++r) {
            Butine.Run run = plan.runs[r];
            check(run.count == run.ids.length && run.count == run.poses.length, "run lengths agree");
            if (p.capacity > 0) check(run.count <= Math.min(p.capacity, 30), "capacity constraint");
            for (int m = 0; m < run.count; ++m) {
                check(ids.add(run.ids[m]), "unique ids across all runs");
                check(run.poses[m].is_finite(), "finite output pose");
            }
            double independentlyCalculated = independent_run(r == 0 ? plan.start : plan.depot, plan.depot,
                    Arrays.asList(run.poses), p.kinematics);
            near(independentlyCalculated, run.timeS, "independent route model agrees with plan");
            if (run.fits) {
                check(!blocked && run.count > 0, "fitting runs are nonempty prefix");
                ++fitting; collected += run.count; committed += independentlyCalculated + Math.max(0, p.scoreOverheadS);
            } else blocked = true;
        }
        check(plan.expectedCollected == collected && plan.fittingRuns == fitting, "reported haul and fitting counts");
        near(committed, plan.committedTimeS, "independent committed-time sum");
    }
}
