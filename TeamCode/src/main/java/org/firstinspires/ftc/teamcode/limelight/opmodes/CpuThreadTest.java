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
import org.firstinspires.ftc.teamcode.limelight.Pollen;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.ThreadAffinity;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/**
 * Disabled, stationary diagnostics: no motors, follower, intake or shooter are constructed.
 * Enable this annotation deliberately for a bench check. A requests one synthetic solve;
 * B cancels it; X toggles control-loop CPU 0 affinity; dpad left/right pages all app threads.
 * Android's original mask is restored. X resets loop timings after its setup commands finish.
 * Camera mapping uses a fixed SYNTHETIC pose, so none of this map is a real robot position.
 * Per-core CPU percentages are total system load; per-thread 100% means one occupied core.
 * /proc and sysfs are sampled at 2 Hz on a separate sleeping sampler, never in the loop.
 */
@Disabled
@TeleOp(name = "Limelight CPU thread test", group = "Limelight")
public final class CpuThreadTest extends LinearOpMode {
    private static final VisionPose SYNTHETIC_POSE = new VisionPose(72, 72, 0);
    private int threadPage;

    @Override public void runOpMode() throws InterruptedException {
        final int controlTid = Process.myTid();
        ThreadAffinity.Result controlBinding = null;
        boolean controlPinned = false;
        String controlDetail = "Android scheduler";
        ButineWorker planner = new ButineWorker();
        Limelight camera = new Limelight(hardwareMap);
        LimelightWorker vision = new LimelightWorker(camera, new PollenMap(camera));
        Sampler sampler = new Sampler();
        try {
            // Child threads inherit their creator's mask. Start them before pinning this loop.
            planner.start();
            vision.submit_pose(SYNTHETIC_POSE);
            vision.start();
            sampler.start();
            controlBinding = ThreadAffinity.bind_current_thread(0);
            controlPinned = controlBinding.applied;
            controlDetail = controlBinding.detail;
            telemetry.setMsTransmissionInterval(200);
            telemetry.addLine("Stationary CPU test: no drive hardware. Optional Limelight may be absent.");
            telemetry.addLine("Camera coordinates use fixed SYNTHETIC 72,72,0; do not drive from this map.");
            telemetry.addLine("A solve once | B cancel | X control CPU 0 pin | dpad L/R all thread pages");
            telemetry.update();
            waitForStart();
            long previousLoop = System.nanoTime();
            long nextTelemetry = previousLoop;
            double lastLoopMs = 0, maxLoopMs = 0, sumLoopMs = 0;
            long loopCount = 0;
            boolean previousA = false, previousB = false, previousX = false;
            boolean previousLeft = false, previousRight = false;
            long lastRequest = 0;
            while (opModeIsActive()) {
                long now = System.nanoTime();
                lastLoopMs = Math.max(0, (now - previousLoop) / 1e6);
                previousLoop = now;
                maxLoopMs = Math.max(maxLoopMs, lastLoopMs);
                sumLoopMs += lastLoopMs;
                ++loopCount;
                vision.submit_pose(SYNTHETIC_POSE);
                boolean a = gamepad1.a, b = gamepad1.b, x = gamepad1.x;
                if (a && !previousA) {
                    Butine.Params settings = new Butine.Params();
                    settings.remainingS = 30;
                    lastRequest = planner.request_plan(synthetic_pollen(), SYNTHETIC_POSE,
                            SYNTHETIC_POSE, settings, 1);
                }
                if (b && !previousB) planner.cancel_pending();
                if (x && !previousX) {
                    if (controlPinned) {
                        boolean restored = ThreadAffinity.restore_current_thread(controlBinding);
                        controlDetail = restored ? "Original Android CPU mask restored" : "Restore failed; inspect allowed CPUs";
                        if (restored) { controlPinned = false; controlBinding = null; }
                    } else {
                        // Restore an earlier failed/partial attempt before taking another baseline.
                        boolean restored = controlBinding == null
                                || ThreadAffinity.restore_current_thread(controlBinding);
                        if (restored) {
                            controlBinding = ThreadAffinity.bind_current_thread(0);
                            controlPinned = controlBinding.applied;
                            controlDetail = controlBinding.detail;
                        } else controlDetail = "Original mask restore failed; rebind refused; X retries restore";
                    }
                    // Affinity setup launches a subprocess. Exclude its one-time cost from steady-loop jitter.
                    now = System.nanoTime();
                    previousLoop = now;
                    nextTelemetry = now;
                    lastLoopMs = maxLoopMs = sumLoopMs = 0;
                    loopCount = 0;
                }
                boolean left = gamepad1.dpad_left, right = gamepad1.dpad_right;
                if (left && !previousLeft) --threadPage;
                if (right && !previousRight) ++threadPage;
                previousLeft = left;
                previousRight = right;
                previousA = a;
                previousB = b;
                previousX = x;
                if (now >= nextTelemetry) {
                    nextTelemetry = now + 200_000_000L;
                    telemetry.addLine("SYNTHETIC camera pose. CPU affinity is shared, not exclusive.");
                    telemetry.addData("Steady loop ms (reset by X)", "last %.3f / mean %.3f / max %.3f", lastLoopMs,
                            sumLoopMs / Math.max(1, loopCount), maxLoopMs);
                    telemetry.addData("Control placement", "%s; %s", controlPinned ? "verified CPU 0"
                            : controlBinding == null ? "original Android mask" : "unverified request", controlDetail);
                    ButineWorker.Stats plannerStats = planner.stats();
                    telemetry.addData("Butine", "request %d / completed %d / discarded %d; last %.3f ms / max %.3f ms",
                            lastRequest, plannerStats.completed, plannerStats.discarded,
                            plannerStats.lastSolveMs, plannerStats.maxSolveMs);
                    telemetry.addData("Butine placement", plannerStats.affinityDetail);
                    ButineWorker.Result result = planner.latest_result();
                    if (result != null) telemetry.addData("Synthetic plan", result.error != null ? result.error
                            : result.plan.expectedCollected + " pickups / " + result.plan.fittingRuns
                            + " runs / " + format(result.remaining_s()) + " s left");
                    vision.telemetry(telemetry);
                    CpuStats.Snapshot snapshot = sampler.snapshot;
                    if (!sampler.error.isEmpty()) telemetry.addData("CPU sampler error", sampler.error);
                    if (snapshot == null) telemetry.addData("CPU sampler", sampler.error.isEmpty() ? "warming up" : sampler.error);
                    else {
                        LimelightWorker.Stats visionStats = vision.stats();
                        show_stats(snapshot, controlTid, visionStats.running ? visionStats.tid : -1,
                                plannerStats.running ? plannerStats.tid : -1, sampler.running ? sampler.tid : -1);
                    }
                    telemetry.addLine("A solve | B cancel | X control pin + reset timing | dpad L/R thread pages");
                    telemetry.update();
                }
                idle();
            }
        } finally {
            try { if (!planner.stop()) RobotLog.ww("CpuThreadTest", "Butine solver still finishing after bounded stop; result invalidated"); }
            finally {
                try { if (!vision.stop()) RobotLog.ww("CpuThreadTest", "Limelight worker still finishing after bounded stop"); }
                finally {
                    try { if (!sampler.stop()) RobotLog.ww("CpuThreadTest", "CPU sampler still finishing after bounded stop"); }
                    finally { if (controlBinding != null && !ThreadAffinity.restore_current_thread(controlBinding))
                        RobotLog.ww("CpuThreadTest", "Could not restore original control-thread CPU mask"); }
                }
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
