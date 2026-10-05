package org.firstinspires.ftc.teamcode.core.control;

/** Shared competition settings for autonomous and teleop OpModes. */
public final class CompConfig {
    private CompConfig() {
    }

    /** Disable Panels, video, and diagnostic telemetry for competition runs. */
    public static final boolean compMode = true;

    /** Show loop time on the Driver Station even when competition mode is on. */
    public static final boolean loopTimeTelemetry = false;

    public static boolean panelsEnabled() {
        return !compMode;
    }

    public static boolean telemetryEnabled() {
        return !compMode;
    }

    public static boolean videoEnabled() {
        return !compMode;
    }

    public static boolean loopTimeTelemetryEnabled() {
        return !compMode || loopTimeTelemetry;
    }
}
