package org.firstinspires.ftc.teamcode.limelight;

import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional Linux affinity for the calling Java thread, using Android's bundled Toybox.
 * Call once at worker startup, then restore in its finally block. A core is zero-based;
 * -1 keeps Android's scheduler. Affinity restricts eligible CPUs; it does not reserve a CPU.
 * Failed/unsupported commands leave a diagnostic result, so workers can still run normally.
 */
public final class ThreadAffinity {
    private static final long COMMAND_TIMEOUT_NS = 750_000_000L;
    private static final int MAX_OUTPUT_BYTES = 8192;
    private static final Pattern MASK_PATTERN = Pattern.compile("affinity mask:\\s*([0-9a-fA-F]+)");

    private ThreadAffinity() { }

    public static final class Result {
        public final boolean applied;
        public final int tid;
        public final int core;
        public final String detail;
        private final String originalMask;
        private final boolean restoreNeeded;

        private Result(boolean applied, int tid, int core, String detail,
                       String originalMask, boolean restoreNeeded) {
            this.applied = applied;
            this.tid = tid;
            this.core = core;
            this.detail = detail;
            this.originalMask = originalMask;
            this.restoreNeeded = restoreNeeded;
        }
    }

    /** Uses the Linux TID from inside this thread, never java.lang.Thread.getId(). */
    public static Result bind_current_thread(int core) {
        int tid;
        try {
            tid = Process.myTid();
        } catch (RuntimeException | LinkageError e) {
            return new Result(false, -1, core, "Kernel TID unavailable: " + e, null, false);
        }
        return bind_thread(core, tid, ThreadAffinity::run_taskset);
    }

    /** Restores only from the original thread; preserves an existing interrupt for shutdown. */
    public static boolean restore_current_thread(Result result) {
        if (result == null || !result.restoreNeeded) return true;
        try {
            if (Process.myTid() != result.tid) return false;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        return restore_thread(result, ThreadAffinity::run_taskset);
    }

    interface CommandRunner {
        CommandResult run(String... args);
    }

    static final class CommandResult {
        final boolean success;
        final String output;
        CommandResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    static Result bind_thread(int core, int tid, CommandRunner commands) {
        if (core == -1) return new Result(false, tid, core, "Android scheduler (no affinity request)", null, false);
        if (core < 0 || core > 1023 || tid <= 0) {
            return new Result(false, tid, core, "Invalid core/TID; expected core -1 or 0..1023", null, false);
        }
        CommandResult before = commands.run("-p", Integer.toString(tid));
        String original = before.success ? parse_mask(before.output) : null;
        if (original == null) {
            return new Result(false, tid, core, "Cannot read original affinity: " + before.output, null, false);
        }
        String requested = BigInteger.ONE.shiftLeft(core).toString(16);
        CommandResult set = commands.run("-p", requested, Integer.toString(tid));
        if (!set.success) {
            // A timed-out command may have changed affinity before its output was available.
            return new Result(false, tid, core, "Affinity request failed: " + set.output, original, true);
        }
        CommandResult after = commands.run("-p", Integer.toString(tid));
        String actual = after.success ? parse_mask(after.output) : null;
        boolean applied = requested.equals(actual);
        return new Result(applied, tid, core,
                applied ? "Verified CPU " + core + " (mask " + actual + ")"
                        : "Affinity not verified; requested " + requested + ", read " + actual + ": " + after.output,
                original, true);
    }

    static boolean restore_thread(Result result, CommandRunner commands) {
        if (result == null || !result.restoreNeeded) return true;
        boolean interrupted = Thread.interrupted();
        try {
            CommandResult set = commands.run("-p", result.originalMask, Integer.toString(result.tid));
            if (!set.success) return false;
            CommandResult after = commands.run("-p", Integer.toString(result.tid));
            return after.success && result.originalMask.equals(parse_mask(after.output));
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static String parse_mask(String output) {
        Matcher matcher = MASK_PATTERN.matcher(output == null ? "" : output);
        String mask = null;
        while (matcher.find()) mask = matcher.group(1);
        if (mask == null) return null;
        BigInteger bits = new BigInteger(mask, 16);
        return bits.signum() == 0 ? null : bits.toString(16);
    }

    private static CommandResult run_taskset(String... args) {
        String[] command = new String[args.length + 2];
        command[0] = "/system/bin/toybox";
        command[1] = "taskset";
        System.arraycopy(args, 0, command, 2, args.length);
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            InputStream output = process.getInputStream();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            long started = System.nanoTime();
            while (true) {
                // Drain as we wait so the child cannot block on a full output pipe.
                while (output.available() > 0) {
                    int count = output.read(buffer, 0, Math.min(buffer.length, output.available()));
                    if (count < 0) break;
                    if (captured.size() + count > MAX_OUTPUT_BYTES) {
                        return new CommandResult(false, "taskset output exceeded " + MAX_OUTPUT_BYTES + " bytes");
                    }
                    captured.write(buffer, 0, count);
                }
                try {
                    int exit = process.exitValue();
                    // After exit, read remaining buffered bytes without waiting for another process.
                    while (output.available() > 0 && captured.size() < MAX_OUTPUT_BYTES) {
                        int count = output.read(buffer, 0,
                                Math.min(buffer.length, Math.min(output.available(), MAX_OUTPUT_BYTES - captured.size())));
                        if (count < 0) break;
                        captured.write(buffer, 0, count);
                    }
                    return new CommandResult(exit == 0, captured.toString("UTF-8").trim());
                } catch (IllegalThreadStateException stillRunning) {
                    if (System.nanoTime() - started >= COMMAND_TIMEOUT_NS) {
                        return new CommandResult(false, "taskset timed out");
                    }
                    Thread.sleep(5);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, "taskset interrupted");
        } catch (Exception e) {
            return new CommandResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
                try { process.getInputStream().close(); } catch (Exception ignored) { }
                try { process.getOutputStream().close(); } catch (Exception ignored) { }
                try { process.getErrorStream().close(); } catch (Exception ignored) { }
            }
        }
    }
}
