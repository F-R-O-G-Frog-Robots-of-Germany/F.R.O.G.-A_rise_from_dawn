package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Standalone, hardware-free regression: compile alongside Butine, Pollen and VisionPose. */
public final class ButineRegression {
    private static int assertions;

    public static void main(String[] args) {
        test_motion_model();
        test_exact_short_run();
        test_filtering_and_deadlines();
        test_live_start();
        test_reproducibility();
        test_generated_fields();
        System.out.println("Butine regression passed: " + assertions + " assertions");
    }

    private static Pollen pollen(int id, double x, double y) {
        return new Pollen(id, new VisionPose(x, y, 0), 1.0, 10.0, 0L);
    }

    private static void check(boolean condition, String message) {
        ++assertions;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1e-7, message + ": " + expected + " vs " + actual);
    }

    private static void test_motion_model() {
        Butine.Kinematics k = new Butine.Kinematics();
        k.cruiseSpeedInS = 2; k.accelInS2 = 2; k.decelInS2 = 2;
        k.omegaMaxRadS = 0;
        near(0, Butine.straight_time(0, k), "zero leg");
        near(Math.sqrt(2), Butine.straight_time(1, k), "triangular motion");
        near(2, Butine.straight_time(2, k), "ramp boundary");
        near(4, Butine.straight_time(6, k), "cruise motion");
        VisionPose origin = new VisionPose(0, 0, 0);
        double[] heading = new double[1];
        near(0, Butine.leg_time(1.23, origin, origin, k, heading), "coincident leg cost");
        near(1.23, heading[0], "coincident leg heading");
        near(8, Butine.run_time(origin, new VisionPose[] {
                new VisionPose(2, 0, 0), new VisionPose(2, 2, 0), new VisionPose(0, 2, 0)
        }, 3, k), "square closed route");
        near(Math.PI / 2, Butine.wrap_pi(5 * Math.PI / 2), "angle wrapping");
        k.omegaMaxRadS = 1; k.turnCoupling = 1; k.turnSettleS = 0;
        near(Math.PI, Butine.leg_time(Math.PI, origin, new VisionPose(1, 0, 0), k), "turn binding");
        k.turnCoupling = 0;
        near(Math.sqrt(2), Butine.leg_time(Math.PI, origin, new VisionPose(1, 0, 0), k), "uncoupled turn");
    }

    private static void test_exact_short_run() {
        Butine.Params p = new Butine.Params();
        p.capacity = 3; p.lookaheadRuns = 1; p.computeBudgetMs = 1000; p.iterationCap = 0;
        VisionPose depot = new VisionPose(0, 0, 0);
        List<Pollen> items = Arrays.asList(pollen(1, 34, 3), pollen(2, -12, 29), pollen(3, 7, -22));
        Butine.Plan plan = Butine.solve(items, depot, p);
        check(plan.fittingRuns == 1 && plan.runs[0].count == 3, "exact run collection");
        double optimum = Double.POSITIVE_INFINITY;
        for (int a = 0; a < 3; ++a) for (int b = 0; b < 3; ++b) for (int c = 0; c < 3; ++c) {
            if (a == b || b == c || a == c) continue;
            optimum = Math.min(optimum, Butine.run_time(depot, new VisionPose[] {
                    items.get(a).position, items.get(b).position, items.get(c).position
            }, 3, p.kinematics));
        }
        near(optimum, plan.runs[0].timeS, "exact permutation optimum");
        validate_plan(plan, p);
    }

    private static void test_filtering_and_deadlines() {
        Butine.Params p = new Butine.Params();
        p.computeBudgetMs = 0; p.minConfidence = .5;
        VisionPose depot = new VisionPose(0, 0, 0);
        check(Butine.solve(Collections.<Pollen>emptyList(), depot, p).runCount == 0, "empty map");
        List<Pollen> items = new ArrayList<>();
        items.add(pollen(1, 12, 0));
        items.add(pollen(1, 12, 0));
        items.add(new Pollen(2, new VisionPose(1, 0, 0), .1, 1, 0L));
        items.add(new Pollen(3, new VisionPose(Double.NaN, 0, 0), 1, 1, 0L));
        items.add(new Pollen(4, new VisionPose(1, 0, 0), Double.NaN, 1, 0L));
        Butine.Plan plan = Butine.solve(items, depot, p);
        check(plan.candidatesConsidered == 1, "filter confidence, coordinates and duplicate ids");
        check(plan.expectedCollected == 1, "filtered map collection");
        p.remainingS = .1;
        plan = Butine.solve(items, depot, p);
        check(plan.fittingRuns == 0 && plan.expectedCollected == 0, "no scoring time available");
        p.remainingS = 0;
        check(Butine.solve(items, depot, p).runCount == 0, "deadline expired");
        p.remainingS = 4; p.scoreOverheadS = .2; p.capacity = 0;
        items.clear();
        items.add(pollen(1, 3, 0)); items.add(pollen(2, 6, 0)); items.add(pollen(3, 140, 140));
        plan = Butine.solve(items, depot, p);
        check(plan.uncapacitated && plan.runCount <= 1, "unbounded single tour");
        check(plan.expectedCollected >= 1 && plan.expectedCollected < 3, "unbounded deadline trim");
        validate_plan(plan, p);
        p.capacity = 3; p.candidateCap = 2; p.remainingS = 30;
        items.clear();
        for (int i = 0; i < 70; ++i) items.add(pollen(i, 100 + i, 100));
        items.add(pollen(100, 1, 0)); items.add(pollen(101, 2, 0));
        plan = Butine.solve(items, depot, p);
        check(plan.candidatesConsidered == 2, "candidate cap");
        Set<Integer> ids = new HashSet<>();
        for (int id : plan.runs[0].ids) ids.add(id);
        check(ids.contains(100) && ids.contains(101), "scan map beyond original 64 entry ceiling");
    }

    private static void test_live_start() {
        Butine.Params p = new Butine.Params();
        p.computeBudgetMs = 0; p.capacity = 2; p.lookaheadRuns = 2;
        p.remainingS = 100;
        VisionPose start = new VisionPose(65, 45, .7), depot = new VisionPose(5, 8, -1.2);
        List<Pollen> items = Arrays.asList(pollen(1, 70, 45), pollen(2, 66, 60), pollen(3, 10, 16));
        Butine.Plan plan = Butine.solve(items, start, depot, p);
        check(plan.start == start && plan.depot == depot, "retain live start and scoring depot");
        check(plan.fittingRuns == 2, "live start lookahead");
        validate_plan(plan, p);
        Butine.Params copy = new Butine.Params(p);
        copy.kinematics.cruiseSpeedInS = 10;
        near(72, p.kinematics.cruiseSpeedInS, "independent settings copy");
    }

    private static void test_reproducibility() {
        Butine.Params p = new Butine.Params();
        p.computeBudgetMs = 1000; p.iterationCap = 8; p.stagnationLimit = 8;
        p.localSearchPasses = 2; p.capacity = 3; p.lookaheadRuns = 3; p.remainingS = 100;
        Random random = new Random(70);
        List<Pollen> items = new ArrayList<>();
        for (int i = 0; i < 10; ++i) items.add(pollen(i, random.nextDouble() * 140, random.nextDouble() * 140));
        VisionPose depot = new VisionPose(50, 50, 0);
        Butine.Plan a = Butine.solve(items, depot, p), b = Butine.solve(items, depot, p);
        check(a.runCount == b.runCount, "seed run count reproducibility");
        for (int r = 0; r < a.runCount; ++r) check(Arrays.equals(a.runs[r].ids, b.runs[r].ids), "seed order reproducibility");
        validate_plan(a, p); validate_plan(b, p);
    }

    private static void test_generated_fields() {
        Random random = new Random(1537);
        for (int field = 0; field < 80; ++field) {
            Butine.Params p = new Butine.Params();
            p.computeBudgetMs = field % 4 == 0 ? 10 : 0;
            p.capacity = field % 3 == 0 ? 0 : 2 + field % 4;
            p.lookaheadRuns = 1 + field % 3; p.remainingS = field % 5 == 0 ? 2.5 : 8 + field % 30;
            p.candidateCap = 30;
            VisionPose depot = new VisionPose(20, 20, 1);
            VisionPose start = new VisionPose(random.nextDouble() * 100, random.nextDouble() * 100, -.5);
            List<Pollen> items = new ArrayList<>();
            for (int i = 0; i < 1 + field % 30; ++i)
                items.add(pollen(i, random.nextDouble() * 140, random.nextDouble() * 140));
            validate_plan(Butine.solve(items, start, depot, p), p);
        }
    }

    private static void validate_plan(Butine.Plan plan, Butine.Params p) {
        check(plan.runCount <= Butine.MAX_RUNS, "bounded run count");
        check(Double.isFinite(plan.committedTimeS) && plan.committedTimeS <= p.remainingS + 1e-7, "deadline respected");
        check(plan.solveUs >= 0 && Double.isFinite(plan.solveUs), "measured solve time");
        Set<Integer> ids = new HashSet<>();
        int collected = 0, fitting = 0;
        double committed = 0;
        boolean passedNonfitting = false;
        for (int r = 0; r < plan.runCount; ++r) {
            Butine.Run run = plan.runs[r];
            check(run.count == run.ids.length && run.count == run.poses.length, "run arrays agree");
            if (p.capacity > 0) check(run.count <= p.capacity, "run capacity respected");
            for (int id : run.ids) check(ids.add(id), "no pollen planned twice");
            near(Butine.run_time(r == 0 ? plan.start : plan.depot, plan.depot,
                    run.poses, run.count, p.kinematics), run.timeS, "table and public costs agree");
            if (run.fits) {
                check(!passedNonfitting, "fitting runs form a prefix");
                ++fitting; collected += run.count; committed += run.timeS + Math.max(0, p.scoreOverheadS);
            } else passedNonfitting = true;
        }
        check(plan.fittingRuns == fitting, "fitting count agrees");
        check(plan.expectedCollected == collected, "committed collection agrees");
        near(committed, plan.committedTimeS, "committed time agrees");
    }
}
