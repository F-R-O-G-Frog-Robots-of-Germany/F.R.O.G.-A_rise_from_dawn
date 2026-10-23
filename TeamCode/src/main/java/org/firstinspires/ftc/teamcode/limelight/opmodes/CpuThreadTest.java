package org.firstinspires.ftc.teamcode.limelight.opmodes;

import android.os.Process;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.RobotLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineWorker;
import org.firstinspires.ftc.teamcode.limelight.CpuStats;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.LimelightWorker;
import org.firstinspires.ftc.teamcode.limelight.LoopPeriodStats;
import org.firstinspires.ftc.teamcode.limelight.Pollen;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.ThreadAffinity;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/**
 * Disabled, stationary diagnostics: no motors, follower, intake or shooter are constructed.
 * Enable this annotation deliberately for a bench check. A requests one synthetic solve;
 * B cancels it; X cycles all-unpinned / main {0} / main {0,1} / main {0,1,2}.
 * Y toggles a repeatable synthetic solver load. Multiple eligible CPUs never make one
 * Java thread execute concurrently. Workers remain on CPUs 1/2 in the three pinned modes.
 * Mode changes stop old owners and restore Android's original mask before creating threads.
 * INIT/setup and a three-second warmup are excluded; warmup cannot guarantee settled JIT.
 * Camera mapping uses a fixed SYNTHETIC pose, so none of this map is a real robot position.
 * Per-core CPU percentages are total system load; per-thread 100% means one occupied core.
 * /proc and sysfs are sampled at 2 Hz on a separate sleeping sampler, never in the loop.
 */
@Disabled
@TeleOp(name = "Limelight CPU thread test", group = "Limelight")
public final class CpuThreadTest extends LinearOpMode {
    private static final VisionPose SYNTHETIC_POSE = new VisionPose(72, 72, 0);
    // Owner's estimate for the future robot, not measurements from this stationary test.
    private static final double ASSUMED_MIN_CONTROL_CYCLE_MS = 4.0;
    private static final double ASSUMED_MAX_CONTROL_CYCLE_MS = 6.0;
    private static final long WARMUP_NANOS = 3_000_000_000L;
    private static final long LOAD_INTERVAL_NANOS = 1_000_000_000L;
    private enum Mode {
        ANDROID("Android original masks", new int[0]),
        ONE("Main {0}; vision 1; planner 2", new int[]{0}),
        TWO("Main {0,1}; vision 1; planner 2", new int[]{0, 1}),
        THREE("Main {0,1,2}; vision 1; planner 2", new int[]{0, 1, 2});
        final String label;
        final int[] cores;
        Mode(String label, int[] cores) { this.label = label; this.cores = cores; }
    }
    private enum Workload { IDLE, AUTO, MANUAL }
    private static final class ModeRun {
        final LoopPeriodStats periods = new LoopPeriodStats();
        final Workload workload;
        boolean cameraPresent;
        long offers, skipped, requests, completed;
        String status = "waiting for startup";
        LoopPeriodStats.Snapshot cached = periods.snapshot();
        ModeRun(Workload workload) { this.workload = workload; }
    }
    private final ModeRun[] runs = new ModeRun[Mode.values().length];
    private int threadPage;
    private int activeMode = -1, pendingMode;
    private Workload workload = Workload.IDLE;
    private ButineWorker planner;
    private LimelightWorker vision;
    private ThreadAffinity.Result controlBinding;
    private boolean switching, restoreBlocked, placementBlocked, ready, collecting;
    private long warmupUntil, modeStartedNanos, nextOffer, lastRequest, completedAtRequest;
    private String controlDetail = "Original Android mask", stateDetail = "Starting";

    @Override public void runOpMode() throws InterruptedException {
        final int controlTid = Process.myTid();
        Sampler sampler = new Sampler();
        try {
            // The sampler retains its original inherited Android mask in every mode.
            sampler.start();
            transition_to(0);
            telemetry.setMsTransmissionInterval(200);
            telemetry.addLine("Stationary CPU test: no drive hardware. Optional Limelight may be absent.");
            telemetry.addLine("Camera coordinates use fixed SYNTHETIC 72,72,0; do not drive from this map.");
            telemetry.addLine("X: Android / main 1 / 2 / 3 eligible CPUs. Y: fixed solver load / idle.");
            telemetry.addLine("A solve / B cancel: manual, non-comparable. dpad L/R: thread pages.");
            telemetry.update();
            waitForStart();
            gamepad1.resetEdgeDetection();
            gamepad1.copy(gamepad1);
            // INIT and waitForStart are never part of a measured period or warmup.
            long nextTelemetry = 0;
            while (opModeIsActive()) {
                long now = System.nanoTime();
                if (switching) complete_transition();
                if (vision != null && !switching) vision.submit_pose(SYNTHETIC_POSE);
                if (collecting) runs[activeMode].periods.record_tick(now);
                if (gamepad1.xWasPressed()) {
                    transition_to(restoreBlocked || switching ? pendingMode
                            : (activeMode + 1) % Mode.values().length);
                    nextTelemetry = 0;
                }
                if (gamepad1.yWasPressed()) {
                    workload = workload == Workload.AUTO ? Workload.IDLE : Workload.AUTO;
                    clear_comparisons();
                    transition_to(switching || restoreBlocked ? pendingMode : activeMode);
                    nextTelemetry = 0;
                }
                if (gamepad1.aWasPressed() && planner != null && !switching && !restoreBlocked) {
                    manual_workload();
                    request_solve();
                }
                if (gamepad1.bWasPressed() && planner != null && !switching && !restoreBlocked) {
                    manual_workload();
                    planner.cancel_pending();
                    lastRequest = 0;
                }
                if (gamepad1.dpadLeftWasPressed()) --threadPage;
                if (gamepad1.dpadRightWasPressed()) ++threadPage;
                // Affinity/stop commands may have taken seconds; never date warmup from before them.
                now = System.nanoTime();
                if (ready && workload == Workload.AUTO && now >= nextOffer) {
                    nextOffer = now + LOAD_INTERVAL_NANOS;
                    ModeRun run = runs[activeMode];
                    run.offers++;
                    // Do not supersede a slow solve: unchanged offered cadence, explicit skips.
                    if (lastRequest != 0 && planner.stats().completed == completedAtRequest) run.skipped++;
                    else request_solve();
                }
                if (now >= nextTelemetry) {
                    nextTelemetry = now + 200_000_000L;
                    if (!switching && !restoreBlocked) check_readiness(now);
                    validate_sampled_masks(sampler.snapshot, now);
                    telemetry.addLine("SYNTHETIC camera pose. CPU affinity is shared, not exclusive.");
                    telemetry.addData("Requested mode", Mode.values()[pendingMode].label);
                    telemetry.addData("Measurement", stateDetail);
                    telemetry.addData("Workload", workload == Workload.AUTO ? "1 synthetic solve offer/s; skip if busy"
                            : workload == Workload.MANUAL ? "MANUAL: non-repeatable; do not compare modes" : "Idle planner (no synthetic solves)");
                    telemetry.addLine("2/3 eligible CPUs allow migration; one main thread still runs on one CPU at a time.");
                    show_comparisons();
                    telemetry.addData("Assumed robot cycle (not measured)", "%.1f-%.1f ms => %.1f-%.1f Hz",
                            ASSUMED_MIN_CONTROL_CYCLE_MS, ASSUMED_MAX_CONTROL_CYCLE_MS,
                            hz_for_ms(ASSUMED_MAX_CONTROL_CYCLE_MS), hz_for_ms(ASSUMED_MIN_CONTROL_CYCLE_MS));
                    telemetry.addData("Assumed headroom at 200 Hz", "%.1f to %.1f ms (negative exceeds 5 ms)",
                            5.0 - ASSUMED_MAX_CONTROL_CYCLE_MS, 5.0 - ASSUMED_MIN_CONTROL_CYCLE_MS);
                    telemetry.addData("Control placement (verified at setup)", controlDetail);
                    ButineWorker.Stats plannerStats = planner == null ? null : planner.stats();
                    if (plannerStats != null) {
                        telemetry.addData("Butine", "request %d / completed %d / discarded %d; last %.3f ms / max %.3f ms",
                                lastRequest, plannerStats.completed, plannerStats.discarded,
                                plannerStats.lastSolveMs, plannerStats.maxSolveMs);
                        telemetry.addData("Butine placement", plannerStats.affinityDetail);
                        ButineWorker.Result result = planner.latest_result();
                        if (result != null) telemetry.addData("Synthetic plan", result.error != null ? result.error
                                : result.plan.expectedCollected + " pickups / " + result.plan.fittingRuns
                                + " runs / " + format(result.remaining_s()) + " s left");
                    }
                    if (vision != null) vision.telemetry(telemetry);
                    CpuStats.Snapshot snapshot = sampler.snapshot;
                    if (!sampler.error.isEmpty()) telemetry.addData("CPU sampler error", sampler.error);
                    if (snapshot == null) telemetry.addData("CPU sampler", sampler.error.isEmpty() ? "warming up" : sampler.error);
                    else {
                        LimelightWorker.Stats visionStats = vision == null ? null : vision.stats();
                        show_stats(snapshot, controlTid, visionStats != null && visionStats.running ? visionStats.tid : -1,
                                plannerStats != null && plannerStats.running ? plannerStats.tid : -1,
                                sampler.running ? sampler.tid : -1);
                    }
                    telemetry.addLine("X mode (replaces that mode's previous visit) | Y idle/load (clears table)");
                    telemetry.addLine("A solve / B cancel: manual | dpad L/R pages. Telemetry/sampler overhead included.");
                    telemetry.update();
                }
                idle();
            }
        } finally {
            try { if (planner != null && !planner.stop()) RobotLog.ww("CpuThreadTest", "Butine solver still finishing after bounded stop; result invalidated"); }
            finally {
                try { if (vision != null && !vision.stop()) RobotLog.ww("CpuThreadTest", "Limelight worker still finishing after bounded stop"); }
                finally {
                    try { if (!sampler.stop()) RobotLog.ww("CpuThreadTest", "CPU sampler still finishing after bounded stop"); }
                    finally { if (controlBinding != null && !ThreadAffinity.restore_current_thread(controlBinding))
                        RobotLog.ww("CpuThreadTest", "Could not restore original control-thread CPU mask"); }
                }
            }
        }
    }

    private void transition_to(int target) {
        pendingMode = target;
        if (activeMode >= 0 && runs[activeMode] != null) {
            ModeRun previous = runs[activeMode];
            previous.periods.pause();
            previous.cached = previous.periods.snapshot();
            if (!previous.status.startsWith("INVALID")) previous.status = "saved visit";
        }
        collecting = ready = false;
        switching = true;
        restoreBlocked = placementBlocked = false;
        stateDetail = "Stopping old owners; no periods recorded";
        if (planner != null) planner.request_stop();
        if (vision != null) vision.request_stop();
        try { if (planner != null) planner.stop(); }
        finally { if (vision != null) vision.stop(); }
        if (activeMode >= 0 && runs[activeMode] != null && planner != null) {
            runs[activeMode].completed = planner.stats().completed;
        }
        complete_transition();
    }

    private void complete_transition() {
        if ((planner != null && planner.has_owner()) || (vision != null && vision.has_owner())) {
            stateDetail = "Waiting for old owners after bounded stop; no replacement or measurement";
            return;
        }
        if (activeMode >= 0 && runs[activeMode] != null && planner != null) {
            // A finite solve may have completed after the first bounded stop returned.
            runs[activeMode].completed = planner.stats().completed;
        }
        // The original broad mask must be in effect before any replacement child is created.
        if (controlBinding != null && !ThreadAffinity.restore_current_thread(controlBinding)) {
            switching = false;
            restoreBlocked = true;
            stateDetail = "Original mask restore FAILED; X retries; no replacement or measurement";
            return;
        }
        controlBinding = null;
        Mode mode = Mode.values()[pendingMode];
        planner = new ButineWorker();
        Limelight camera = new Limelight(hardwareMap);
        vision = new LimelightWorker(camera, new PollenMap(camera));
        planner.preferredCore = mode == Mode.ANDROID ? -1 : 2;
        vision.preferredCore = mode == Mode.ANDROID ? -1 : 1;
        planner.start();
        vision.start();
        vision.submit_pose(SYNTHETIC_POSE);
        if (mode != Mode.ANDROID) controlBinding = ThreadAffinity.bind_current_thread(mode.cores);
        controlDetail = mode == Mode.ANDROID ? "Original Android mask restored/inherited; no affinity requests"
                : controlBinding.detail;
        activeMode = pendingMode;
        runs[activeMode] = new ModeRun(workload);
        warmupUntil = nextOffer = lastRequest = completedAtRequest = 0;
        modeStartedNanos = System.nanoTime();
        switching = false;
        stateDetail = "Waiting for worker initialization and placement verification";
    }

    private void check_readiness(long now) {
        if (placementBlocked || planner == null || vision == null) return;
        ButineWorker.Stats plan = planner.stats();
        LimelightWorker.Stats camera = vision.stats();
        Mode mode = Mode.values()[activeMode];
        boolean absent = !vision.has_owner() && vision.snapshot().status == Limelight.Status.INIT_FAILED;
        if (!planner.has_owner() || !plan.running) { block_measurement("Planner exited; X selects a new mode"); return; }
        if (mode != Mode.ANDROID && (controlBinding == null || !controlBinding.applied)) {
            block_measurement("Main affinity unverified; inspect placement; no comparison samples"); return;
        }
        if (plan.tid <= 0 || (!absent && (camera.tid <= 0 || camera.loops == 0))) {
            if (ready) block_measurement("Worker stopped or lost startup state; no comparison samples");
            return;
        }
        if (mode != Mode.ANDROID && (!plan.affinityApplied || plan.cpuCore != 2
                || (!absent && (!camera.affinityApplied || camera.cpuCore != 1)))) {
            block_measurement("Worker affinity unverified; inspect placement; no comparison samples"); return;
        }
        if (!absent && !camera.running) { block_measurement("Camera worker stopped unexpectedly; no comparison samples"); return; }
        ModeRun run = runs[activeMode];
        if (!ready) {
            ready = true;
            run.cameraPresent = !absent;
            warmupUntil = now + WARMUP_NANOS;
            nextOffer = now;
        } else if (run.cameraPresent == absent) {
            block_measurement("Camera workload changed; X restarts comparison"); return;
        }
        run.completed = plan.completed;
        if (now < warmupUntil) {
            stateDetail = String.format(Locale.US, "Warmup %.1f s; INIT/setup excluded (JIT may continue)", (warmupUntil - now) / 1e9);
        } else {
            if (!collecting) { collecting = true; run.periods.pause(); }
            stateDetail = "Measuring steady loop; compare same workload/camera and similar temperature";
        }
        run.status = collecting ? "measuring" : "warmup";
    }

    private void block_measurement(String reason) {
        placementBlocked = true;
        collecting = ready = false;
        stateDetail = reason;
        ModeRun run = runs[activeMode];
        run.periods.reset();
        run.status = "INVALID: " + reason;
    }

    private void clear_comparisons() {
        for (int i = 0; i < runs.length; i++) runs[i] = null;
    }

    private void manual_workload() {
        if (workload == Workload.MANUAL) return;
        workload = Workload.MANUAL;
        boolean cameraPresent = activeMode >= 0 && runs[activeMode] != null && runs[activeMode].cameraPresent;
        clear_comparisons();
        ModeRun run = new ModeRun(workload);
        run.cameraPresent = cameraPresent;
        run.status = collecting ? "manual: measuring" : "manual: warmup";
        runs[activeMode] = run;
    }

    private void request_solve() {
        Butine.Params settings = new Butine.Params();
        settings.remainingS = 30;
        completedAtRequest = planner.stats().completed;
        lastRequest = planner.request_plan(synthetic_pollen(), SYNTHETIC_POSE, SYNTHETIC_POSE, settings, 1);
        if (lastRequest != 0) runs[activeMode].requests++;
    }

    private void show_comparisons() {
        telemetry.addLine("Whole-visit percentiles: 10 us upward rounding; >100 ms overflow. p99 provisional below 1000 samples.");
        for (int i = 0; i < runs.length; i++) {
            ModeRun run = runs[i];
            if (run == null) { telemetry.addData("Compare " + Mode.values()[i].name(), "not sampled"); continue; }
            // Saved visits are immutable: four visited modes must not quadruple scan work.
            if (i == activeMode) run.cached = run.periods.snapshot();
            LoopPeriodStats.Snapshot stats = run.cached;
            telemetry.addData("Compare " + Mode.values()[i].name(),
                    "%s / %s / camera worker %s; n=%d%s; mean %.3f / p95 %s / p99 %s / max %.3f ms; >5/>6 ms %d/%d",
                    run.status, run.workload, run.cameraPresent ? "active" : "absent",
                    stats.count, stats.enough_tail_samples() ? "" : " (provisional)", stats.meanMs,
                    percentile(stats.p95Ms), percentile(stats.p99Ms), stats.maxMs, stats.over5Ms, stats.over6Ms);
            telemetry.addData("Load " + Mode.values()[i].name(), "offers %d / requests %d / busy skips %d / completed %d",
                    run.offers, run.requests, run.skipped, run.completed);
            if (i == activeMode) {
                telemetry.addData("Measured stationary Hz", "last %.1f / mean-period %.1f / slowest %.1f",
                        hz_for_ms(stats.lastMs), hz_for_ms(stats.meanMs), hz_for_ms(stats.maxMs));
            }
        }
    }

    private static String percentile(double milliseconds) {
        return Double.isInfinite(milliseconds) ? ">100" : String.format(Locale.US, "%.3f", milliseconds);
    }

    private void validate_sampled_masks(CpuStats.Snapshot snapshot, long now) {
        if (!ready || snapshot == null || controlBinding == null || !controlBinding.applied
                || snapshot.capturedNanos < modeStartedNanos || now - snapshot.capturedNanos > 1_500_000_000L) return;
        ButineWorker.Stats plan = planner.stats();
        LimelightWorker.Stats camera = vision.stats();
        for (CpuStats.ThreadSample thread : snapshot.threads) {
            String expected = thread.tid == controlBinding.tid ? controlBinding.requestedMask
                    : thread.tid == plan.tid ? "4"
                    : runs[activeMode].cameraPresent && thread.tid == camera.tid ? "2" : null;
            if (expected == null || thread.allowedCpuMask == null) continue;
            try {
                String actual = new java.math.BigInteger(thread.allowedCpuMask.replace(",", "").trim(), 16).toString(16);
                if (!expected.equals(actual)) {
                    block_measurement("Sampled mask changed for TID " + thread.tid + ": expected "
                            + expected + ", read " + actual + "; comparison invalidated");
                    return;
                }
            } catch (NumberFormatException unavailable) {
                // /proc may be unreadable; telemetry continues to disclose raw allowed masks.
            }
        }
    }

    private void show_stats(CpuStats.Snapshot snapshot, int controlTid, int visionTid, int plannerTid, int samplerTid) {
        telemetry.addData("Stats age ms", "%.0f (%s)", (System.nanoTime() - snapshot.capturedNanos) / 1e6, snapshot.detail);
        for (CpuStats.CoreSample core : snapshot.cores) {
            telemetry.addData("CPU " + core.cpuId, "%s%% / %s kHz / online %s", format(core.cpuPercent),
                    core.frequencyKhz == null ? "unavailable" : core.frequencyKhz.toString(),
                    core.online == null ? "unavailable" : core.online.toString());
        }
        show_thread("Control", controlTid, snapshot.threads);
        show_thread("Limelight", visionTid, snapshot.threads);
        show_thread("Butine", plannerTid, snapshot.threads);
        show_thread("Sampler", samplerTid, snapshot.threads);
        ArrayList<CpuStats.ThreadSample> top = new ArrayList<>(snapshot.threads);
        top.sort(Comparator.comparingDouble((CpuStats.ThreadSample thread) ->
                thread.cpuPercent == null ? -1 : thread.cpuPercent).reversed());
        telemetry.addData("App threads", snapshot.threads.size());
        for (int i = 0; i < Math.min(6, top.size()); ++i) {
            CpuStats.ThreadSample thread = top.get(i);
            telemetry.addData("Top thread " + (i + 1), "%s tid %d / %s%% / CPU %s / allowed %s",
                    value(thread.name), thread.tid, format(thread.cpuPercent), value(thread.lastCpu), value(thread.allowedCpuList));
        }
        ArrayList<CpuStats.ThreadSample> all = new ArrayList<>(snapshot.threads);
        all.sort(Comparator.comparingInt(thread -> thread.tid));
        int pages = Math.max(1, (all.size() + 5) / 6);
        threadPage = Math.floorMod(threadPage, pages);
        telemetry.addData("All app threads", "page %d/%d; dpad left/right; 100%% = one core", threadPage + 1, pages);
        for (int i = threadPage * 6; i < Math.min(all.size(), threadPage * 6 + 6); ++i) {
            CpuStats.ThreadSample thread = all.get(i);
            telemetry.addData("Thread " + thread.tid, "%s / %s%% / last CPU %s / allowed %s / mask %s / state %s / nice %s",
                    value(thread.name), format(thread.cpuPercent), value(thread.lastCpu), value(thread.allowedCpuList),
                    value(thread.allowedCpuMask), value(thread.state), value(thread.nice));
        }
        CpuStats.MemorySample memory = snapshot.memory;
        if (memory != null) {
            telemetry.addData("Memory MiB", "RSS %s / virtual %s / Java used %s / committed %s / max %s",
                    mib(memory.processRssBytes), mib(memory.processVirtualBytes), mib(memory.javaUsedBytes),
                    mib(memory.javaCommittedBytes), mib(memory.javaMaxBytes));
        }
        for (int i = 0; i < Math.min(4, snapshot.thermals.size()); ++i) {
            CpuStats.ThermalSample thermal = snapshot.thermals.get(i);
            telemetry.addData("Thermal " + thermal.name, "%s C (%s)", format(thermal.celsius), thermal.type);
        }
    }

    private void show_thread(String label, int tid, List<CpuStats.ThreadSample> threads) {
        if (tid <= 0) {
            telemetry.addData(label + " thread", "no active worker TID (stopped or starting)");
            return;
        }
        for (CpuStats.ThreadSample thread : threads) if (thread.tid == tid) {
            telemetry.addData(label + " thread", "tid %d / %s%% / last CPU %s / allowed %s / mask %s / state %s / priority %s / nice %s",
                    tid, format(thread.cpuPercent), value(thread.lastCpu), value(thread.allowedCpuList),
                    value(thread.allowedCpuMask), value(thread.state), value(thread.nativePriority), value(thread.nice));
            if (thread.detail != null && !thread.detail.isEmpty()) telemetry.addData(label + " detail", thread.detail);
            return;
        }
        telemetry.addData(label + " thread", "tid %d / not sampled yet or /proc unavailable", tid);
    }

    private static double hz_for_ms(double milliseconds) { return milliseconds > 0 ? 1000.0 / milliseconds : 0; }
    private static String value(Object value) { return value == null ? "unavailable" : value.toString(); }
    private static String format(Double value) { return value == null ? "unavailable" : String.format(Locale.US, "%.2f", value); }
    private static String mib(Long bytes) { return bytes == null ? "unavailable" : String.format(Locale.US, "%.1f", bytes / 1048576.0); }

    private static List<Pollen> synthetic_pollen() {
        double[][] points = {{38,36}, {44,41}, {35,45}, {47,33}, {108,112}, {114,106}, {103,104},
                {116,117}, {72,22}, {22,100}, {130,62}, {62,130}, {88,70}, {56,84}};
        ArrayList<Pollen> result = new ArrayList<>();
        for (int i = 0; i < points.length; ++i)
            result.add(new Pollen(i + 1, new VisionPose(points[i][0], points[i][1], 0), 1, 1));
        return result;
    }

    private static final class Sampler {
        private final CpuStats reader = new CpuStats();
        private volatile boolean running;
        private volatile CpuStats.Snapshot snapshot;
        private volatile String error = "";
        private volatile int tid = -1;
        private Thread thread;

        void start() {
            running = true;
            thread = new Thread(() -> {
                tid = Process.myTid();
                while (running) {
                    try { snapshot = reader.sample(); error = ""; }
                    catch (RuntimeException failure) { error = failure.toString(); }
                    try { Thread.sleep(500); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }, "FROG-CPU-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        boolean stop() {
            running = false;
            if (thread == null) return true;
            thread.interrupt();
            try { thread.join(250); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return !thread.isAlive();
        }
    }
}
