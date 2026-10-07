package org.firstinspires.ftc.teamcode.core.control;

/** Work time excludes telemetry; cycle time includes everything between consecutive starts. */
public final class LoopTiming {
    private long previousStart;
    private long currentStart;
    private double cycleMs;
    private double workMs;

    public void begin() {
        currentStart = System.nanoTime();
        cycleMs = previousStart == 0 ? 0 : (currentStart - previousStart) / 1e6;
        previousStart = currentStart;
    }

    public void end_work() { workMs = (System.nanoTime() - currentStart) / 1e6; }
    public double cycle_ms() { return cycleMs; }
    public double work_ms() { return workMs; }
    public double hz() { return cycleMs > 0 ? 1000.0 / cycleMs : 0; }
}
