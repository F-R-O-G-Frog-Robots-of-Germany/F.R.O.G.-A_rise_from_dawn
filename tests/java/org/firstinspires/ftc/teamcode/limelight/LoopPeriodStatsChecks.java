package org.firstinspires.ftc.teamcode.limelight;

/** Known synthetic distributions, including tail stalls; no robot or wall-clock sleeps. */
public final class LoopPeriodStatsChecks {
    private static int checks;

    public static void main(String[] args) {
        LoopPeriodStats stats = new LoopPeriodStats();
        stats.record_nanoseconds(0);
        stats.record_nanoseconds(-1);
        check(stats.snapshot().count == 0, "nonpositive periods excluded");
        check(stats.snapshot().p95Ms == 0 && stats.snapshot().p99Ms == 0, "empty percentiles");
        for (int i = 1; i <= 1000; i++) stats.record_nanoseconds(i * 10_000L);
        LoopPeriodStats.Snapshot distribution = stats.snapshot();
        check(distribution.count == 1000 && distribution.enough_tail_samples(), "tail sample threshold");
        near(distribution.p95Ms, 9.5, "nearest-rank p95");
        near(distribution.p99Ms, 9.9, "nearest-rank p99");
        near(distribution.meanMs, 5.005, "exact mean, not bucket approximation");
        near(distribution.lastMs, 10, "last period");
        near(distribution.maxMs, 10, "max period");
        check(distribution.over5Ms == 500 && distribution.over6Ms == 400, "strict threshold counts");
        check(distribution.overflow == 0, "no overflow");
        stats.reset();
        check(stats.snapshot().count == 0 && stats.snapshot().meanMs == 0, "reset clears totals");
        check(stats.snapshot().maxMs == 0 && stats.snapshot().over5Ms == 0, "reset clears max and deadlines");
        stats.record_nanoseconds(4_000_001);
        LoopPeriodStats.Snapshot rounded = stats.snapshot();
        near(rounded.p95Ms, 4.01, "percentile rounded upward by less than 10 us");
        near(rounded.meanMs, 4.000001, "mean retains sub-bucket detail");
        check(!rounded.enough_tail_samples(), "small sample marked provisional");
        stats.reset();
        // A sparse large stall affects max/mean, but does not falsely become the p99.
        for (int i = 0; i < 999; i++) stats.record_nanoseconds(4_000_000);
        stats.record_nanoseconds(2_000_000_000L);
        LoopPeriodStats.Snapshot sparse = stats.snapshot();
        near(sparse.p99Ms, 4, "one-in-1000 stall below nearest-rank p99");
        near(sparse.maxMs, 2000, "overflow retains exact maximum");
        check(sparse.overflow == 1 && sparse.over6Ms == 1, "overflow is also a deadline miss");
        stats.reset();
        for (int i = 0; i < 98; i++) stats.record_nanoseconds(100_000_000);
        stats.record_nanoseconds(100_000_001);
        stats.record_nanoseconds(Long.MAX_VALUE);
        LoopPeriodStats.Snapshot overflow = stats.snapshot();
        near(overflow.p95Ms, 100, "100 ms upper edge remains finite");
        check(Double.isInfinite(overflow.p99Ms), "overflow percentile never reports a fabricated bound");
        check(overflow.overflow == 2 && Double.isFinite(overflow.meanMs), "huge intervals safely counted");
        stats.reset();
        for (int i = 0; i < 100_000; i++) stats.record_nanoseconds(6_000_000);
        near(stats.snapshot().p95Ms, 6, "bounded histogram handles long runs");
        check(stats.snapshot().over5Ms == 100_000 && stats.snapshot().over6Ms == 0, "threshold equality");
        check(distribution.count == 1000 && distribution.p99Ms == 9.9, "published snapshots immutable after reset");
        stats.reset();
        stats.record_tick(1_000_000_000L);
        stats.record_tick(1_004_000_000L);
        stats.pause();
        stats.record_tick(20_000_000_000L);
        stats.record_tick(20_006_000_000L);
        check(stats.snapshot().count == 2, "first ticks exclude startup and transition gaps");
        near(stats.snapshot().meanMs, 5, "only complete measured periods retained across pause");
        near(stats.snapshot().maxMs, 6, "large transition gap cannot become a max or tail stall");
        LoopPeriodStats otherMode = new LoopPeriodStats();
        otherMode.record_tick(30_000_000_000L);
        otherMode.record_tick(30_009_000_000L);
        near(otherMode.snapshot().meanMs, 9, "separate mode has its own sample distribution");
        check(stats.snapshot().count == 2 && stats.snapshot().maxMs == 6, "new mode leaves saved comparison intact");
        otherMode.reset();
        otherMode.record_tick(90_000_000_000L);
        check(otherMode.snapshot().count == 0, "new visit/reset discards prior timing boundary");
        System.out.println("LoopPeriodStatsChecks: " + checks + " checks passed");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual);
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
