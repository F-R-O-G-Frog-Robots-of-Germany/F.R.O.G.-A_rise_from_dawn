package org.firstinspires.ftc.teamcode.core.control;

/** Work time excludes telemetry; cycle time includes everything between consecutive starts. */
public final class LoopTiming {
    public static final int TELEMETRY_INTERVAL_MS = 100;
    private static final long TELEMETRY_INTERVAL_NANOS = TELEMETRY_INTERVAL_MS * 1_000_000L;

    private long previousStart;
    private long currentStart;
    private long workEnd;
    private long lastTelemetry;
    private boolean telemetryPublished;
    private double cycleMs;
    private double workMs;

    public void begin() {
        currentStart = System.nanoTime();
        cycleMs = previousStart == 0 ? 0 : (currentStart - previousStart) / 1e6;
        previousStart = currentStart;
    }

    public void end_work() {
        workEnd = System.nanoTime();
        workMs = (workEnd - currentStart) / 1e6;
    }

    /** Consume a publication slot after end_work(), measured from the previous slot. */
    public boolean should_publish_telemetry() {
        if (telemetryPublished && workEnd - lastTelemetry < TELEMETRY_INTERVAL_NANOS) return false;
        lastTelemetry = workEnd;
        telemetryPublished = true;
        return true;
    }

    public double cycle_ms() { return cycleMs; }
    public double work_ms() { return workMs; }
    public double hz() { return cycleMs > 0 ? 1000.0 / cycleMs : 0; }
}
