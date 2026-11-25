package org.firstinspires.ftc.teamcode.core.control;

/** Work time excludes telemetry; cycle time includes everything between consecutive starts. */
public final class LoopTiming {
    public static final int TELEMETRY_INTERVAL_MS = 100;
    private static final long TELEMETRY_INTERVAL_NANOS = TELEMETRY_INTERVAL_MS * 1_000_000L;

    private long previousStart;
    private long currentStart;
    private double cycleMs;
    private double workMs;
    private long lastTelemetryNanos;
    private boolean telemetryPublished;

    public void begin() {
        currentStart = System.nanoTime();
        cycleMs = previousStart == 0 ? 0 : (currentStart - previousStart) / 1e6;
        previousStart = currentStart;
    }

    public long end_work() {
        long now = System.nanoTime();
        workMs = (now - currentStart) / 1e6;
        return now;
    }

    /** Reserve a telemetry update at most once per interval, starting with the first sample. */
    public boolean telemetry_due(long now) {
        if (telemetryPublished && now - lastTelemetryNanos < TELEMETRY_INTERVAL_NANOS) return false;
        lastTelemetryNanos = now;
        telemetryPublished = true;
        return true;
    }

    public double cycle_ms() { return cycleMs; }
    public double work_ms() { return workMs; }
    public double hz() { return cycleMs > 0 ? 1000.0 / cycleMs : 0; }
}
