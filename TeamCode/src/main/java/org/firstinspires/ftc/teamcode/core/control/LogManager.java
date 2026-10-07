package org.firstinspires.ftc.teamcode.core.control;

import static android.util.Log.INFO;

import android.util.Log;

import java.util.function.Supplier;

public final class LogManager {
    private static final int DEFAULT_LOG_TYPE = INFO;
    private static final String DEFAULT_LOG_TAG = "DEFAULTLOG";

    public static boolean enabled = true;

    private LogManager() {
        // utility class, no instances
    }

    // --- Cheap / simple messages: use these for the vast majority of logs ---

    public static void log(String msg) {
        log(DEFAULT_LOG_TYPE, DEFAULT_LOG_TAG, msg);
    }

    public static void log(String tag, String msg) {
        log(DEFAULT_LOG_TYPE, tag, msg);
    }

    public static void log(int logType, String tag, String msg) {
        if (enabled) {
            Log.println(logType, tag, msg);
        }
    }

    // --- Expensive-to-build messages: use these only when construction cost matters ---

    public static void log(String tag, Supplier<String> msgSupplier) {
        log(DEFAULT_LOG_TYPE, tag, msgSupplier);
    }

    public static void log(int logType, String tag, Supplier<String> msgSupplier) {
        if (enabled) {
            Log.println(logType, tag, msgSupplier.get());
        }
    }
}
/*
 * Centralized logging utility for teamcode — routes all logs through Android's
 * Log.println() with a shared default tag/priority, so behavior (on/off, format,
 * backend) can be changed in one place without touching call sites.
 *
 * HOW TO LOG:
 *
 *  - Default (use this ~95% of the time) — plain string, default tag "Madlen", INFO priority:
 *      LogManager.log("Motor power: " + leftPower + ", " + rightPower);
 *
 *  - Custom tag, default priority:
 *      LogManager.log("AUTON", "Heading corrected to " + heading);
 *
 *  - Custom tag + priority (e.g. warnings/errors):
 *      LogManager.log(Log.WARN, "SENSOR", "IMU reading out of range: " + rawValue);
 *
 *  - Expensive-to-build messages only (object serialization, large telemetry dumps,
 *    non-trivial toString() calls) — message is built lazily and skipped entirely
 *    when logging is disabled:
 *      LogManager.log("TELEMETRY", () -> buildFullStateDump(robotState));
 *    Don't use this for simple concatenations — the lambda allocation costs more
 *    than the string-building it's meant to avoid.
 *
 *  - Disable all logging globally (e.g. before a competition run) by setting:
 *      LogManager.enabled = false;
 *    This skips the actual Log.println() write everywhere; plain-string overloads
 *    still build their message beforehand (cheap), Supplier-based calls don't.
 */
