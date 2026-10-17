package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Isolated P1-P10 comparison. No robot subsystem, follower, or background sampler is started.
 * D-pad selects/edits settings; X baseline; Y optimized; A runs; B ends a run and calculates.
 * START retries the last CSV export. Both bumpers must stay held for any live actuation.
 * Statistics, result formatting, and files are processed only AFTER recording and motor stop.
 * Default outputs are simulated: select live POWER/VELOCITY/hood to measure real SDK writes.
 */
@TeleOp(name = "Performance comparison", group = "Test")
public final class PerformanceComparison extends LinearOpMode {
    private static final int SAMPLE_CAPACITY = 65_536;
    private static final int SETTING_COUNT = 19;
    private static final int[] DURATIONS_S = {5, 15, 30, 60};
    private static final int[] WARMUPS_S = {1, 3, 5};
    private static final int[] REPEATS = {1, 3, 5};
    private static final long[] RANGE_PERIODS = {0, 100_000_000L, 300_000_000L};
    private static final long[] TELEMETRY_PERIODS = {-1, 0, 100_000_000L, 200_000_000L};
    private static final long ORDER_SEED = 20261016L;

    enum WritePolicy { RAW, EXACT_CHANGE, EPSILON }
    enum OutputMode { SHADOW, POWER, VELOCITY }
    enum CommandProfile { CONSTANT, SMALL_CHANGES, STEPS }
    enum PoseProfile { STATIONARY, HEADING_ONLY, MOVING }
    enum ShotMode { ALLOCATE, REUSE, CACHE_UNCHANGED }
    enum Suite { CUSTOM, BASELINE_AND_ALL, SINGLE_CHANGES, ALL_MINUS_ONE }

    static final class Config {
        LynxModule.BulkCachingMode bulkMode = LynxModule.BulkCachingMode.MANUAL;
        WritePolicy powerPolicy = WritePolicy.EPSILON, servoPolicy = WritePolicy.EPSILON;
        boolean shareReads = true, staggerRanges = true, reuseTarget = true, cacheVelocity = true;
        boolean servoLive, stageTimings = true;
        long rangeIntervalNanos = 300_000_000L, telemetryPeriodNanos = 100_000_000L;
        OutputMode outputMode = OutputMode.SHADOW;
        CommandProfile commandProfile = CommandProfile.CONSTANT;
        PoseProfile poseProfile = PoseProfile.STATIONARY;
        ShotMode shotMode = ShotMode.REUSE;

        Config copy() {
            Config c = new Config();
            c.bulkMode = bulkMode; c.powerPolicy = powerPolicy; c.servoPolicy = servoPolicy;
            c.shareReads = shareReads; c.staggerRanges = staggerRanges; c.reuseTarget = reuseTarget;
            c.cacheVelocity = cacheVelocity; c.servoLive = servoLive; c.stageTimings = stageTimings;
            c.rangeIntervalNanos = rangeIntervalNanos; c.telemetryPeriodNanos = telemetryPeriodNanos;
            c.outputMode = outputMode; c.commandProfile = commandProfile;
            c.poseProfile = poseProfile; c.shotMode = shotMode;
            return c;
        }

        boolean live() { return outputMode != OutputMode.SHADOW || servoLive; }

        String metadata() {
            return "bulk_mode=" + bulkMode + "\npower_policy=" + powerPolicy + "\nservo_policy=" + servoPolicy
                    + "\nshare_reads=" + shareReads + "\nrange_interval_ns=" + rangeIntervalNanos
                    + "\nstagger_ranges=" + staggerRanges + "\ntelemetry_period_ns=" + telemetryPeriodNanos
                    + "\nreuse_target=" + reuseTarget + "\nshot_mode=" + shotMode
                    + "\ncache_velocity=" + cacheVelocity + "\noutput_mode=" + outputMode
                    + "\nservo_live=" + servoLive + "\ncommand_profile=" + commandProfile
                    + "\npose_profile=" + poseProfile + "\nstage_timings=" + stageTimings;
        }
    }

    static final class Trial {
        final String label, reference;
        final Config config;
        final int repeat;
        Trial(String label, String reference, Config config, int repeat) {
            this.label = label; this.reference = reference; this.config = config.copy(); this.repeat = repeat;
        }
    }

    private final Config draft = new Config();
    private final Recorder recorder = new Recorder(SAMPLE_CAPACITY);
    private Suite suite = Suite.CUSTOM;
    private int selected, durationIndex = 1, warmupIndex = 1, repeatsIndex;
    private Workload workload;
    private File sessionDirectory;
    private String status = "Configure, then A to run. Outputs start in SHADOW simulation.";
    private String phase = "WARMUP", lastMetadata, lastRunId, lastExportError = "";
    private Summary lastSummary;
    private Trial lastTrial;
    private double lastHz, lastElapsedS;
    private boolean lastSummaryWritten;
    private int sequence;
    private long nextWorkloadTelemetry;

    @Override public void runOpMode() throws InterruptedException {
        try {
            workload = new Workload(hardwareMap);
            sessionDirectory = new File(new File(AppUtil.FIRST_FOLDER, "performance-comparison"),
                    Long.toString(System.currentTimeMillis()));
            // Fixed across P7 variants: only construction cadence changes, not this SDK send limit.
            telemetry.setMsTransmissionInterval(100);
            int previousButtons = 0;
            long nextScreen = 0;
            while (!isStopRequested()) {
                int buttons = buttons();
                int pressed = buttons & ~previousButtons;
                previousButtons = buttons;
                if ((pressed & 1) != 0) selected = wrap(selected - 1, SETTING_COUNT);
                if ((pressed & 2) != 0) selected = wrap(selected + 1, SETTING_COUNT);
                if ((pressed & 4) != 0) edit(-1);
                if ((pressed & 8) != 0) edit(1);
                if ((pressed & 16) != 0) apply_baseline(draft);
                if ((pressed & 32) != 0) apply_optimized(draft);
                if ((pressed & 64) != 0 && isStarted()) {
                    if (!lastExportError.isEmpty()) status = "START retries the pending export before another run.";
                    else if (draft.live() && !armed()) status = "Hold BOTH bumpers to begin live outputs; keep holding.";
                    else {
                        run_suite();
                        previousButtons = buttons();
                    }
                }
                if ((pressed & 128) != 0 && lastSummary != null) export_last();
                long now = System.nanoTime();
                if (now >= nextScreen) {
                    nextScreen = now + 200_000_000L;
                    show_menu();
                }
                idle();
            }
        } finally {
            if (workload != null) workload.restore();
        }
    }

    private void run_suite() {
        List<Trial> trials = build_trials(draft, suite, REPEATS[repeatsIndex]);
        int order = 0;
        for (Trial trial : trials) {
            if (isStopRequested()) break;
            status = "Trial " + (++order) + "/" + trials.size() + " " + trial.label;
            telemetry.addLine(status);
            telemetry.addLine("B ends recording and calculates. Live output: keep BOTH bumpers held.");
            telemetry.update();
            try {
                boolean aborted = run_trial(trial, order);
                if (aborted || !lastExportError.isEmpty()) break;
            } catch (RuntimeException error) {
                status = "Trial failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
                break;
            }
        }
    }

    /** Capture previous work at the NEXT loop start, pairing it with its own full cycle. */
    private boolean run_trial(Trial trial, int order) {
        Config c = trial.config;
        // The shared raw buffer is about to change; never export it with an older run's metadata.
        lastSummary = null;
        lastMetadata = null;
        recorder.reset();
        String endReason = "DURATION";
        long firstStart = 0, pairedEnd = 0;
        boolean aborted = false;
        long requestedNanos = DURATIONS_S[durationIndex] * 1_000_000_000L;
        long warmupNanos = WARMUPS_S[warmupIndex] * 1_000_000_000L;
        try {
            workload.prepare(c);
            phase = "WARMUP";
            long trialStart = System.nanoTime();
            nextWorkloadTelemetry = trialStart;
            while (opModeIsActive()) {
                long now = System.nanoTime();
                if (cancelled(c)) { aborted = true; endReason = "WARMUP_CANCELLED"; break; }
                if (now - trialStart >= warmupNanos) break;
                workload.read(now);
                workload.compute(now - trialStart);
                workload.write(now - trialStart, !c.live() || armed());
                workload_telemetry(c, now);
            }
            workload.reset_counters();
            phase = "RECORDING";
            long previousStart = 0, pendingWork = 0, pendingRead = 0, pendingCompute = 0;
            long pendingWrite = 0, pendingTelemetry = 0;
            while (!aborted && opModeIsActive()) {
                long start = System.nanoTime();
                if (previousStart != 0) {
                    recorder.record(start - previousStart, pendingWork, pendingRead,
                            pendingCompute, pendingWrite, pendingTelemetry);
                    pairedEnd = start;
                } else firstStart = start;
                if (recorder.full) { endReason = "BUFFER_FULL"; break; }
                if (cancelled(c)) { aborted = true; endReason = "OPERATOR_CANCELLED"; break; }
                if (start - firstStart >= requestedNanos) break;
                previousStart = start;
                long workStart = System.nanoTime();
                workload.read(workStart);
                long readEnd = c.stageTimings ? System.nanoTime() : 0;
                workload.compute(start - trialStart);
                long computeEnd = c.stageTimings ? System.nanoTime() : 0;
                workload.write(start - trialStart, !c.live() || armed());
                long workEnd = System.nanoTime();
                boolean sent = workload_telemetry(c, workEnd);
                long telemetryEnd = c.stageTimings && sent ? System.nanoTime() : 0;
                pendingWork = workEnd - workStart;
                pendingRead = c.stageTimings ? readEnd - workStart : 0;
                pendingCompute = c.stageTimings ? computeEnd - readEnd : 0;
                pendingWrite = c.stageTimings ? workEnd - computeEnd : 0;
                pendingTelemetry = telemetryEnd == 0 ? 0 : telemetryEnd - workEnd;
            }
            if (isStopRequested()) { endReason = "STOP_REQUESTED"; aborted = true; }
        } finally {
            // Stop motors before sorting/formatting/file I/O. All cleanup is outside recorded cycles.
            workload.stop();
        }
        // DS STOP prioritizes prompt cleanup. Use B to end, calculate, and save a partial recording.
        if (isStopRequested()) return true;
        lastElapsedS = pairedEnd > firstStart && firstStart != 0 ? (pairedEnd - firstStart) / 1e9 : 0;
        lastHz = lastElapsedS > 0 ? recorder.count / lastElapsedS : Double.NaN;
        lastTrial = trial;
        lastRunId = String.format(Locale.US, "%03d_%s_r%d", ++sequence, trial.label, trial.repeat);
        lastSummaryWritten = false;
        lastSummary = recorder.summarize();
        lastMetadata = "run_id=" + lastRunId + "\nreference=" + trial.reference + "\nrepeat=" + trial.repeat
                + "\norder=" + order + "\norder_seed=" + ORDER_SEED + "\nsuite=" + suite
                + "\nend_reason=" + endReason + "\nrequested_duration_ns=" + requestedNanos
                + "\nwarmup_ns=" + warmupNanos + "\ncaptured_elapsed_s=" + lastElapsedS
                + "\nactual_hz=" + lastHz + "\nsdk_telemetry_send_interval_ms=100"
                + "\nstatistics_during_recording=false\nrecording_arrays=6_long_arrays_plus_sort_scratch"
                + "\ncycle_definition=start_to_next_start_including_recording_guards_sdk_idle_and_telemetry"
                + "\nwork_excludes=recording_guards_idle_and_telemetry"
                + "\nstages_zero_when_disabled=true\nlast_unpaired_work_on_stop_excluded=true"
                + "\ntarget_pose_source=SYNTHETIC_NOT_ODOMETRY\napi_calls_are_not_hub_packet_counts=true\n"
                + c.metadata() + "\nhardware=" + workload.hardware_description()
                + "\n" + workload.metadata() + "\nunavailable=" + workload.unavailable(c);
        export_last();
        status = endReason + ": " + recorder.count + " samples. Stats calculated AFTER stop.";
        return aborted;
    }

    private boolean cancelled(Config c) { return gamepad1.b || (c.live() && !armed()); }
    private boolean armed() { return gamepad1.left_bumper && gamepad1.right_bumper; }

    /** P7 intentionally measures this entire block; result UI never runs during recording. */
    private boolean workload_telemetry(Config c, long now) {
        if (c.telemetryPeriodNanos < 0 || now < nextWorkloadTelemetry) return false;
        nextWorkloadTelemetry = now + c.telemetryPeriodNanos;
        telemetry.addData("Benchmark phase", phase);
        telemetry.addData("Synthetic distance [in]", workload.distance);
        telemetry.addData("Synthetic target RPM", workload.rpm);
        telemetry.addData("Synthetic hood [deg]", workload.hoodAngle);
        telemetry.addData("Read/compute checksum", workload.checksum);
        telemetry.update();
        return true;
    }

    private void export_last() {
        if (lastSummary == null) return;
        try {
            if (!sessionDirectory.isDirectory() && !sessionDirectory.mkdirs()) {
                throw new IOException("Cannot create " + sessionDirectory);
            }
            recorder.export(new File(sessionDirectory, lastRunId + ".csv"), lastMetadata);
            if (!lastSummaryWritten) {
                File summaryFile = new File(sessionDirectory, lastRunId + "_summary.csv");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, false))) {
                    writer.write("run_id,reference,repeat,elapsed_s,actual_hz,metric,n,mean_ms,median_ms,p95_ms,p99_ms,max_ms,over_10ms,over_20ms\n");
                    write_summary(writer, "cycle", lastSummary.cycle);
                    write_summary(writer, "work", lastSummary.work);
                    if (lastTrial.config.stageTimings) {
                        write_summary(writer, "read", lastSummary.read);
                        write_summary(writer, "compute", lastSummary.compute);
                        write_summary(writer, "write", lastSummary.write);
                        write_summary(writer, "telemetry", lastSummary.telemetry);
                    }
                }
                lastSummaryWritten = true;
            }
            lastExportError = "";
        } catch (IOException error) {
            lastExportError = "CSV failed; START retries before next run: " + error.getMessage();
        }
    }

    private void write_summary(BufferedWriter writer, String metric, Stats s) throws IOException {
        writer.write(String.format(Locale.US, "%s,%s,%d,%.9f,%.6f,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d%n",
                lastRunId, lastTrial.reference, lastTrial.repeat, lastElapsedS, lastHz,
                metric, s.n, s.meanMs, s.medianMs, s.p95Ms, s.p99Ms, s.maxMs, s.over10ms, s.over20ms));
    }

    private void show_menu() {
        telemetry.addLine("PERFORMANCE COMPARISON | " + (isStarted() ? "READY" : "Configure then press DS START"));
        telemetry.addLine("D-pad select/edit | X baseline | Y optimized | A run | B end+calculate | START retry CSV");
        telemetry.addData("Status", status);
        telemetry.addData("Hardware", workload.hardware_description());
        telemetry.addData("Unavailable / simulated", workload.unavailable(draft));
        telemetry.addLine("Live drive/hood: clear movement area and hold BOTH bumpers throughout.");
        for (int row = Math.max(0, selected - 3); row <= Math.min(SETTING_COUNT - 1, selected + 3); row++) {
            telemetry.addLine((row == selected ? "> " : "  ") + setting_text(row));
        }
        if (lastSummary != null) {
            Stats s = lastSummary.cycle;
            telemetry.addData("Last trial", lastRunId);
            telemetry.addData("Cycle ms: mean / median", "%.4f / %.4f", s.meanMs, s.medianMs);
            telemetry.addData("p95 / p99 / max ms", "%.4f / %.4f / %.4f", s.p95Ms, s.p99Ms, s.maxMs);
            telemetry.addData("Samples / elapsed / Hz", "%d / %.3f s / %.1f", s.n, lastElapsedS, lastHz);
            telemetry.addData("Cycles >10 / >20 ms", "%d / %d", s.over10ms, s.over20ms);
            telemetry.addData("CSV directory", sessionDirectory.getAbsolutePath());
        }
        if (!lastExportError.isEmpty()) telemetry.addLine(lastExportError);
        telemetry.update();
    }

    private String setting_text(int row) {
        switch (row) {
            case 0: return "Suite: " + suite;
            case 1: return "P1 bulk: " + draft.bulkMode;
            case 2: return "P2 power policy: " + draft.powerPolicy;
            case 3: return "P3 servo policy: " + draft.servoPolicy;
            case 4: return "P4 share motor reads: " + draft.shareReads;
            case 5: return "P5 range interval ms: " + draft.rangeIntervalNanos / 1_000_000;
            case 6: return "P6 stagger range reads: " + draft.staggerRanges;
            case 7: return "P7 telemetry: " + (draft.telemetryPeriodNanos < 0 ? "OFF" : draft.telemetryPeriodNanos == 0 ? "EVERY LOOP" : draft.telemetryPeriodNanos / 1_000_000 + " ms");
            case 8: return "P8 reuse target: " + draft.reuseTarget;
            case 9: return "P9 shot result: " + draft.shotMode;
            case 10: return "P10 cache velocity target: " + draft.cacheVelocity;
            case 11: return "Drive output: " + draft.outputMode + " (POWER <=0.10; VELOCITY <=100 ticks/s)";
            case 12: return "Live hood writes: " + draft.servoLive + " (+/-0.01 from INIT command)";
            case 13: return "Command profile: " + draft.commandProfile;
            case 14: return "Synthetic pose: " + draft.poseProfile;
            case 15: return "Stage timing probes: " + draft.stageTimings;
            case 16: return "Measured seconds: " + DURATIONS_S[durationIndex];
            case 17: return "Repeats: " + REPEATS[repeatsIndex];
            default: return "Warmup seconds: " + WARMUPS_S[warmupIndex];
        }
    }

    private void edit(int delta) {
        switch (selected) {
            case 0: suite = Suite.values()[wrap(suite.ordinal() + delta, Suite.values().length)]; break;
            case 1: draft.bulkMode = LynxModule.BulkCachingMode.values()[wrap(draft.bulkMode.ordinal() + delta, 3)]; break;
            case 2: draft.powerPolicy = WritePolicy.values()[wrap(draft.powerPolicy.ordinal() + delta, 3)]; break;
            case 3: draft.servoPolicy = WritePolicy.values()[wrap(draft.servoPolicy.ordinal() + delta, 3)]; break;
            case 4: draft.shareReads = !draft.shareReads; break;
            case 5: draft.rangeIntervalNanos = next(RANGE_PERIODS, draft.rangeIntervalNanos, delta); if (draft.rangeIntervalNanos == 0) draft.staggerRanges = false; break;
            case 6: draft.staggerRanges = !draft.staggerRanges; if (draft.staggerRanges && draft.rangeIntervalNanos == 0) draft.rangeIntervalNanos = 300_000_000L; break;
            case 7: draft.telemetryPeriodNanos = next(TELEMETRY_PERIODS, draft.telemetryPeriodNanos, delta); break;
            case 8: draft.reuseTarget = !draft.reuseTarget; break;
            case 9: draft.shotMode = ShotMode.values()[wrap(draft.shotMode.ordinal() + delta, 3)]; break;
            case 10: draft.cacheVelocity = !draft.cacheVelocity; break;
            case 11: draft.outputMode = OutputMode.values()[wrap(draft.outputMode.ordinal() + delta, 3)]; break;
            case 12: draft.servoLive = !draft.servoLive; break;
            case 13: draft.commandProfile = CommandProfile.values()[wrap(draft.commandProfile.ordinal() + delta, 3)]; break;
            case 14: draft.poseProfile = PoseProfile.values()[wrap(draft.poseProfile.ordinal() + delta, 3)]; break;
            case 15: draft.stageTimings = !draft.stageTimings; break;
            case 16: durationIndex = wrap(durationIndex + delta, DURATIONS_S.length); break;
            case 17: repeatsIndex = wrap(repeatsIndex + delta, REPEATS.length); break;
            default: warmupIndex = wrap(warmupIndex + delta, WARMUPS_S.length);
        }
    }

    static void apply_baseline(Config c) {
        c.bulkMode = LynxModule.BulkCachingMode.OFF;
        c.powerPolicy = WritePolicy.RAW; c.servoPolicy = WritePolicy.RAW;
        c.shareReads = false; c.rangeIntervalNanos = 0; c.staggerRanges = false;
        c.telemetryPeriodNanos = 0; c.reuseTarget = false;
        c.shotMode = ShotMode.ALLOCATE; c.cacheVelocity = false;
    }

    static void apply_optimized(Config c) {
        c.bulkMode = LynxModule.BulkCachingMode.MANUAL;
        c.powerPolicy = WritePolicy.EPSILON; c.servoPolicy = WritePolicy.EPSILON;
        c.shareReads = true; c.rangeIntervalNanos = 300_000_000L; c.staggerRanges = true;
        c.telemetryPeriodNanos = 100_000_000L; c.reuseTarget = true;
        c.shotMode = ShotMode.REUSE; c.cacheVelocity = true;
    }

    static List<Trial> build_trials(Config selected, Suite suite, int repeats) {
        List<Trial> result = new ArrayList<>();
        Random random = new Random(ORDER_SEED);
        for (int repeat = 1; repeat <= repeats; repeat++) {
            List<Trial> group = new ArrayList<>();
            Config base = selected.copy(); apply_baseline(base);
            Config all = selected.copy(); apply_optimized(all);
            if (suite == Suite.CUSTOM) group.add(new Trial("CUSTOM", "CUSTOM", selected, repeat));
            else {
                group.add(new Trial("BASELINE", "BASELINE", base, repeat));
                group.add(new Trial("ALL", "BASELINE", all, repeat));
                if (suite == Suite.SINGLE_CHANGES) {
                    for (int variant = 0; variant < 15; variant++) {
                        Config c = base.copy(); String label;
                        switch (variant) {
                            case 0: c.bulkMode = LynxModule.BulkCachingMode.AUTO; label = "P1_AUTO"; break;
                            case 1: c.bulkMode = LynxModule.BulkCachingMode.MANUAL; label = "P1_MANUAL"; break;
                            case 2: c.powerPolicy = WritePolicy.EXACT_CHANGE; label = "P2_EXACT"; break;
                            case 3: c.powerPolicy = WritePolicy.EPSILON; label = "P2_EPSILON"; break;
                            case 4: c.servoPolicy = WritePolicy.EXACT_CHANGE; label = "P3_EXACT"; break;
                            case 5: c.servoPolicy = WritePolicy.EPSILON; label = "P3_EPSILON"; break;
                            case 6: c.shareReads = true; label = "P4_SHARE"; break;
                            case 7: c.rangeIntervalNanos = 300_000_000L; label = "P5_RANGE_300"; break;
                            case 8: c.rangeIntervalNanos = 300_000_000L; c.staggerRanges = true; label = "P6_STAGGER_300"; break;
                            case 9: c.telemetryPeriodNanos = 100_000_000L; label = "P7_TELEMETRY_100"; break;
                            case 10: c.telemetryPeriodNanos = 200_000_000L; label = "P7_TELEMETRY_200"; break;
                            case 11: c.telemetryPeriodNanos = -1; label = "P7_TELEMETRY_OFF"; break;
                            case 12: c.reuseTarget = true; label = "P8_TARGET"; break;
                            case 13: c.shotMode = ShotMode.REUSE; label = "P9_REUSE"; break;
                            default: c.cacheVelocity = true; label = "P10_VELOCITY";
                        }
                        group.add(new Trial(label, variant == 8 ? "P5_RANGE_300" : "BASELINE", c, repeat));
                    }
                    Config cached = base.copy(); cached.shotMode = ShotMode.CACHE_UNCHANGED;
                    group.add(new Trial("P9_CACHE", "BASELINE", cached, repeat));
                } else if (suite == Suite.ALL_MINUS_ONE) {
                    for (int toggle = 1; toggle <= 10; toggle++) {
                        Config c = all.copy(); String label = "WITHOUT_P" + toggle;
                        switch (toggle) {
                            case 1: c.bulkMode = LynxModule.BulkCachingMode.OFF; break;
                            case 2: c.powerPolicy = WritePolicy.RAW; break;
                            case 3: c.servoPolicy = WritePolicy.RAW; break;
                            case 4: c.shareReads = false; break;
                            case 5: c.rangeIntervalNanos = 0; c.staggerRanges = false; label = "WITHOUT_P5_AND_P6"; break;
                            case 6: c.staggerRanges = false; break;
                            case 7: c.telemetryPeriodNanos = 0; break;
                            case 8: c.reuseTarget = false; break;
                            case 9: c.shotMode = ShotMode.ALLOCATE; break;
                            default: c.cacheVelocity = false;
                        }
                        group.add(new Trial(label, "ALL", c, repeat));
                    }
                }
                Collections.shuffle(group, random);
            }
            result.addAll(group);
        }
        return result;
    }

    private int buttons() {
        return (gamepad1.dpad_up ? 1 : 0) | (gamepad1.dpad_down ? 2 : 0)
                | (gamepad1.dpad_left ? 4 : 0) | (gamepad1.dpad_right ? 8 : 0)
                | (gamepad1.x ? 16 : 0) | (gamepad1.y ? 32 : 0)
                | (gamepad1.a ? 64 : 0) | (gamepad1.start ? 128 : 0);
    }
    private static int wrap(int value, int size) { return (value % size + size) % size; }
    private static long next(long[] values, long current, int delta) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[wrap(i + delta, values.length)];
        return values[0];
    }

    /** Only record() runs in the measured loop; summaries and CSV writes run after it stops. */
    static final class Recorder {
        private final long[] cycleNanos;
        private final long[] workNanos;
        private final long[] readNanos;
        private final long[] computeNanos;
        private final long[] writeNanos;
        private final long[] telemetryNanos;
        private final long[] sortedNanos;
        int count;
        boolean full;

        Recorder(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Timing capacity must be positive");
            }
            cycleNanos = new long[capacity];
            workNanos = new long[capacity];
            readNanos = new long[capacity];
            computeNanos = new long[capacity];
            writeNanos = new long[capacity];
            telemetryNanos = new long[capacity];
            sortedNanos = new long[capacity];
        }

        void reset() {
            count = 0;
            full = false;
        }

        void record(long cycle, long work, long read, long compute, long write, long telemetry) {
            if (full) {
                throw new IllegalStateException("Timing buffer full; stop the measured block");
            }
            int index = count;
            cycleNanos[index] = cycle;
            workNanos[index] = work;
            readNanos[index] = read;
            computeNanos[index] = compute;
            writeNanos[index] = write;
            telemetryNanos[index] = telemetry;
            count = index + 1;
            full = count == cycleNanos.length;
        }

        Summary summarize() {
            return new Summary(summarize_values(cycleNanos), summarize_values(workNanos),
                    summarize_values(readNanos), summarize_values(computeNanos),
                    summarize_values(writeNanos), summarize_values(telemetryNanos));
        }

        private Stats summarize_values(long[] values) {
            if (count == 0) {
                return new Stats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, 0, 0);
            }
            System.arraycopy(values, 0, sortedNanos, 0, count);
            java.util.Arrays.sort(sortedNanos, 0, count);
            double sumNanos = 0.0;
            int over10ms = 0;
            int over20ms = 0;
            for (int i = 0; i < count; i++) {
                long value = sortedNanos[i];
                sumNanos += value;
                if (value > 10_000_000L) over10ms++;
                if (value > 20_000_000L) over20ms++;
            }
            double medianNanos;
            if ((count & 1) == 0) {
                medianNanos = (sortedNanos[count / 2 - 1] / 2.0)
                        + (sortedNanos[count / 2] / 2.0);
            } else {
                medianNanos = sortedNanos[count / 2];
            }
            int p95Index = (int) ((95L * count + 99L) / 100L) - 1;
            int p99Index = (int) ((99L * count + 99L) / 100L) - 1;
            return new Stats(count, sumNanos / count / 1_000_000.0,
                    medianNanos / 1_000_000.0, sortedNanos[p95Index] / 1_000_000.0,
                    sortedNanos[p99Index] / 1_000_000.0,
                    sortedNanos[count - 1] / 1_000_000.0, over10ms, over20ms);
        }

        /** Metadata is newline-separated text; every line is emitted as a CSV comment. */
        java.io.File export(java.io.File target, String metadata) throws java.io.IOException {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(target),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                writer.write("# sample_count=" + count);
                writer.newLine();
                writer.write("# buffer_capacity=" + cycleNanos.length);
                writer.newLine();
                writer.write("# stopped_at_capacity=" + full);
                writer.newLine();
                if (metadata != null && !metadata.isEmpty()) {
                    for (String line : metadata.split("\\r\\n|\\r|\\n")) {
                        writer.write("# ");
                        writer.write(line);
                        writer.newLine();
                    }
                }
                writer.write("sample,cycle_ns,work_ns,read_ns,compute_ns,write_ns,telemetry_ns");
                writer.newLine();
                for (int i = 0; i < count; i++) {
                    writer.write(Integer.toString(i + 1));
                    write_value(writer, cycleNanos[i]);
                    write_value(writer, workNanos[i]);
                    write_value(writer, readNanos[i]);
                    write_value(writer, computeNanos[i]);
                    write_value(writer, writeNanos[i]);
                    write_value(writer, telemetryNanos[i]);
                    writer.newLine();
                }
            }
            return target;
        }

        private static void write_value(java.io.BufferedWriter writer, long value)
                throws java.io.IOException {
            writer.write(',');
            writer.write(Long.toString(value));
        }
    }

    static final class Summary {
        final Stats cycle;
        final Stats work;
        final Stats read;
        final Stats compute;
        final Stats write;
        final Stats telemetry;

        Summary(Stats cycle, Stats work, Stats read, Stats compute, Stats write, Stats telemetry) {
            this.cycle = cycle;
            this.work = work;
            this.read = read;
            this.compute = compute;
            this.write = write;
            this.telemetry = telemetry;
        }
    }

    static final class Stats {
        final int n;
        final double meanMs;
        final double medianMs;
        final double p95Ms;
        final double p99Ms;
        final double maxMs;
        final int over10ms;
        final int over20ms;

        Stats(int n, double meanMs, double medianMs, double p95Ms, double p99Ms,
                double maxMs, int over10ms, int over20ms) {
            this.n = n;
            this.meanMs = meanMs;
            this.medianMs = medianMs;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.maxMs = maxMs;
            this.over10ms = over10ms;
            this.over20ms = over20ms;
        }
    }
    /** Test-only policies; no production wrapper, follower or subsystem is initialized. */
    private static final class Workload {
        static final double BENCH_POWER = 0.10;
        static final double BENCH_VELOCITY = 100.0; // Encoder ticks/s, never shooter RPM.
        private static final double POWER_EPSILON = 0.002;
        private static final double SERVO_EPSILON = 0.005;
        private static final int CONSUMERS = 3;
        private static final String[] MOTOR_NAMES = {"leftFront", "leftRear", "rightFront", "rightRear"};
        private final com.qualcomm.robotcore.hardware.DcMotorEx[] motors =
                new com.qualcomm.robotcore.hardware.DcMotorEx[MOTOR_NAMES.length];
        private final com.qualcomm.robotcore.hardware.DcMotor.RunMode[] savedModes =
                new com.qualcomm.robotcore.hardware.DcMotor.RunMode[MOTOR_NAMES.length];
        private final java.util.List<com.qualcomm.hardware.lynx.LynxModule> hubs;
        private final com.qualcomm.hardware.lynx.LynxModule.BulkCachingMode[] savedBulkModes;
        private final com.qualcomm.robotcore.hardware.ColorRangeSensor lower, upper;
        private final com.qualcomm.robotcore.hardware.Servo servo;
        private final double servoCenter;
        private final boolean servoCenterSafe;
        private final String hardwareDescription;
        private final double[] lastPower = new double[MOTOR_NAMES.length];
        private final double[] lastVelocity = new double[MOTOR_NAMES.length];
        private final boolean[] powerInitialized = new boolean[MOTOR_NAMES.length];
        private final boolean[] velocityInitialized = new boolean[MOTOR_NAMES.length];
        private final boolean[] touchedMotor = new boolean[MOTOR_NAMES.length];
        private final boolean[] activeVelocityMode = new boolean[MOTOR_NAMES.length];
        private boolean servoInitialized, servoTouched, motorsLive;
        private double lastServo;
        private Config config;
        private long lowerReadAt, upperReadAt, lowerValidAt, upperValidAt;
        private long nextLowerAt, nextUpperAt, nextPairAt;
        private boolean rangesStarted, lowerHasSample, upperHasSample;
        private boolean lowerValid, upperValid;
        private double lowerDistance, upperDistance;
        private boolean targetInitialized;
        private double lastX, lastY;
        private org.firstinspires.ftc.teamcode.core.units.Units.Alliance lastAlliance;
        private boolean lastPoseValid;
        private org.firstinspires.ftc.teamcode.subsystems.Shooter.HiveDistance target;
        // Retain the immutable result past compute(), matching the reusable result's lifetime.
        private org.firstinspires.ftc.teamcode.subsystems.Shooter.ShotSolution allocatedShot;
        private final MutableShot reusableShot = new MutableShot();
        private boolean shotInitialized;
        private double lastShotDistance;
        private boolean lastShotInputValid;
        private boolean reusableShotValid;
        private String shotVerification = "not checked";
        double distance = Double.NaN, rpm = Double.NaN, hoodAngle = Double.NaN, checksum;
        long lowerAgeNanos = -1, upperAgeNanos = -1;
        private long encoderPositionReads, encoderVelocityReads, invalidSamples, readErrors;
        private long lowerReads, upperReads, lowerInvalid, upperInvalid;
        private long lowerAgeTotal, upperAgeTotal, lowerAgeCount, upperAgeCount;
        private long lowerMaxAge, upperMaxAge, lowerMaxGap, upperMaxGap;
        private long powerAttempts, powerForwarded, powerSkipped, powerSdkCalls, powerShadowForwarded;
        private long servoAttempts, servoForwarded, servoSkipped, servoSdkCalls, servoShadowForwarded;
        private long velocityAttempts, velocityForwarded, velocitySkipped, velocitySdkCalls, velocityShadowForwarded;
        private long unavailablePowerCalls, unavailableVelocityCalls;
        private long targetCalculations, targetReuses, shotCalculations, shotReuses, allocatedShotCalls;
        private double powerMaxDeviation, servoMaxDeviation, velocityMaxDeviation;
        private double powerDeviationTotal, servoDeviationTotal;

        Workload(com.qualcomm.robotcore.hardware.HardwareMap map) {
            StringBuilder found = new StringBuilder("drive motors=");
            for (int i = 0; i < motors.length; i++) {
                motors[i] = map.tryGet(com.qualcomm.robotcore.hardware.DcMotorEx.class, MOTOR_NAMES[i]);
                if (i > 0) found.append(';');
                found.append(MOTOR_NAMES[i]).append(':').append(motors[i] != null ? "present" : "MISSING");
                if (motors[i] != null) {
                    savedModes[i] = motors[i].getMode();
                    found.append("(mode=").append(savedModes[i]).append(",direction=")
                            .append(motors[i].getDirection()).append(",zero=")
                            .append(motors[i].getZeroPowerBehavior()).append(')');
                }
            }
            lower = map.tryGet(com.qualcomm.robotcore.hardware.ColorRangeSensor.class, "colorSensorLow");
            upper = map.tryGet(com.qualcomm.robotcore.hardware.ColorRangeSensor.class, "colorSensorHigh");
            servo = map.tryGet(com.qualcomm.robotcore.hardware.Servo.class, "servoHood");
            // getPosition is the SDK's last commanded position, NOT physical hood feedback.
            servoCenter = servo == null ? Double.NaN : servo.getPosition();
            servoCenterSafe = Double.isFinite(servoCenter) && servoCenter >= 0.05 && servoCenter <= 0.95;
            hubs = map.getAll(com.qualcomm.hardware.lynx.LynxModule.class);
            savedBulkModes = new com.qualcomm.hardware.lynx.LynxModule.BulkCachingMode[hubs.size()];
            for (int i = 0; i < hubs.size(); i++) savedBulkModes[i] = hubs.get(i).getBulkCachingMode();
            found.append(";rangeLow=").append(lower != null).append(";rangeHigh=").append(upper != null)
                    .append(";servoHood=").append(servo != null).append(";servoInitCommand=").append(servoCenter)
                    .append(";servoLiveEnvelopeValid=").append(servoCenterSafe).append(";hubs=").append(hubs.size());
            hardwareDescription = found.toString();
        }

        String hardware_description() { return hardwareDescription; }

        String unavailable(Config settings) {
            StringBuilder message = new StringBuilder();
            if (hubs.isEmpty()) message.append("P1: no Lynx hub. ");
            for (int i = 0; i < motors.length; i++) {
                if (motors[i] == null) message.append("P2/P4/P10: missing ").append(MOTOR_NAMES[i]).append(". ");
            }
            if (lower == null || upper == null) message.append("P5/P6: missing range device(s). ");
            if (settings.staggerRanges && settings.rangeIntervalNanos <= 0)
                message.append("P6 needs positive interval; zero interval reads both every loop. ");
            if (!settings.servoLive) message.append("P3 shadow cache only; no SDK servo transport. ");
            else if (servo == null || !servoCenterSafe)
                message.append("P3 live unavailable: missing hood or initial SDK command outside [0.05,0.95]. ");
            if (settings.outputMode == OutputMode.SHADOW)
                message.append("P2/P10 shadow cache only; no SDK motor transport. ");
            else if (settings.outputMode != OutputMode.VELOCITY)
                message.append("P10 shadow cache only in POWER; use VELOCITY for live target calls. ");
            else message.append("P2 shadow cache only in VELOCITY; use POWER for live power calls. ");
            return message.toString();
        }

        void prepare(Config settings) {
            stop();
            config = settings;
            for (int i = 0; i < hubs.size(); i++) hubs.get(i).setBulkCachingMode(settings.bulkMode);
            for (int i = 0; i < motors.length; i++) {
                powerInitialized[i] = false;
                velocityInitialized[i] = false;
                if (motors[i] != null && settings.outputMode != OutputMode.SHADOW) {
                    // Changes of control mode happen outside timing; every transition invalidates both caches.
                    touchedMotor[i] = true;
                    motors[i].setPower(0.0);
                    activeVelocityMode[i] = settings.outputMode == OutputMode.VELOCITY;
                    motors[i].setMode(settings.outputMode == OutputMode.VELOCITY
                            ? com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_USING_ENCODER
                            : com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                }
            }
            servoInitialized = false;
            rangesStarted = false;
            lowerHasSample = upperHasSample = false;
            lowerValid = upperValid = false;
            lowerReadAt = upperReadAt = lowerValidAt = upperValidAt = 0;
            targetInitialized = shotInitialized = false;
            target = null;
            distance = rpm = hoodAngle = Double.NaN;
            lowerAgeNanos = upperAgeNanos = -1;
            verify_shot_model();
            reset_counters();
        }

        /** Constant-cost primitive updates only; warm-up state and refresh schedules are retained. */
        void reset_counters() {
            encoderPositionReads = encoderVelocityReads = invalidSamples = readErrors = 0;
            lowerReads = upperReads = lowerInvalid = upperInvalid = 0;
            lowerAgeTotal = upperAgeTotal = lowerAgeCount = upperAgeCount = 0;
            lowerMaxAge = upperMaxAge = lowerMaxGap = upperMaxGap = 0;
            powerAttempts = powerForwarded = powerSkipped = powerSdkCalls = powerShadowForwarded = 0;
            servoAttempts = servoForwarded = servoSkipped = servoSdkCalls = servoShadowForwarded = 0;
            velocityAttempts = velocityForwarded = velocitySkipped = velocitySdkCalls = velocityShadowForwarded = 0;
            unavailablePowerCalls = unavailableVelocityCalls = 0;
            targetCalculations = targetReuses = shotCalculations = shotReuses = allocatedShotCalls = 0;
            powerMaxDeviation = servoMaxDeviation = velocityMaxDeviation = 0;
            powerDeviationTotal = servoDeviationTotal = checksum = 0;
        }

        /** nowNanos is absolute System.nanoTime; MANUAL is cleared before this iteration's first read. */
        void read(long nowNanos) {
            if (config.bulkMode == com.qualcomm.hardware.lynx.LynxModule.BulkCachingMode.MANUAL)
                for (int i = 0; i < hubs.size(); i++) hubs.get(i).clearBulkCache();
            double readChecksum = 0;
            for (com.qualcomm.robotcore.hardware.DcMotorEx motor : motors) {
                if (motor == null) continue;
                if (config.shareReads) {
                    double position = read_position(motor), velocity = read_velocity(motor);
                    for (int consumer = 1; consumer <= CONSUMERS; consumer++)
                        readChecksum += consumer * (position + velocity * 0.001);
                } else {
                    for (int consumer = 1; consumer <= CONSUMERS; consumer++)
                        readChecksum += consumer * (read_position(motor) + read_velocity(motor) * 0.001);
                }
            }
            long interval = Math.max(0, config.rangeIntervalNanos);
            if (!rangesStarted) {
                rangesStarted = true;
                nextLowerAt = nextPairAt = nowNanos;
                nextUpperAt = config.staggerRanges && interval > 0 ? nowNanos + interval / 2 : nowNanos;
            }
            if (config.staggerRanges && interval > 0) {
                if (nowNanos - nextLowerAt >= 0) {
                    read_range(true);
                    nextLowerAt = next_deadline(nextLowerAt, System.nanoTime(), interval);
                }
                if (nowNanos - nextUpperAt >= 0) {
                    read_range(false);
                    nextUpperAt = next_deadline(nextUpperAt, System.nanoTime(), interval);
                }
            } else if (interval == 0 || nowNanos - nextPairAt >= 0) {
                read_range(true);
                read_range(false);
                nextPairAt = next_deadline(nextPairAt, System.nanoTime(), interval);
            }
            long ageAt = System.nanoTime(); // Age includes the work taken by both range calls.
            lowerAgeNanos = lowerValidAt == 0 ? -1 : Math.max(0, ageAt - lowerValidAt);
            upperAgeNanos = upperValidAt == 0 ? -1 : Math.max(0, ageAt - upperValidAt);
            if (lowerAgeNanos >= 0) {
                lowerAgeTotal += lowerAgeNanos; lowerAgeCount++;
                lowerMaxAge = Math.max(lowerMaxAge, lowerAgeNanos);
            }
            if (upperAgeNanos >= 0) {
                upperAgeTotal += upperAgeNanos; upperAgeCount++;
                upperMaxAge = Math.max(upperMaxAge, upperAgeNanos);
            }
            if (lowerValid) readChecksum += lowerDistance * 0.001;
            if (upperValid) readChecksum += upperDistance * 0.001;
            checksum += readChecksum;
        }

        /** Fixed origin deadlines match paired/staggered cadence; missed deadlines never create a backlog. */
        private static long next_deadline(long deadline, long completedAt, long interval) {
            if (interval <= 0) return completedAt;
            long late = Math.max(0, completedAt - deadline);
            return deadline + (late / interval + 1) * interval;
        }

        private double read_position(com.qualcomm.robotcore.hardware.DcMotorEx motor) {
            encoderPositionReads++;
            try { return motor.getCurrentPosition(); }
            catch (RuntimeException failedRead) { readErrors++; invalidSamples++; return 0; }
        }

        private double read_velocity(com.qualcomm.robotcore.hardware.DcMotorEx motor) {
            encoderVelocityReads++;
            try {
                double value = motor.getVelocity();
                if (Double.isFinite(value)) return value;
            } catch (RuntimeException failedRead) { readErrors++; }
            invalidSamples++;
            return 0;
        }

        private void read_range(boolean isLower) {
            com.qualcomm.robotcore.hardware.ColorRangeSensor sensor = isLower ? lower : upper;
            if (sensor == null) return;
            double value = Double.NaN;
            if (isLower) lowerReads++; else upperReads++;
            try { value = sensor.getDistance(org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.MM); }
            catch (RuntimeException failedRead) { readErrors++; }
            long completedAt = System.nanoTime();
            boolean valid = Double.isFinite(value) && value >= 0;
            if (isLower) {
                if (lowerHasSample) lowerMaxGap = Math.max(lowerMaxGap, completedAt - lowerReadAt);
                lowerReadAt = completedAt; lowerHasSample = true; lowerValid = valid;
                if (valid) { lowerDistance = value; lowerValidAt = completedAt; }
                else { lowerInvalid++; invalidSamples++; }
            } else {
                if (upperHasSample) upperMaxGap = Math.max(upperMaxGap, completedAt - upperReadAt);
                upperReadAt = completedAt; upperHasSample = true; upperValid = valid;
                if (valid) { upperDistance = value; upperValidAt = completedAt; }
                else { upperInvalid++; invalidSamples++; }
            }
        }

        /** P8/P9 use synthetic field coordinates; hardware localization is deliberately not a workload. */
        void compute(long elapsedNanos) {
            double seconds = elapsedNanos * 1e-9;
            double x = 24.0, y = 24.0, heading = 0;
            if (config.poseProfile == PoseProfile.HEADING_ONLY) heading = seconds * 0.4;
            else if (config.poseProfile == PoseProfile.MOVING) {
                x += 4.0 * Math.sin(seconds * 0.7);
                y += 4.0 * Math.cos(seconds * 0.5);
                heading = seconds * 0.4;
            }
            boolean valid = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(heading);
            org.firstinspires.ftc.teamcode.core.units.Units.Alliance alliance =
                    org.firstinspires.ftc.teamcode.core.units.Units.Alliance.BLUE;
            if (config.reuseTarget && targetInitialized && x == lastX && y == lastY
                    && alliance == lastAlliance && valid == lastPoseValid) targetReuses++;
            else {
                target = org.firstinspires.ftc.teamcode.subsystems.Shooter.nearest_hive(x, y, alliance, valid);
                targetCalculations++;
                targetInitialized = true; lastX = x; lastY = y; lastAlliance = alliance; lastPoseValid = valid;
            }
            boolean targetValid = target != null && target.valid;
            distance = targetValid ? target.distance : Double.NaN;
            if (config.shotMode == ShotMode.ALLOCATE) {
                allocatedShot = org.firstinspires.ftc.teamcode.subsystems.Shooter.calculate_shot_solution(distance);
                rpm = allocatedShot.rpm; hoodAngle = allocatedShot.hoodAngleDegrees;
                allocatedShotCalls++; shotCalculations++;
            } else {
                boolean unchanged = shotInitialized && targetValid == lastShotInputValid
                        && (distance == lastShotDistance || !targetValid);
                if (config.shotMode == ShotMode.CACHE_UNCHANGED && unchanged) shotReuses++;
                else {
                    reusableShot.calculate(distance);
                    reusableShotValid = reusableShot.valid;
                    shotInitialized = true; lastShotDistance = distance; lastShotInputValid = targetValid;
                    shotCalculations++;
                }
                rpm = reusableShotValid ? reusableShot.rpm : Double.NaN;
                hoodAngle = reusableShotValid ? reusableShot.hood : Double.NaN;
            }
            // All computed results feed an externally observed scalar, preventing dead-code elimination.
            checksum += (Double.isFinite(distance) ? distance : 0)
                    + (Double.isFinite(rpm) ? rpm * 0.001 : 0)
                    + (Double.isFinite(hoodAngle) ? hoodAngle * 0.01 : 0) + heading * 0.000001;
        }

        void write(long elapsedNanos, boolean armed) {
            if (!armed) { if (motorsLive) stop(); return; }
            double unit = command_fraction(elapsedNanos);
            double power = unit * BENCH_POWER;
            double velocity = unit * BENCH_VELOCITY;
            for (int i = 0; i < motors.length; i++) {
                // The two policies are both simulated, but only ONE control method can reach the SDK.
                powerAttempts++;
                boolean powerForward = forward(config.powerPolicy, powerInitialized[i], power, lastPower[i], POWER_EPSILON, true);
                if (powerForward) {
                    lastPower[i] = power; powerInitialized[i] = true; powerForwarded++;
                    if (config.outputMode == OutputMode.POWER) {
                        if (motors[i] != null) {
                            touchedMotor[i] = motorsLive = true;
                            motors[i].setPower(power); powerSdkCalls++;
                        } else unavailablePowerCalls++;
                    } else powerShadowForwarded++;
                } else powerSkipped++;
                double powerDifference = Math.abs(power - lastPower[i]);
                powerMaxDeviation = Math.max(powerMaxDeviation, powerDifference);
                powerDeviationTotal += powerDifference;

                velocityAttempts++;
                boolean velocityForward = !config.cacheVelocity || !velocityInitialized[i]
                        || velocity != lastVelocity[i] || (velocity == 0 && lastVelocity[i] != 0);
                if (velocityForward) {
                    lastVelocity[i] = velocity; velocityInitialized[i] = true; velocityForwarded++;
                    if (config.outputMode == OutputMode.VELOCITY) {
                        if (motors[i] != null) {
                            touchedMotor[i] = motorsLive = true;
                            motors[i].setVelocity(velocity); velocitySdkCalls++;
                        } else unavailableVelocityCalls++;
                    } else velocityShadowForwarded++;
                } else velocitySkipped++;
                velocityMaxDeviation = Math.max(velocityMaxDeviation, Math.abs(velocity - lastVelocity[i]));
            }
            // Even the full STEPS profile stays within +/-0.01 of the captured SDK command.
            double center = servoCenterSafe ? servoCenter : 0.5;
            double position = center + (unit - 0.5) * 0.02;
            servoAttempts++;
            if (forward(config.servoPolicy, servoInitialized, position, lastServo, SERVO_EPSILON, false)) {
                lastServo = position; servoInitialized = true; servoForwarded++;
                if (config.servoLive && servo != null && servoCenterSafe) {
                    servoTouched = true;
                    servo.setPosition(position); servoSdkCalls++;
                } else servoShadowForwarded++;
            } else servoSkipped++;
            double servoDifference = Math.abs(position - lastServo);
            servoMaxDeviation = Math.max(servoMaxDeviation, servoDifference);
            servoDeviationTotal += servoDifference;
            checksum += lastPower[0] + lastVelocity[0] * 0.001 + lastServo;
        }

        private double command_fraction(long elapsedNanos) {
            switch (config.commandProfile) {
                case SMALL_CHANGES:
                    // 0.0001 power / 0.00002 servo increments each 10 ms, crossing deadbands cumulatively.
                    return ((elapsedNanos / 10_000_000L) % 1001) / 1000.0;
                case STEPS:
                    switch ((int) ((elapsedNanos / 1_000_000_000L) % 4)) {
                        case 0: return 0;
                        case 1: return 0.25;
                        case 2: return 0.75;
                        default: return 1;
                    }
                default: return 0.5;
            }
        }

        private static boolean forward(WritePolicy policy, boolean initialized,
                double requested, double previous, double epsilon, boolean exactZero) {
            if (!Double.isFinite(requested)) return false;
            return !initialized || policy == WritePolicy.RAW || (exactZero && requested == 0 && previous != 0)
                    || (policy == WritePolicy.EXACT_CHANGE && requested != previous)
                    || (policy == WritePolicy.EPSILON && Math.abs(requested - previous) >= epsilon);
        }

        /** Stop all touched motors even if one device fails; never restore an earlier nonzero output. */
        void stop() {
            RuntimeException first = null;
            for (int i = 0; i < motors.length; i++) {
                if (motors[i] == null || !touchedMotor[i]) continue;
                // setVelocity does not refresh the SDK's cached power; force its zero target first.
                if (activeVelocityMode[i]) {
                    try { motors[i].setVelocity(0.0); }
                    catch (RuntimeException failure) { if (first == null) first = failure; }
                }
                try { motors[i].setPower(0.0); }
                catch (RuntimeException failure) { if (first == null) first = failure; }
                powerInitialized[i] = velocityInitialized[i] = false;
            }
            motorsLive = false;
            if (first != null) throw first;
        }

        void restore() {
            RuntimeException first = null;
            try { stop(); } catch (RuntimeException failure) { first = failure; }
            for (int i = 0; i < motors.length; i++) {
                if (motors[i] == null || !touchedMotor[i]) continue;
                try {
                    motors[i].setMode(savedModes[i]);
                    motors[i].setPower(0.0);
                } catch (RuntimeException failure) { if (first == null) first = failure; }
            }
            if (servoTouched && servo != null && servoCenterSafe) {
                try { servo.setPosition(servoCenter); }
                catch (RuntimeException failure) { if (first == null) first = failure; }
            }
            for (int i = 0; i < hubs.size(); i++) {
                try { hubs.get(i).setBulkCachingMode(savedBulkModes[i]); }
                catch (RuntimeException failure) { if (first == null) first = failure; }
            }
            if (first != null) throw first;
        }

        /** Called only after recording. No string construction or aggregation belongs in the hot loop. */
        String metadata() {
            return "targetInput=SYNTHETIC_FIELD_COORDINATES_NO_LOCALIZATION;alliance=BLUE;readConsumers=" + CONSUMERS
                    + ";powerCap=" + BENCH_POWER + ";velocityCapTicksPerSec=" + BENCH_VELOCITY
                    + ";setupStopRestoreCallsExcludedFromCounters=true;forwardedApiCallsAreNotHubPackets=true"
                    + ";servoCenterIsSdkCommandNotFeedback=true;servoEnvelopeHalfWidth=0.01"
                    + ";powerEpsilon=" + POWER_EPSILON + ";servoEpsilon=" + SERVO_EPSILON
                    + ";encoderPositionReadAttempts=" + encoderPositionReads + ";encoderVelocityReadAttempts=" + encoderVelocityReads
                    + ";invalidSamples=" + invalidSamples + ";hardwareReadErrors=" + readErrors
                    + ";lowerRangeReadAttempts=" + lowerReads + ";upperRangeReadAttempts=" + upperReads
                    + ";lowerInvalid=" + lowerInvalid + ";upperInvalid=" + upperInvalid
                    + ";lowerLatestSampleValid=" + lowerValid + ";upperLatestSampleValid=" + upperValid
                    + ";lowerLastValidAgeNs=" + lowerAgeNanos + ";upperLastValidAgeNs=" + upperAgeNanos
                    + ";lowerMeanValidSampleAgeNs=" + (lowerAgeCount == 0 ? -1 : (double) lowerAgeTotal / lowerAgeCount)
                    + ";upperMeanValidSampleAgeNs=" + (upperAgeCount == 0 ? -1 : (double) upperAgeTotal / upperAgeCount)
                    + ";lowerMaxValidSampleAgeNs=" + lowerMaxAge + ";upperMaxValidSampleAgeNs=" + upperMaxAge
                    + ";lowerMaxRefreshGapNs=" + lowerMaxGap + ";upperMaxRefreshGapNs=" + upperMaxGap
                    + ";powerAttempts=" + powerAttempts + ";powerPolicyForwarded=" + powerForwarded + ";powerPolicySkipped=" + powerSkipped
                    + ";powerSdkCalls=" + powerSdkCalls + ";powerShadowForwarded=" + powerShadowForwarded
                    + ";powerMissingDeviceForwarded=" + unavailablePowerCalls + ";powerMaxDeviation=" + powerMaxDeviation
                    + ";powerMeanDeviation=" + (powerAttempts == 0 ? 0 : powerDeviationTotal / powerAttempts)
                    + ";servoAttempts=" + servoAttempts + ";servoPolicyForwarded=" + servoForwarded + ";servoPolicySkipped=" + servoSkipped
                    + ";servoSdkCalls=" + servoSdkCalls + ";servoShadowForwarded=" + servoShadowForwarded
                    + ";servoMaxDeviation=" + servoMaxDeviation
                    + ";servoMeanDeviation=" + (servoAttempts == 0 ? 0 : servoDeviationTotal / servoAttempts)
                    + ";velocityAttempts=" + velocityAttempts + ";velocityPolicyForwarded=" + velocityForwarded
                    + ";velocityPolicySkipped=" + velocitySkipped + ";velocitySdkCalls=" + velocitySdkCalls
                    + ";velocityShadowForwarded=" + velocityShadowForwarded + ";velocityMissingDeviceForwarded=" + unavailableVelocityCalls
                    + ";velocityMaxDeviation=" + velocityMaxDeviation + ";targetCalculations=" + targetCalculations
                    + ";targetReuses=" + targetReuses + ";shotCalculations=" + shotCalculations
                    + ";shotReuses=" + shotReuses + ";shotAllocationApiCalls=" + allocatedShotCalls
                    + ";shotNumericVerification=" + shotVerification + ";checksum=" + checksum;
        }

        private void verify_shot_model() {
            double[] inputs = {0, 0.1, 24, 72, 144, 1000, Double.NaN, Double.POSITIVE_INFINITY, -1};
            boolean matches = true;
            for (double input : inputs) {
                org.firstinspires.ftc.teamcode.subsystems.Shooter.ShotSolution reference =
                        org.firstinspires.ftc.teamcode.subsystems.Shooter.calculate_shot_solution(input);
                reusableShot.calculate(input);
                matches &= reference.valid == reusableShot.valid
                        && (reference.valid ? reference.rpm == reusableShot.rpm && reference.hoodAngleDegrees == reusableShot.hood
                        : Double.isNaN(reusableShot.rpm) && Double.isNaN(reusableShot.hood));
            }
            shotVerification = matches ? "PASS_9_inputs_outside_timing" : "FAILED_DO_NOT_COMPARE_P9";
            if (!matches) throw new IllegalStateException("Reusable shot model differs from Shooter.calculate_shot_solution");
        }

        /** Same public coefficients, validity rules, Horner evaluation and clamps as Shooter. */
        private static final class MutableShot {
            private static final boolean MODEL_VALID =
                    Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_A)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_B)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_C)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_A)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_B)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_C)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MIN_ANGLE_DEG)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MAX_ANGLE_DEG)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MIN_ANGLE)
                    && Double.isFinite(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MAX_ANGLE)
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MAX_ANGLE_DEG > org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MIN_ANGLE_DEG
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MIN_ANGLE >= 0
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MIN_ANGLE <= 1
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MAX_ANGLE >= 0
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MAX_ANGLE <= 1
                    && org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MIN_ANGLE != org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_SERVO_AT_MAX_ANGLE;
            double rpm, hood;
            boolean valid;

            void calculate(double inputDistance) {
                if (!MODEL_VALID || !Double.isFinite(inputDistance) || inputDistance < 0) { invalidate(); return; }
                double fittedRpm = (org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_A * inputDistance
                        + org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_B) * inputDistance
                        + org.firstinspires.ftc.teamcode.subsystems.Shooter.DISTANCE_C;
                double fittedHood = (org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_A * inputDistance
                        + org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_B) * inputDistance
                        + org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_DISTANCE_C;
                if (!Double.isFinite(fittedRpm) || !Double.isFinite(fittedHood)) { invalidate(); return; }
                rpm = Math.max(0, Math.min(org.firstinspires.ftc.teamcode.subsystems.Shooter.MAX_RPM, fittedRpm));
                hood = Math.max(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MIN_ANGLE_DEG,
                        Math.min(org.firstinspires.ftc.teamcode.subsystems.Shooter.HOOD_MAX_ANGLE_DEG, fittedHood));
                valid = true;
            }

            private void invalidate() { valid = false; rpm = hood = Double.NaN; }
        }
    }
}
