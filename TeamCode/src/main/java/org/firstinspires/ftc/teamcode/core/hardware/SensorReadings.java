package org.firstinspires.ftc.teamcode.core.hardware;

/** Constant-time numeric validation; callers still choose how to handle invalid readings. */
public final class SensorReadings {
    private SensorReadings() { }

    public static boolean is_valid(double value) {
        return Double.isFinite(value);
    }
}
