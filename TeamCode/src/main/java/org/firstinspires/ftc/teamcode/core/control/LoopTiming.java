package org.firstinspires.ftc.teamcode.core.control;

/** Work time excludes telemetry; cycle time includes everything between consecutive starts. */
public final class LoopTiming {
    /** Interval configured for FTC telemetry transmission. */
    public static final int TELEMETRY_INTERVAL_MS = 100;
    private static final int TELEMETRY_INTERVAL_LOOPS = 20;

    private long previousStart;
    private long currentStart;
    private int completedLoopsInTelemetryInterval;
    private boolean telemetryDue;
    private double cycleMs;
    private double workMs;

    public void begin() {
        currentStart = System.nanoTime();
        cycleMs = previousStart == 0 ? 0 : (currentStart - previousStart) / 1e6;
        previousStart = currentStart;
    }

    public void end_work() {
        workMs = (System.nanoTime() - currentStart) / 1e6;
        completedLoopsInTelemetryInterval++;
        telemetryDue = completedLoopsInTelemetryInterval == TELEMETRY_INTERVAL_LOOPS;
        if (telemetryDue) completedLoopsInTelemetryInterval = 0;
    }

    /** Consume the publication slot offered after every twentieth completed loop. */
    public boolean should_publish_telemetry() {
        if (!telemetryDue) return false;
        telemetryDue = false;
        return true;
    }

    public double cycle_ms() { return cycleMs; }
    public double work_ms() { return workMs; }
    public double hz() { return cycleMs > 0 ? 1000.0 / cycleMs : 0; }
}
