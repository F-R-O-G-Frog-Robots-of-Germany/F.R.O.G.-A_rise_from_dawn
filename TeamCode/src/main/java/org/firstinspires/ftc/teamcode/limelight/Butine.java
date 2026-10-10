package org.firstinspires.ftc.teamcode.limelight;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, receding-horizon pollen planner ported from Ma Douce's butine.cpp.
 * Coordinates are real field inches and radians, in the same frame as the observations.
 * Drive only the first fitting run, score at the depot, then solve against the new map.
 * The planner models pickup as a point; intake and scoring are supplied by the runner.
 */
public final class Butine {
    public static final int MAX_CANDIDATES = 30;
    public static final int MAX_RUNS = 3;
    public static final int MAX_RUN_LEN = MAX_CANDIDATES;
    public static final int EXACT_ORDER_LIMIT = 5;
    private static final double EPSILON = 1e-9;
    private static final double DEGENERATE_LEG_IN = 1e-3;

    public Butine() { }

    /** Seed motion measurements; calibrate on this robot before relying on deadlines. */
    public static final class Kinematics {
        public double cruiseSpeedInS = 72.0;
        public double accelInS2 = 162.0;
        public double decelInS2 = 173.0;
        public double omegaMaxRadS = 4.0;
        public double turnSettleS = 0.08;
        public double turnDeadbandRad = 0.09;
        public double turnCoupling = 0.67;
        public double pickupDwellS = 0.0;

        public Kinematics() { }

        public Kinematics(Kinematics source) {
            cruiseSpeedInS = source.cruiseSpeedInS;
            accelInS2 = source.accelInS2;
            decelInS2 = source.decelInS2;
            omegaMaxRadS = source.omegaMaxRadS;
            turnSettleS = source.turnSettleS;
            turnDeadbandRad = source.turnDeadbandRad;
            turnCoupling = source.turnCoupling;
            pickupDwellS = source.pickupDwellS;
        }
    }

    public static final class Params {
        /** Zero or less selects one unbounded tour without intermediate returns. */
        public int capacity = 5;
        public int lookaheadRuns = 2;
        public double discount = 1.0;
        public double remainingS = 30.0;
        public double scoreOverheadS = 2.0;
        public int candidateCap = 25;
        /** Fast defaults; source's fuller search uses 45 ms, 20000 iterations, 40 stagnation. */
        public double computeBudgetMs = 15.0;
        public int iterationCap = 64;
        public int stagnationLimit = 8;
        /** Bounds each local search as well as the overall wall-clock budget. */
        public int localSearchPasses = 4;
        public int exactOrderLimit = EXACT_ORDER_LIMIT;
        public double minConfidence = 0.0;
        public int seed = 0x9E3779B9;
        public Kinematics kinematics = new Kinematics();

        public Params() { }

        public Params(Params source) {
            capacity = source.capacity;
            lookaheadRuns = source.lookaheadRuns;
            discount = source.discount;
            remainingS = source.remainingS;
            scoreOverheadS = source.scoreOverheadS;
            candidateCap = source.candidateCap;
            computeBudgetMs = source.computeBudgetMs;
            iterationCap = source.iterationCap;
            stagnationLimit = source.stagnationLimit;
            localSearchPasses = source.localSearchPasses;
            exactOrderLimit = source.exactOrderLimit;
            minConfidence = source.minConfidence;
            seed = source.seed;
            kinematics = new Kinematics(source.kinematics);
        }
    }

    public static final class Run {
        public final int[] ids;
        public final VisionPose[] poses;
        public final int count;
        /** Travel, pickup dwell and final heading settle; excludes scoring overhead. */
        public final double timeS;
        public final boolean fits;

        public Run(int[] ids, VisionPose[] poses, double timeS, boolean fits) {
            Objects.requireNonNull(ids, "ids");
            Objects.requireNonNull(poses, "poses");
            if (ids.length != poses.length) throw new IllegalArgumentException("Mismatched run arrays");
            if (ids.length > MAX_RUN_LEN || !Double.isFinite(timeS) || timeS < 0
                    || (fits && ids.length == 0)) throw new IllegalArgumentException("Invalid run size, time or fit");
            for (int i = 0; i < ids.length; ++i) {
                require_pose(poses[i]);
                for (int j = 0; j < i; ++j)
                    if (ids[j] == ids[i]) throw new IllegalArgumentException("Duplicate pollen id in run");
            }
            this.ids = ids.clone();
            this.poses = poses.clone();
            this.count = ids.length;
            this.timeS = timeS;
            this.fits = fits;
        }
    }

    public static final class Plan {
        public final VisionPose start;
        public final VisionPose depot;
        public final Run[] runs;
        public final int runCount;
        public final int fittingRuns;
        public final int expectedCollected;
        public final double committedTimeS;
        /** Discounted objective before the final deadline trim, as in the original. */
        public final double objective;
        public final int candidatesConsidered;
        public final double solveUs;
        public final int iterations;
        public final boolean budgetExhausted;
        public final boolean uncapacitated;

        private Plan(Context ctx, Run[] runs, int fittingRuns, int expectedCollected,
                     double committedTimeS, double objective, int iterations, boolean uncapacitated) {
            start = ctx.start;
            depot = ctx.depot;
            this.runs = runs;
            runCount = runs.length;
            this.fittingRuns = fittingRuns;
            this.expectedCollected = expectedCollected;
            this.committedTimeS = committedTimeS;
            this.objective = objective;
            candidatesConsidered = ctx.count;
            solveUs = (System.nanoTime() - ctx.startedNs) / 1000.0;
            this.iterations = iterations;
            budgetExhausted = ctx.budgetExhausted;
            this.uncapacitated = uncapacitated;
        }
    }

    /** Trapezoidal/triangular straight-line motion, at rest at both ends. */
    public static double straight_time(double distanceIn, Kinematics k) {
        if (!Double.isFinite(distanceIn)) throw new IllegalArgumentException("Distance must be finite");
        require_translation(k);
        if (!(distanceIn > EPSILON)) return 0.0;
        double cruise = Math.max(k.cruiseSpeedInS, EPSILON);
        double accel = Math.max(k.accelInS2, EPSILON);
        double decel = Math.max(k.decelInS2, EPSILON);
        // Divide before multiplying: v*v and a*d can overflow for finite settings.
        double ramp = (cruise / accel) * (cruise / 2) + (cruise / decel) * (cruise / 2);
        if (distanceIn >= ramp) return finite_time(cruise / accel + cruise / decel + (distanceIn - ramp) / cruise);
        double smaller = Math.min(accel, decel);
        double effectiveAccel = smaller / (1 + smaller / Math.max(accel, decel));
        return finite_time(Math.sqrt(distanceIn) / Math.sqrt(effectiveAccel) * Math.sqrt(2));
    }

    public static double wrap_pi(double angle) {
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("Angle must be finite");
        double wrapped = angle % (2 * Math.PI);
        if (wrapped > Math.PI) wrapped -= 2 * Math.PI;
        if (wrapped < -Math.PI) wrapped += 2 * Math.PI;
        return wrapped;
    }

    /** Turn overlaps translation; only the portion outlasting the drive adds cost. */
    public static double leg_time(double incomingHeading, VisionPose from, VisionPose to, Kinematics k) {
        return leg_time(incomingHeading, from, to, k, null);
    }

    /** Optional outHeading[0] receives the travel direction, or incoming heading for a zero leg. */
    public static double leg_time(double incomingHeading, VisionPose from, VisionPose to,
                                  Kinematics k, double[] outHeading) {
        require_pose(from);
        require_pose(to);
        validate_kinematics(k);
        if (!Double.isFinite(incomingHeading)) throw new IllegalArgumentException("Incoming heading must be finite");
        if (outHeading != null && outHeading.length == 0) throw new IllegalArgumentException("Heading output must contain one slot");
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double distance = Math.hypot(dx, dy);
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("Coordinate difference exceeds numeric range");
        double direction = distance < DEGENERATE_LEG_IN ? incomingHeading : Math.atan2(dy, dx);
        if (outHeading != null) outHeading[0] = direction;
        if (distance < DEGENERATE_LEG_IN) return 0.0;
        return coupled_time(straight_time(distance, k), direction, incomingHeading, k);
    }

    public static double run_time(VisionPose depot, List<VisionPose> poses, Kinematics k) {
        return run_time(depot, depot, poses.toArray(new VisionPose[0]), poses.size(), k);
    }

    public static double run_time(VisionPose depot, VisionPose[] poses, int count, Kinematics k) {
        return run_time(depot, depot, poses, count, k);
    }

    /** Actual-start version; all later runs should still start at the depot. */
    public static double run_time(VisionPose start, VisionPose depot, VisionPose[] poses,
                                 int count, Kinematics k) {
        require_pose(start);
        require_pose(depot);
        Objects.requireNonNull(poses, "poses");
        validate_kinematics(k);
        if (count < 0) throw new IllegalArgumentException("Run count must be nonnegative");
        if (count <= 0) return 0.0;
        if (count > poses.length) throw new IllegalArgumentException("Run count exceeds poses");
        double heading = start.heading;
        double total = 0;
        VisionPose previous = start;
        for (int i = 0; i < count; ++i) {
            total += leg_time(heading, previous, poses[i], k) + k.pickupDwellS;
            heading = travel_heading(heading, previous, poses[i]);
            previous = poses[i];
        }
        total += leg_time(heading, previous, depot, k);
        heading = travel_heading(heading, previous, depot);
        return finite_time(total + settle_time(angle_difference(depot.heading, heading), k));
    }

    public static Plan solve(List<Pollen> pollen, VisionPose depot, Params params) {
        return solve(pollen, depot, depot, params);
    }

    /** Pure planning: no motors, path builder, alliance mirroring or scoring side effects. */
    public static Plan solve(List<Pollen> pollen, VisionPose start, VisionPose depot, Params params) {
        Objects.requireNonNull(pollen, "pollen");
        Objects.requireNonNull(params, "params");
        require_pose(start);
        require_pose(depot);
        Params settings = new Params(params);
        validate(settings);
        Context ctx = new Context(start, depot, settings);
        boolean uncapacitated = settings.capacity <= 0;
        select_candidates(pollen, ctx);
        if (ctx.count == 0 || settings.remainingS <= 0) {
            return new Plan(ctx, new Run[0], 0, 0, 0, 0, 0, uncapacitated);
        }
        ctx.build_tables();
        int capacity = uncapacitated ? ctx.count : clamp(settings.capacity, 1, MAX_RUN_LEN);
        double overhead = Math.max(0, settings.scoreOverheadS);
        double[] gamma = {1, clamp(settings.discount, 0, 1), 0};
        gamma[2] = gamma[1] * gamma[1];
        int horizon = 1;
        if (!uncapacitated) {
            int byCandidates = (ctx.count + capacity - 1) / capacity;
            int byClock = overhead > EPSILON ? (int) Math.min(MAX_RUNS, settings.remainingS / overhead) : MAX_RUNS;
            horizon = Math.max(1, Math.min(clamp(settings.lookaheadRuns, 1, MAX_RUNS),
                    Math.min(byCandidates, Math.max(1, byClock))));
        }
        int[] tour = new int[ctx.count];
        build_giant_tour(ctx, tour);
        double travelBudget = Math.max(0, settings.remainingS - overhead);
        // Produce a bounded usable answer before spending time on optional search.
        Solution best = uncapacitated
                ? build_uncapacitated(tour, ctx, travelBudget, gamma, overhead)
                : greedy_split(tour, ctx, capacity, horizon, gamma, overhead);
        int[] bestTour = tour.clone();
        if (ctx.may_search()) {
            optimize_sequence(tour, tour.length, ctx, true);
            Solution candidate = construct(tour, ctx, capacity, horizon, gamma, overhead, travelBudget, uncapacitated);
            if (better(candidate, best, uncapacitated)) {
                best = candidate;
                System.arraycopy(tour, 0, bestTour, 0, tour.length);
            }
        }
        Rng rng = new Rng(settings.seed);
        int iterations = 0;
        int stagnation = 0;
        while (iterations < Math.max(0, settings.iterationCap)
                && stagnation < Math.max(1, settings.stagnationLimit) && ctx.may_search()) {
            ++iterations;
            System.arraycopy(bestTour, 0, tour, 0, tour.length);
            double_bridge(tour, rng);
            optimize_sequence(tour, tour.length, ctx, true);
            Solution candidate = construct(tour, ctx, capacity, horizon, gamma, overhead, travelBudget, uncapacitated);
            if (better(candidate, best, uncapacitated)) {
                best = candidate;
                System.arraycopy(tour, 0, bestTour, 0, tour.length);
                stagnation = 0;
            } else ++stagnation;
        }
        return fit_deadline(best, ctx, overhead, iterations, uncapacitated);
    }

    private static double travel_heading(double heading, VisionPose from, VisionPose to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        return Math.hypot(dx, dy) < DEGENERATE_LEG_IN ? heading : Math.atan2(dy, dx);
    }

    private static double coupled_time(double straight, double direction, double incoming, Kinematics k) {
        double swing = Math.abs(angle_difference(direction, incoming));
        if (swing > k.turnDeadbandRad && k.omegaMaxRadS > EPSILON) {
            double rotate = swing / k.omegaMaxRadS + k.turnSettleS;
            straight += k.turnCoupling * Math.max(0, rotate - straight);
        }
        return finite_time(straight);
    }

    private static double settle_time(double angle, Kinematics k) {
        double swing = Math.abs(wrap_pi(angle));
        return finite_time(swing > k.turnDeadbandRad && k.omegaMaxRadS > EPSILON
                ? swing / k.omegaMaxRadS + k.turnSettleS : 0);
    }

    private static double angle_difference(double target, double current) {
        return wrap_pi(wrap_pi(target) - wrap_pi(current));
    }

    private static double finite_time(double seconds) {
        if (!Double.isFinite(seconds)) throw new IllegalArgumentException("Motion time exceeds numeric range");
        return seconds;
    }

    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private static double clamp(double n, double min, double max) { return Math.max(min, Math.min(max, n)); }

    private static void require_pose(VisionPose pose) {
        Objects.requireNonNull(pose, "pose");
        if (!Double.isFinite(pose.x) || !Double.isFinite(pose.y) || !Double.isFinite(pose.heading))
            throw new IllegalArgumentException("Pose must be finite");
    }

    private static void validate(Params p) {
        double[] finite = {p.discount, p.remainingS, p.scoreOverheadS, p.computeBudgetMs, p.minConfidence};
        for (double value : finite) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite planner setting");
        validate_kinematics(p.kinematics);
    }

    private static void require_translation(Kinematics k) {
        Objects.requireNonNull(k, "kinematics");
        if (!Double.isFinite(k.cruiseSpeedInS) || !Double.isFinite(k.accelInS2)
                || !Double.isFinite(k.decelInS2) || k.cruiseSpeedInS <= 0
                || k.accelInS2 <= 0 || k.decelInS2 <= 0)
            throw new IllegalArgumentException("Invalid translation kinematics");
    }

    private static void validate_kinematics(Kinematics k) {
        require_translation(k);
        if (!Double.isFinite(k.omegaMaxRadS) || !Double.isFinite(k.turnSettleS)
                || !Double.isFinite(k.turnDeadbandRad) || !Double.isFinite(k.turnCoupling)
                || !Double.isFinite(k.pickupDwellS)
                || k.omegaMaxRadS < 0 || k.turnSettleS < 0 || k.turnDeadbandRad < 0
                || k.turnCoupling < 0 || k.turnCoupling > 1 || k.pickupDwellS < 0)
            throw new IllegalArgumentException("Invalid kinematics");
    }

    private static final class Context {
        final VisionPose start;
        final VisionPose depot;
        final Params params;
        final Kinematics k;
        final long startedNs = System.nanoTime();
        final long budgetNs;
        final VisionPose[] poses = new VisionPose[MAX_CANDIDATES];
        final int[] ids = new int[MAX_CANDIDATES];
        final double[] reach = new double[MAX_CANDIDATES];
        final double[][] straight = new double[MAX_CANDIDATES + 2][MAX_CANDIDATES + 2];
        final double[][] direction = new double[MAX_CANDIDATES + 2][MAX_CANDIDATES + 2];
        final boolean[][] degenerate = new boolean[MAX_CANDIDATES + 2][MAX_CANDIDATES + 2];
        int count;
        boolean budgetExhausted;

        Context(VisionPose start, VisionPose depot, Params params) {
            this.start = start;
            this.depot = depot;
            this.params = params;
            k = params.kinematics;
            budgetNs = (long) Math.min(Long.MAX_VALUE, Math.max(0, params.computeBudgetMs) * 1e6);
        }

        boolean may_search() {
            if (System.nanoTime() - startedNs < budgetNs) return true;
            budgetExhausted = true;
            return false;
        }

        VisionPose node(int index) { return index == 0 ? depot : index == count + 1 ? start : poses[index - 1]; }

        void build_tables() {
            for (int a = 0; a < count + 2; ++a) {
                for (int b = 0; b < count + 2; ++b) {
                    VisionPose from = node(a);
                    VisionPose to = node(b);
                    double dx = to.x - from.x;
                    double dy = to.y - from.y;
                    double distance = Math.hypot(dx, dy);
                    degenerate[a][b] = distance < DEGENERATE_LEG_IN;
                    straight[a][b] = degenerate[a][b] ? 0 : straight_time(distance, k);
                    direction[a][b] = degenerate[a][b] ? 0 : Math.atan2(dy, dx);
                }
            }
        }
    }

    /** Bounded insertion selection scans the entire map instead of only its first 64 entries. */
    private static void select_candidates(List<Pollen> pollen, Context ctx) {
        int wanted = clamp(ctx.params.candidateCap, 1, MAX_CANDIDATES);
        for (Pollen item : pollen) {
            if (item == null || item.position == null || !Double.isFinite(item.confidence)
                    || item.confidence < ctx.params.minConfidence
                    || !Double.isFinite(item.position.x) || !Double.isFinite(item.position.y)
                    || !Double.isFinite(item.position.heading)) continue;
            boolean duplicate = false;
            for (int i = 0; i < ctx.count; ++i) if (ctx.ids[i] == item.id) { duplicate = true; break; }
            if (duplicate) continue;
            double seconds = straight_time(Math.hypot(item.position.x - ctx.start.x, item.position.y - ctx.start.y), ctx.k);
            int position = 0;
            while (position < ctx.count && ctx.reach[position] <= seconds) ++position;
            if (position >= wanted) continue;
            int last = Math.min(ctx.count, wanted - 1);
            for (int i = last; i > position; --i) {
                ctx.ids[i] = ctx.ids[i - 1];
                ctx.poses[i] = ctx.poses[i - 1];
                ctx.reach[i] = ctx.reach[i - 1];
            }
            ctx.ids[position] = item.id;
            ctx.poses[position] = item.position;
            ctx.reach[position] = seconds;
            ctx.count = Math.min(wanted, ctx.count + 1);
        }
    }

    private static double sequence_time(int[] members, int length, Context ctx, boolean first) {
        if (length <= 0) return 0;
        double total = 0;
        double heading = first ? ctx.start.heading : ctx.depot.heading;
        int previous = first ? ctx.count + 1 : 0;
        for (int m = 0; m < length; ++m) {
            int node = members[m] + 1;
            if (!ctx.degenerate[previous][node]) {
                total += coupled_time(ctx.straight[previous][node], ctx.direction[previous][node], heading, ctx.k);
                heading = ctx.direction[previous][node];
            }
            total += ctx.k.pickupDwellS;
            previous = node;
        }
        if (!ctx.degenerate[previous][0]) {
            total += coupled_time(ctx.straight[previous][0], ctx.direction[previous][0], heading, ctx.k);
            heading = ctx.direction[previous][0];
        }
        return finite_time(total + settle_time(angle_difference(ctx.depot.heading, heading), ctx.k));
    }

    private static void reverse(int[] values, int from, int to) {
        while (from < to) {
            int value = values[from]; values[from++] = values[to]; values[to--] = value;
        }
    }

    /** Exact short-run permutations, with current order retained when the budget expires. */
    private static double optimize_run(int[] members, int length, Context ctx, boolean first) {
        if (length <= 1) return sequence_time(members, length, ctx, first);
        int exactLimit = clamp(ctx.params.exactOrderLimit, 0, EXACT_ORDER_LIMIT);
        if (length > exactLimit) return optimize_sequence(members, length, ctx, first);
        double best = sequence_time(members, length, ctx, first);
        if (!ctx.may_search()) return best;
        int[] trial = Arrays.copyOf(members, length);
        int[] bestOrder = Arrays.copyOf(members, length);
        Arrays.sort(trial);
        do {
            double cost = sequence_time(trial, length, ctx, first);
            if (cost < best - EPSILON) {
                best = cost;
                System.arraycopy(trial, 0, bestOrder, 0, length);
            }
            if (!ctx.may_search()) break;
        } while (next_permutation(trial));
        System.arraycopy(bestOrder, 0, members, 0, length);
        return best;
    }

    private static boolean next_permutation(int[] values) {
        int a = values.length - 2;
        while (a >= 0 && values[a] >= values[a + 1]) --a;
        if (a < 0) return false;
        int b = values.length - 1;
        while (values[b] <= values[a]) --b;
        int value = values[a]; values[a] = values[b]; values[b] = value;
        reverse(values, a + 1, values.length - 1);
        return true;
    }

    /** 2-opt and Or-opt (1..3 members, either orientation) with a bounded pass count. */
    private static double optimize_sequence(int[] members, int length, Context ctx, boolean first) {
        double best = sequence_time(members, length, ctx, first);
        if (length < 3 || !ctx.may_search()) return best;
        int[] trial = new int[length];
        int[] remainder = new int[length];
        for (int pass = 0; pass < Math.max(0, ctx.params.localSearchPasses); ++pass) {
            boolean improved = false;
            for (int a = 0; a < length - 1; ++a) {
                for (int b = a + 1; b < length; ++b) {
                    if (!ctx.may_search()) return best;
                    reverse(members, a, b);
                    double cost = sequence_time(members, length, ctx, first);
                    if (cost < best - EPSILON) { best = cost; improved = true; }
                    else reverse(members, a, b);
                }
            }
            for (int span = 1; span <= 3 && span < length; ++span) {
                for (int start = 0; start + span <= length; ++start) {
                    for (int flip = 0; flip <= 1; ++flip) {
                        for (int insert = 0; insert + span <= length; ++insert) {
                            if (!ctx.may_search()) return best;
                            if (insert == start && flip == 0) continue;
                            int write = 0;
                            for (int m = 0; m < length; ++m)
                                if (m < start || m >= start + span) remainder[write++] = members[m];
                            for (int m = 0; m < insert; ++m) trial[m] = remainder[m];
                            for (int m = 0; m < span; ++m)
                                trial[insert + m] = members[start + (flip == 0 ? m : span - 1 - m)];
                            for (int m = insert; m < length - span; ++m) trial[m + span] = remainder[m];
                            double cost = sequence_time(trial, length, ctx, first);
                            if (cost < best - EPSILON) {
                                best = cost;
                                improved = true;
                                System.arraycopy(trial, 0, members, 0, length);
                            }
                        }
                    }
                }
            }
            if (!improved) break;
        }
        return best;
    }

    private static final class Solution {
        final int[][] members = new int[MAX_RUNS][MAX_RUN_LEN];
        final int[] length = new int[MAX_RUNS];
        final double[] seconds = new double[MAX_RUNS];
        int count;
        int horizon;
        int collected;
        double frontLoad;
        double objective;
        boolean valid;
    }

    private static double front_load_weight(int run, int horizon, int capacity) {
        double weight = 1;
        for (int i = run + 1; i < horizon; ++i) weight *= Math.max(capacity, 1) + 1;
        return weight;
    }

    private static void score_solution(Solution s, double[] gamma, double overhead, int capacity, int horizon) {
        s.horizon = horizon;
        s.collected = 0; s.frontLoad = 0; s.objective = 0;
        for (int r = 0; r < s.count; ++r) {
            s.collected += s.length[r];
            s.frontLoad += front_load_weight(r, horizon, capacity) * s.length[r];
            s.objective += gamma[r] * (s.seconds[r] + overhead);
        }
        finite_time(s.objective);
        s.valid = s.count > 0;
    }

    private static boolean better_value(int collected, double frontLoad, double cost,
                                         int oldCollected, double oldFrontLoad, double oldCost) {
        if (collected != oldCollected) return collected > oldCollected;
        if (Math.abs(frontLoad - oldFrontLoad) > EPSILON) return frontLoad > oldFrontLoad;
        return cost < oldCost - EPSILON;
    }

    private static boolean better(Solution candidate, Solution old, boolean uncapacitated) {
        if (!old.valid) return candidate.valid;
        if (!candidate.valid) return false;
        if (uncapacitated) return candidate.length[0] > old.length[0]
                || (candidate.length[0] == old.length[0] && candidate.seconds[0] < old.seconds[0] - EPSILON);
        return better_value(candidate.collected, candidate.frontLoad, candidate.objective,
                old.collected, old.frontLoad, old.objective);
    }

    private static void build_giant_tour(Context ctx, int[] tour) {
        boolean[] visited = new boolean[ctx.count];
        int previous = ctx.count + 1;
        double heading = ctx.start.heading;
        for (int step = 0; step < ctx.count; ++step) {
            int best = -1;
            double cheapest = Double.POSITIVE_INFINITY;
            for (int c = 0; c < ctx.count; ++c) {
                if (visited[c]) continue;
                double cost = ctx.degenerate[previous][c + 1] ? 0 : coupled_time(
                        ctx.straight[previous][c + 1], ctx.direction[previous][c + 1], heading, ctx.k);
                if (cost < cheapest) { cheapest = cost; best = c; }
            }
            tour[step] = best;
            visited[best] = true;
            if (!ctx.degenerate[previous][best + 1]) heading = ctx.direction[previous][best + 1];
            previous = best + 1;
        }
    }

    private static final class Rng {
        int state;
        Rng(int seed) { state = seed == 0 ? 0x9E3779B9 : seed; }
        int below(int bound) {
            if (bound <= 0) return 0;
            state ^= state << 13; state ^= state >>> 17; state ^= state << 5;
            return (int) (Integer.toUnsignedLong(state) % bound);
        }
    }

    private static void double_bridge(int[] tour, Rng rng) {
        int length = tour.length;
        if (length < 8) {
            if (length >= 2) {
                int a = rng.below(length), b = rng.below(length);
                int value = tour[a]; tour[a] = tour[b]; tour[b] = value;
            }
            return;
        }
        int a = 1 + rng.below(length - 3);
        int b = a + 1 + rng.below(length - a - 2);
        int c = b + 1 + rng.below(length - b - 1);
        int[] original = tour.clone();
        int write = 0;
        for (int i = 0; i < a; ++i) tour[write++] = original[i];
        for (int i = b; i < c; ++i) tour[write++] = original[i];
        for (int i = a; i < b; ++i) tour[write++] = original[i];
        for (int i = c; i < length; ++i) tour[write++] = original[i];
    }

    private static Solution greedy_split(int[] tour, Context ctx, int capacity, int horizon,
                                           double[] gamma, double overhead) {
        Solution s = new Solution();
        int cursor = 0;
        while (s.count < horizon && cursor < tour.length) {
            int r = s.count++;
            int length = Math.min(capacity, tour.length - cursor);
            System.arraycopy(tour, cursor, s.members[r], 0, length);
            cursor += length;
            s.length[r] = length;
            s.seconds[r] = optimize_run(s.members[r], length, ctx, r == 0);
        }
        score_solution(s, gamma, overhead, capacity, horizon);
        return s;
    }

    /** Dynamic-programming split of a giant tour; individual candidates may be skipped. */
    private static Solution split_tour(int[] tour, Context ctx, int capacity, int horizon,
                                      double[] gamma, double overhead) {
        Solution fallback = greedy_split(tour, ctx, capacity, horizon, gamma, overhead);
        int[][] collected = new int[ctx.count + 1][horizon + 1];
        double[][] front = new double[ctx.count + 1][horizon + 1];
        double[][] cost = new double[ctx.count + 1][horizon + 1];
        int[][] backPosition = new int[ctx.count + 1][horizon + 1];
        int[][] backLength = new int[ctx.count + 1][horizon + 1];
        for (int j = 0; j <= ctx.count; ++j) {
            Arrays.fill(collected[j], -1);
            Arrays.fill(cost[j], Double.POSITIVE_INFINITY);
            Arrays.fill(backPosition[j], -1);
        }
        collected[0][0] = 0; cost[0][0] = 0;
        int[] scratch = new int[MAX_RUN_LEN];
        for (int j = 0; j < ctx.count; ++j) {
            for (int r = 0; r <= horizon; ++r) {
                if (collected[j][r] < 0) continue;
                if (!ctx.may_search()) return fallback;
                if (better_value(collected[j][r], front[j][r], cost[j][r],
                        collected[j + 1][r], front[j + 1][r], cost[j + 1][r])) {
                    collected[j + 1][r] = collected[j][r];
                    front[j + 1][r] = front[j][r]; cost[j + 1][r] = cost[j][r];
                    backPosition[j + 1][r] = j; backLength[j + 1][r] = 0;
                }
                if (r == horizon) continue;
                for (int span = 1; span <= Math.min(capacity, ctx.count - j); ++span) {
                    System.arraycopy(tour, j, scratch, 0, span);
                    double seconds = optimize_run(scratch, span, ctx, r == 0);
                    int haul = collected[j][r] + span;
                    double load = front[j][r] + front_load_weight(r, horizon, capacity) * span;
                    double value = cost[j][r] + gamma[r] * (seconds + overhead);
                    if (better_value(haul, load, value, collected[j + span][r + 1],
                            front[j + span][r + 1], cost[j + span][r + 1])) {
                        collected[j + span][r + 1] = haul;
                        front[j + span][r + 1] = load; cost[j + span][r + 1] = value;
                        backPosition[j + span][r + 1] = j; backLength[j + span][r + 1] = span;
                    }
                }
            }
        }
        int chosenPosition = -1, chosenRuns = -1, chosenCollected = -1;
        double chosenFront = 0, chosenCost = Double.POSITIVE_INFINITY;
        for (int r = 1; r <= horizon; ++r) {
            for (int j = 0; j <= ctx.count; ++j) {
                if (collected[j][r] >= 0 && better_value(collected[j][r], front[j][r], cost[j][r],
                        chosenCollected, chosenFront, chosenCost)) {
                    chosenPosition = j; chosenRuns = r; chosenCollected = collected[j][r];
                    chosenFront = front[j][r]; chosenCost = cost[j][r];
                }
            }
        }
        if (chosenRuns < 0) return fallback;
        Solution s = new Solution();
        int j = chosenPosition, r = chosenRuns;
        while (r > 0 && j >= 0) {
            int from = backPosition[j][r], length = backLength[j][r];
            if (from < 0) break;
            if (length > 0) {
                int slot = r - 1;
                System.arraycopy(tour, from, s.members[slot], 0, length);
                s.length[slot] = length; --r;
            }
            j = from;
        }
        s.count = chosenRuns;
        for (r = 0; r < s.count; ++r) s.seconds[r] = optimize_run(s.members[r], s.length[r], ctx, r == 0);
        score_solution(s, gamma, overhead, capacity, horizon);
        return better(s, fallback, false) ? s : fallback;
    }

    private static Solution construct(int[] tour, Context ctx, int capacity, int horizon,
                                      double[] gamma, double overhead, double budget, boolean uncapacitated) {
        if (uncapacitated) return build_uncapacitated(tour, ctx, budget, gamma, overhead);
        Solution s = split_tour(tour, ctx, capacity, horizon, gamma, overhead);
        if (s.valid && ctx.may_search()) polish_solution(s, ctx, capacity, gamma);
        return s;
    }

    /** Cross-run relocate, exchange, and exchange with candidates skipped by the split. */
    private static void polish_solution(Solution s, Context ctx, int capacity, double[] gamma) {
        int[] trialA = new int[MAX_RUN_LEN], trialB = new int[MAX_RUN_LEN];
        boolean[] used = new boolean[ctx.count];
        for (int r = 0; r < s.count; ++r)
            for (int m = 0; m < s.length[r]; ++m) used[s.members[r][m]] = true;
        int[] spare = new int[ctx.count];
        int spareCount = 0;
        for (int c = 0; c < ctx.count; ++c) if (!used[c]) spare[spareCount++] = c;
        for (int pass = 0; pass < Math.max(0, ctx.params.localSearchPasses); ++pass) {
            boolean improved = false;
            for (int a = 0; a < s.count; ++a) {
                for (int b = 0; b < s.count; ++b) {
                    if (a == b || s.length[b] >= capacity || s.length[a] <= 1) continue;
                    double frontDelta = front_load_weight(b, s.horizon, capacity) - front_load_weight(a, s.horizon, capacity);
                    if (frontDelta < -EPSILON) continue;
                    for (int i = 0; i < s.length[a]; ++i) {
                        if (!ctx.may_search()) return;
                        int write = 0;
                        for (int m = 0; m < s.length[a]; ++m) if (m != i) trialA[write++] = s.members[a][m];
                        System.arraycopy(s.members[b], 0, trialB, 0, s.length[b]);
                        trialB[s.length[b]] = s.members[a][i];
                        double timeA = optimize_run(trialA, write, ctx, a == 0);
                        double timeB = optimize_run(trialB, s.length[b] + 1, ctx, b == 0);
                        double delta = gamma[a] * (timeA - s.seconds[a]) + gamma[b] * (timeB - s.seconds[b]);
                        if (frontDelta > EPSILON || delta < -EPSILON) {
                            s.length[a] = write; ++s.length[b];
                            s.seconds[a] = timeA; s.seconds[b] = timeB;
                            System.arraycopy(trialA, 0, s.members[a], 0, s.length[a]);
                            System.arraycopy(trialB, 0, s.members[b], 0, s.length[b]);
                            s.objective += delta; s.frontLoad += frontDelta; improved = true;
                            break;
                        }
                    }
                }
            }
            for (int a = 0; a < s.count; ++a) {
                for (int b = a + 1; b < s.count; ++b) {
                    for (int i = 0; i < s.length[a]; ++i) {
                        for (int j = 0; j < s.length[b]; ++j) {
                            if (!ctx.may_search()) return;
                            System.arraycopy(s.members[a], 0, trialA, 0, s.length[a]);
                            System.arraycopy(s.members[b], 0, trialB, 0, s.length[b]);
                            int value = trialA[i]; trialA[i] = trialB[j]; trialB[j] = value;
                            double timeA = optimize_run(trialA, s.length[a], ctx, a == 0);
                            double timeB = optimize_run(trialB, s.length[b], ctx, b == 0);
                            double delta = gamma[a] * (timeA - s.seconds[a]) + gamma[b] * (timeB - s.seconds[b]);
                            if (delta < -EPSILON) {
                                s.seconds[a] = timeA; s.seconds[b] = timeB;
                                System.arraycopy(trialA, 0, s.members[a], 0, s.length[a]);
                                System.arraycopy(trialB, 0, s.members[b], 0, s.length[b]);
                                s.objective += delta; improved = true;
                            }
                        }
                    }
                }
                for (int i = 0; i < s.length[a]; ++i) {
                    for (int u = 0; u < spareCount; ++u) {
                        if (!ctx.may_search()) return;
                        System.arraycopy(s.members[a], 0, trialA, 0, s.length[a]);
                        trialA[i] = spare[u];
                        double seconds = optimize_run(trialA, s.length[a], ctx, a == 0);
                        double delta = gamma[a] * (seconds - s.seconds[a]);
                        if (delta < -EPSILON) {
                            spare[u] = s.members[a][i];
                            s.seconds[a] = seconds;
                            System.arraycopy(trialA, 0, s.members[a], 0, s.length[a]);
                            s.objective += delta; improved = true;
                            break;
                        }
                    }
                }
            }
            if (!improved) break;
        }
    }

    private static double detour_cost(int[] members, int length, int position, Context ctx, boolean first) {
        int[] scratch = new int[length - 1];
        System.arraycopy(members, 0, scratch, 0, position);
        System.arraycopy(members, position + 1, scratch, position, length - position - 1);
        return sequence_time(members, length, ctx, first) - sequence_time(scratch, scratch.length, ctx, first);
    }

    private static int worst_member(int[] members, int length, Context ctx, boolean first) {
        int worst = 0;
        double bestSaving = Double.NEGATIVE_INFINITY;
        for (int m = 0; m < length; ++m) {
            double saving = detour_cost(members, length, m, ctx, first);
            if (saving > bestSaving) { bestSaving = saving; worst = m; }
        }
        return worst;
    }

    private static void remove_at(int[] members, int length, int position) {
        System.arraycopy(members, position + 1, members, position, length - position - 1);
    }

    /** One tour: trim expensive detours to the clock, then reinsert anything slack permits. */
    private static Solution build_uncapacitated(int[] tour, Context ctx, double budget,
                                               double[] gamma, double overhead) {
        Solution s = new Solution();
        s.count = 1;
        int length = ctx.count;
        System.arraycopy(tour, 0, s.members[0], 0, length);
        boolean[] dropped = new boolean[ctx.count];
        double seconds = optimize_sequence(s.members[0], length, ctx, true);
        while (length > 0 && seconds > budget + EPSILON) {
            int worst = worst_member(s.members[0], length, ctx, true);
            dropped[s.members[0][worst]] = true;
            remove_at(s.members[0], length, worst);
            seconds = optimize_sequence(s.members[0], --length, ctx, true);
        }
        int[] trial = new int[MAX_RUN_LEN];
        boolean added = true;
        while (added && length < MAX_RUN_LEN && ctx.may_search()) {
            added = false;
            for (int c = 0; c < ctx.count && ctx.may_search(); ++c) {
                if (!dropped[c]) continue;
                System.arraycopy(s.members[0], 0, trial, 0, length);
                trial[length] = c;
                double candidate = optimize_sequence(trial, length + 1, ctx, true);
                if (candidate <= budget + EPSILON) {
                    System.arraycopy(trial, 0, s.members[0], 0, ++length);
                    seconds = candidate; dropped[c] = false; added = true;
                    break;
                }
            }
        }
        s.length[0] = length;
        s.seconds[0] = seconds;
        score_solution(s, gamma, overhead, ctx.count, 1);
        return s;
    }

    /** Deadline fitting is mandatory and bounded even when optional search has exhausted its budget. */
    private static Plan fit_deadline(Solution best, Context ctx, double overhead, int iterations, boolean uncapacitated) {
        if (!best.valid) return new Plan(ctx, new Run[0], 0, 0, 0, 0, iterations, uncapacitated);
        double objective = best.objective;
        Run[] runs = new Run[best.count];
        double committed = 0;
        int fittingRuns = 0, collected = 0;
        boolean blocked = false;
        for (int r = 0; r < best.count; ++r) {
            if (!blocked) {
                while (best.length[r] > 0 && committed + best.seconds[r] + overhead > ctx.params.remainingS + EPSILON) {
                    int worst = worst_member(best.members[r], best.length[r], ctx, r == 0);
                    remove_at(best.members[r], best.length[r], worst);
                    --best.length[r];
                    best.seconds[r] = optimize_run(best.members[r], best.length[r], ctx, r == 0);
                }
            }
            int length = best.length[r];
            int[] ids = new int[length];
            VisionPose[] poses = new VisionPose[length];
            for (int m = 0; m < length; ++m) {
                int candidate = best.members[r][m];
                ids[m] = ctx.ids[candidate]; poses[m] = ctx.poses[candidate];
            }
            boolean fits = !blocked && length > 0
                    && committed + best.seconds[r] + overhead <= ctx.params.remainingS + EPSILON;
            runs[r] = new Run(ids, poses, best.seconds[r], fits);
            if (fits) { committed += best.seconds[r] + overhead; collected += length; ++fittingRuns; }
            else blocked = true;
        }
        return new Plan(ctx, runs, fittingRuns, collected, committed, objective, iterations, uncapacitated);
    }
}
