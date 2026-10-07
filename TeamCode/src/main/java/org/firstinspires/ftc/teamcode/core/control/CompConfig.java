package org.firstinspires.ftc.teamcode.core.control;

/** Shared competition settings for autonomous and teleop OpModes. */
public final class CompConfig {
    private CompConfig() {
    }

    /** Disable Panels, video, and diagnostic telemetry for competition runs. */
    public static final boolean COMP_MODE = true;

    /** Show loop time on the Driver Station even when competition mode is on. */
    public static final boolean LOOP_TIME_TELEMETRY = true;

    /** Allow INIT selection telemetry in competition mode; false suppresses that exception. */
    public static final boolean INIT_TELEMETRY = true;

    public static boolean panels_enabled() {
        return !COMP_MODE;
    }

    public static boolean telemetry_enabled() {
        return !COMP_MODE;
    }

    public static boolean video_enabled() {
        return !COMP_MODE;
    }

    public static boolean loop_time_telemetry_enabled() {
        return !COMP_MODE || LOOP_TIME_TELEMETRY;
    }

    public static boolean init_telemetry_enabled() {
        return !COMP_MODE || INIT_TELEMETRY;
    }
}
