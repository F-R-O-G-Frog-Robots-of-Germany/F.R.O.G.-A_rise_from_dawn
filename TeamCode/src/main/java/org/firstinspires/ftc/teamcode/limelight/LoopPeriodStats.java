package org.firstinspires.ftc.teamcode.limelight;

/**
 * Constant-memory loop-period histogram: recording allocates nothing and takes constant time.
 * Percentiles cover every recorded period since reset, rounded UP to 10 microseconds.
 * Periods above 100 ms share an overflow bucket; their percentile is reported as infinity
 * rather than inventing an upper bound. Mean, last and maximum retain their measured values.
 */
public final class LoopPeriodStats {
    private static final long BUCKET_NANOS = 10_000;
    private static final int LAST_FINITE_BUCKET = 10_000;
    private final long[] histogram = new long[LAST_FINITE_BUCKET + 2];
    private long count, lastNanos, maxNanos, over5Ms, over6Ms, overflow, previousTick;
    private double sumNanos;

    /** First tick after pause/reset establishes a boundary; it never includes setup time. */
    public void record_tick(long now) {
        if (previousTick != 0) record_nanoseconds(now - previousTick);
        previousTick = now;
    }

    /** Retain measurements, but exclude the gap before the next measured tick. */
    public void pause() { previousTick = 0; }

    public void record_nanoseconds(long nanos) {
        if (nanos <= 0) return;
        long units = (nanos - 1) / BUCKET_NANOS + 1;
        int bucket = (int) Math.min(LAST_FINITE_BUCKET + 1L, units);
        histogram[bucket]++;
        count++;
        lastNanos = nanos;
        maxNanos = Math.max(maxNanos, nanos);
        sumNanos += nanos;
        if (nanos > 5_000_000) over5Ms++;
        if (nanos > 6_000_000) over6Ms++;
        if (bucket > LAST_FINITE_BUCKET) overflow++;
    }

    public void reset() {
        java.util.Arrays.fill(histogram, 0);
        count = lastNanos = maxNanos = over5Ms = over6Ms = overflow = previousTick = 0;
        sumNanos = 0;
    }

    /** Call at telemetry cadence, not on every period: this scans the fixed histogram. */
    public Snapshot snapshot() {
        long rank95 = count - count / 20;
        long rank99 = count - count / 100;
        long accumulated = 0;
        double p95 = 0, p99 = 0;
        boolean found95 = false;
        for (int i = 1; i < histogram.length && accumulated < rank99; i++) {
            accumulated += histogram[i];
            double bound = i > LAST_FINITE_BUCKET ? Double.POSITIVE_INFINITY : i * BUCKET_NANOS / 1e6;
            if (!found95 && accumulated >= rank95) { p95 = bound; found95 = true; }
            if (accumulated >= rank99) p99 = bound;
        }
        return new Snapshot(count, lastNanos / 1e6, count == 0 ? 0 : sumNanos / count / 1e6,
                maxNanos / 1e6, p95, p99, over5Ms, over6Ms, overflow);
    }

    public static final class Snapshot {
        public final long count, over5Ms, over6Ms, overflow;
        public final double lastMs, meanMs, maxMs, p95Ms, p99Ms;

        private Snapshot(long count, double lastMs, double meanMs, double maxMs,
                         double p95Ms, double p99Ms, long over5Ms, long over6Ms, long overflow) {
            this.count = count;
            this.lastMs = lastMs;
            this.meanMs = meanMs;
            this.maxMs = maxMs;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.over5Ms = over5Ms;
            this.over6Ms = over6Ms;
            this.overflow = overflow;
        }

        /** At least ten samples in the nominal 1% tail; this is not a confidence guarantee. */
        public boolean enough_tail_samples() { return count >= 1000; }
    }
}
