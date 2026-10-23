package org.firstinspires.ftc.teamcode.limelight;

import android.os.Debug;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * One camera owner with bounded input mailboxes and immutable output snapshots.
 * Configure the camera/calibration/map before start; never call its SDK-facing methods
 * elsewhere while this worker owns it. Drive, follower and telemetry stay on the OpMode thread.
 */
public final class LimelightWorker {
    private static final long LOOP_NANOS = 10_000_000L;
    private static final long HEALTH_NANOS = 250_000_000L;
    private static final int POSE_HISTORY = 64;
    private static final long POSE_BUCKET_MS = 10;

    /** Configure before start. CPU indices are zero based; -1 leaves Android scheduling enabled. */
    public int preferredCore = 1;

    public static final class Snapshot {
        public final Limelight.Status status;
        public final String error;
        public final boolean running, connected;
        public final int pipeline, mapTracks;
        public final long frameSequence, updatedNanos, pipelineAckToken;
        public final boolean pipelineAckSuccess;
        public final Limelight.Result result;
        public final List<Pollen> pollen;
        private final VisionPose pose;
        private final boolean targetValid, pollenValid;
        private final long resultRemainingNanos, pollenRemainingNanos;

        private Snapshot(Limelight.Status status, String error, boolean running,
                         boolean connected, int pipeline, int mapTracks, long frameSequence,
                         long updatedNanos, long pipelineAckToken, boolean pipelineAckSuccess,
                         Limelight.Result result, List<Pollen> pollen, VisionPose pose,
                         boolean targetValid, boolean pollenValid,
                         long resultRemainingNanos, long pollenRemainingNanos) {
            this.status = status;
            this.error = error;
            this.running = running;
            this.connected = connected;
            this.pipeline = pipeline;
            this.mapTracks = mapTracks;
            this.frameSequence = frameSequence;
            this.updatedNanos = updatedNanos;
            this.pipelineAckToken = pipelineAckToken;
            this.pipelineAckSuccess = pipelineAckSuccess;
            this.result = result;
            this.pollen = pollen;
            this.pose = pose;
            this.targetValid = targetValid;
            this.pollenValid = pollenValid;
            this.resultRemainingNanos = resultRemainingNanos;
            this.pollenRemainingNanos = pollenRemainingNanos;
        }

        private boolean fresh(long remaining) {
            return running && remaining >= 0 && System.nanoTime() - updatedNanos <= remaining;
        }

        /** A held snapshot expires even if SDK I/O stalls and no newer snapshot arrives. */
        public boolean has_target() { return targetValid && fresh(resultRemainingNanos); }
        public boolean has_pollen_frame() { return pollenValid && fresh(pollenRemainingNanos); }
        public boolean has_pipeline_frame(int index) {
            return pipeline == index && result.pipelineIndex == index && fresh(resultRemainingNanos);
        }
        public VisionPose get_pose() { return fresh(resultRemainingNanos) ? pose : null; }
        public double get_tx() { return has_target() ? result.tx : 0; }
        public double get_ty() { return has_target() ? result.ty : 0; }
        public double get_ta() { return has_target() ? result.ta : 0; }

        private Snapshot stopped() {
            return new Snapshot(status, error, false, false, pipeline, mapTracks, frameSequence,
                    System.nanoTime(), pipelineAckToken, pipelineAckSuccess, result,
                    Collections.<Pollen>emptyList(), null, false, false, -1, -1);
        }
    }

    public static final class Stats {
        public final int tid, cpuCore;
        public final boolean affinityApplied, running;
        public final String affinityDetail;
        public final long loops, overruns;
        public final double wallMs, lastLoopMs, maxLoopMs, lastCpuMs, totalCpuMs;

        private Stats(int tid, int cpuCore, boolean affinityApplied, boolean running,
                      String affinityDetail, long loops, long overruns, double wallMs,
                      double lastLoopMs, double maxLoopMs, double lastCpuMs, double totalCpuMs) {
            this.tid = tid;
            this.cpuCore = cpuCore;
            this.affinityApplied = affinityApplied;
            this.running = running;
            this.affinityDetail = affinityDetail;
            this.loops = loops;
            this.overruns = overruns;
            this.wallMs = wallMs;
            this.lastLoopMs = lastLoopMs;
            this.maxLoopMs = maxLoopMs;
            this.lastCpuMs = lastCpuMs;
            this.totalCpuMs = totalCpuMs;
        }
    }

    private final Limelight camera;
    private final PollenMap map;
    private final Object lifecycleLock = new Object(), inputLock = new Object();
    private final VisionPose[] poses = new VisionPose[POSE_HISTORY];
    private final long[] poseStamps = new long[POSE_HISTORY];
    private int poseNext, poseCount;
    private long poseBucket = -1;
    private volatile VisionPose latestPose;
    private volatile long latestPoseStamp = -1;
    private volatile boolean stopRequested, mappingEnabled = true;
    private long poseEpoch;
    private long ingestedPoseEpoch = -1;
    private volatile Thread thread;
    private volatile Snapshot latest;
    private volatile Stats statistics = new Stats(-1, -1, false, false, "not started",
            0, 0, 0, 0, 0, -1, -1);
    private long nextPipelineToken, pendingPipelineToken, pipelineAckToken;
    private int pendingPipeline;
    private boolean pipelineAckSuccess;
    private volatile Limelight.Status failureStatus;
    private String pendingSnapshot;
    private String affinityRestoreError = "";

    public LimelightWorker(Limelight camera, PollenMap map) {
        if (camera == null || map == null) throw new IllegalArgumentException("camera and map are required");
        this.camera = camera;
        this.map = map;
        latest = new Snapshot(Limelight.Status.NOT_INITIALISED, "", false, false,
                Limelight.DETECTOR_PIPELINE, map.size(), 0, System.nanoTime(), 0, false,
                camera.get_result(), Collections.<Pollen>emptyList(), null, false, false, -1, -1);
    }

    /** Starts asynchronously: true means a worker launched; inspect snapshot().status for init outcome. */
    public boolean start() {
        synchronized (lifecycleLock) {
            // A blocked old SDK request retains ownership, including while stop is pending.
            if (thread != null) return false;
            if (preferredCore < -1 || preferredCore > 1023) {
                throw new IllegalArgumentException("preferredCore must be -1 or 0..1023");
            }
            stopRequested = false;
            failureStatus = null;
            affinityRestoreError = "";
            latest = latest.stopped();
            synchronized (inputLock) {
                poseCount = poseNext = 0;
                poseBucket = -1;
                ingestedPoseEpoch = -1;
                latestPose = null;
                latestPoseStamp = -1;
                pendingPipelineToken = pipelineAckToken = 0;
                pendingSnapshot = null;
                pipelineAckSuccess = false;
            }
            Thread created = new Thread(new Runnable() {
                @Override public void run() { run_camera(); }
            }, "FROG-Limelight");
            created.setDaemon(true);
            thread = created;
            try { created.start(); }
            catch (RuntimeException exception) { thread = null; stopRequested = true; throw exception; }
            return true;
        }
    }

    /** Submit actual localized odometry once per loop; invalid input immediately disables map evidence. */
    public void submit_pose(VisionPose pose) {
        if (pose == null || !pose.is_finite()) {
            // Same monitor as the evidence write: no frame can sweep the map after this returns.
            synchronized (map) {
                synchronized (inputLock) {
                    latestPose = null;
                    latestPoseStamp = -1;
                    poseCount = poseNext = 0;
                    poseBucket = -1;
                    poseEpoch++;
                }
            }
            return;
        }
        long stamp = VisionPose.now_ms();
        synchronized (inputLock) {
            long bucket = stamp / POSE_BUCKET_MS;
            if (poseCount > 0 && bucket == poseBucket) {
                int index = (poseNext + POSE_HISTORY - 1) % POSE_HISTORY;
                poses[index] = pose;
                poseStamps[index] = stamp;
            } else {
                poses[poseNext] = pose;
                poseStamps[poseNext] = stamp;
                poseNext = (poseNext + 1) % POSE_HISTORY;
                poseCount = Math.min(POSE_HISTORY, poseCount + 1);
                poseBucket = bucket;
            }
            latestPose = pose;
            latestPoseStamp = stamp;
        }
    }

    /** Pause before injecting synthetic bench targets so live empty frames cannot clear them. */
    public void set_mapping_enabled(boolean enabled) {
        synchronized (map) { mappingEnabled = enabled; }
    }
    public boolean mapping_enabled() { return mappingEnabled; }

    /** Latest request wins; returns a nonzero acknowledgement token, or zero if not accepted. */
    public long request_pipeline(int index) {
        if (index < 0 || index > 9 || !is_running()) return 0;
        synchronized (inputLock) {
            if (stopRequested) return 0;
            pendingPipeline = index;
            pendingPipelineToken = ++nextPipelineToken;
            return pendingPipelineToken;
        }
    }

    public boolean pipeline_applied(long token, int index) {
        Snapshot view = latest;
        return token > 0 && view.running && view.pipelineAckToken >= token
                && view.pipelineAckSuccess && view.has_pipeline_frame(index);
    }

    /** At most one snapshot waits; capture/HTTP I/O happens on the worker. */
    public boolean request_snapshot(String name) {
        if (name == null || name.isEmpty() || !is_running()) return false;
        synchronized (inputLock) {
            if (stopRequested || pendingSnapshot != null) return false;
            pendingSnapshot = name;
            return true;
        }
    }

    public Snapshot snapshot() { return latest; }
    public Stats stats() { return statistics; }
    public boolean is_running() { return thread != null && !stopRequested; }
    /** Retains ownership through requested stop, blocked SDK calls and final camera shutdown. */
    public boolean has_owner() { return thread != null; }

    /** Nonblocking stop used on loop failure. It never steals camera ownership from blocked SDK I/O. */
    public void request_stop() { request_stop_owner(); }

    private Thread request_stop_owner() {
        Thread current;
        synchronized (lifecycleLock) {
            stopRequested = true;
            latest = latest.stopped();
            current = thread;
        }
        if (current != null) current.interrupt();
        return current;
    }

    /** Cleanup only: waits at most 500 ms. false means the old owner must finish before restart. */
    public boolean stop() {
        Thread current = request_stop_owner();
        if (current != null && current != Thread.currentThread()) {
            try { current.join(500); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        return thread == null;
    }

    private void ingest_poses() {
        VisionPose[] copy;
        long[] stamps;
        long epoch;
        synchronized (inputLock) {
            epoch = poseEpoch;
            copy = new VisionPose[poseCount];
            stamps = new long[poseCount];
            for (int i = 0; i < poseCount; i++) {
                int index = (poseNext - poseCount + i + POSE_HISTORY) % POSE_HISTORY;
                copy[i] = poses[index];
                stamps[i] = poseStamps[index];
            }
            poseCount = 0;
        }
        if (epoch != ingestedPoseEpoch) {
            camera.clear_pollen();
            camera.clear_pose_history();
            ingestedPoseEpoch = epoch;
        }
        for (int i = 0; i < copy.length; i++) camera.record_pose(stamps[i], copy[i]);
    }

    private void reset_pending_trail() {
        synchronized (inputLock) {
            poseCount = poseNext = 0;
            poseBucket = -1;
            // A pose correction may already be queued; retain just that newest origin.
            if (latestPose != null && latestPose.is_finite() && latestPoseStamp >= 0) {
                poses[0] = latestPose;
                poseStamps[0] = latestPoseStamp;
                poseCount = poseNext = 1;
                poseBucket = latestPoseStamp / POSE_BUCKET_MS;
            }
        }
    }

    private void run_camera() {
        ThreadAffinity.Result affinity = null;
        long loops = 0, overruns = 0, maxLoop = 0, totalCpu = -1;
        long started = System.nanoTime(), healthAt = 0;
        boolean connected = false;
        String error = "";
        try {
            affinity = ThreadAffinity.bind_current_thread(preferredCore);
            statistics = stats(affinity, !stopRequested, 0, 0, started, 0, 0, -1, -1);
            if (stopRequested) return;
            if (!camera.init()) {
                failureStatus = Limelight.Status.INIT_FAILED;
                publish(false, false, camera.get_error());
                return;
            }
            while (!stopRequested) {
                long loopStart = System.nanoTime(), cpuStart = cpu_nanos();
                try {
                    long token;
                    int pipeline;
                    String snapshotName;
                    synchronized (inputLock) {
                        token = pendingPipelineToken;
                        pipeline = pendingPipeline;
                        pendingPipelineToken = 0;
                        snapshotName = pendingSnapshot;
                        pendingSnapshot = null;
                    }
                    if (token != 0) {
                        int previousPipeline = camera.pipeline();
                        pipelineAckSuccess = camera.set_pipeline(pipeline);
                        if (pipelineAckSuccess && previousPipeline != pipeline) reset_pending_trail();
                        pipelineAckToken = token;
                        if (!pipelineAckSuccess) error = "pipeline " + pipeline + " request failed";
                    }
                    if (stopRequested) break;
                    if (loopStart >= healthAt) {
                        camera.update_health();
                        connected = camera.is_connected();
                        healthAt = System.nanoTime() + HEALTH_NANOS;
                    }
                    if (stopRequested) break;
                    // Ingest after potentially slow HTTP/status work, preserving original timestamps.
                    ingest_poses();
                    VisionPose pose;
                    long stamp, epoch;
                    synchronized (inputLock) { pose = latestPose; stamp = latestPoseStamp; epoch = poseEpoch; }
                    camera.update_sampled(pose, stamp);
                    synchronized (map) {
                        boolean inputValid;
                        synchronized (inputLock) {
                            inputValid = epoch == poseEpoch && epoch == ingestedPoseEpoch && latestPose != null && latestPose.is_finite()
                                    && latestPoseStamp >= 0
                                    && VisionPose.now_ms() - latestPoseStamp <= 100;
                        }
                        if (!stopRequested && mappingEnabled && inputValid) map.update(camera);
                    }
                    if (!stopRequested && snapshotName != null && !camera.capture_snapshot(snapshotName)) {
                        error = "snapshot capture failed: " + snapshotName;
                    }
                    publish(!stopRequested, connected, error);
                } catch (RuntimeException exception) {
                    error = exception.toString();
                    publish(!stopRequested, connected, error);
                }
                long elapsed = System.nanoTime() - loopStart;
                long cpuEnd = cpu_nanos();
                long cpu = cpuStart >= 0 && cpuEnd >= cpuStart ? cpuEnd - cpuStart : -1;
                if (cpu >= 0) totalCpu = Math.max(0, totalCpu) + cpu;
                maxLoop = Math.max(maxLoop, elapsed);
                loops++;
                if (elapsed > LOOP_NANOS) overruns++;
                statistics = stats(affinity, !stopRequested, loops, overruns, started,
                        elapsed, maxLoop, cpu, totalCpu);
                // No catch-up queue: every iteration schedules at most one new poll.
                if (!stopRequested && elapsed < LOOP_NANOS) LockSupport.parkNanos(LOOP_NANOS - elapsed);
                if (!stopRequested) Thread.interrupted();
            }
        } catch (RuntimeException exception) {
            error = exception.toString();
            publish(false, false, error);
        } finally {
            // stop(), including a blocked stop(), is always executed by the original camera owner.
            try { camera.stop(); }
            finally {
                if (affinity != null && !ThreadAffinity.restore_current_thread(affinity)) {
                    affinityRestoreError = "; original affinity restore failed";
                }
                publish(false, false, error);
                statistics = stats(affinity, false, loops, overruns, started, 0, maxLoop,
                        totalCpu < 0 ? -1 : 0, totalCpu);
                synchronized (lifecycleLock) { thread = null; }
            }
        }
    }

    private Stats stats(ThreadAffinity.Result affinity, boolean running, long loops, long overruns,
                        long started, long elapsed, long maxLoop, long cpu, long totalCpu) {
        return new Stats(affinity == null ? -1 : affinity.tid,
                affinity != null && affinity.applied ? affinity.core : -1,
                affinity != null && affinity.applied, running,
                (affinity == null ? "affinity not attempted" : affinity.detail) + affinityRestoreError,
                loops, overruns, (System.nanoTime() - started) / 1e6,
                elapsed / 1e6, maxLoop / 1e6, cpu < 0 ? -1 : cpu / 1e6,
                totalCpu < 0 ? -1 : totalCpu / 1e6);
    }

    private void publish(boolean running, boolean connected, String workerError) {
        String cameraError = camera.get_error();
        String error = workerError.isEmpty() ? cameraError : workerError;
        boolean inputValid;
        synchronized (inputLock) {
            inputValid = poseEpoch == ingestedPoseEpoch && latestPose != null
                    && latestPose.is_finite() && latestPoseStamp >= 0
                    && VisionPose.now_ms() - latestPoseStamp <= 100;
        }
        latest = new Snapshot(failureStatus == null ? camera.status() : failureStatus,
                error, running && !stopRequested,
                connected && !stopRequested, camera.pipeline(), map.size(), camera.pollen_frame_sequence(),
                System.nanoTime(), pipelineAckToken, pipelineAckSuccess, camera.get_result(),
                camera.get_pollen(), camera.get_pose(), camera.has_target(), inputValid && camera.has_pollen_frame(),
                milliseconds_to_nanos(camera.result_remaining_ms()),
                milliseconds_to_nanos(camera.pollen_remaining_ms()));
    }

    private static long milliseconds_to_nanos(long value) {
        if (value < 0) return -1;
        return value > Long.MAX_VALUE / 1_000_000 ? Long.MAX_VALUE : value * 1_000_000;
    }

    private static long cpu_nanos() {
        try { return Debug.threadCpuTimeNanos(); }
        catch (RuntimeException | LinkageError unavailable) { return -1; }
    }

    public void telemetry(Telemetry telemetry) {
        if (telemetry == null) return;
        Snapshot view = snapshot();
        Stats stats = stats();
        telemetry.addData("Limelight worker", "%s connected=%s pipeline=%d TID=%d CPU=%d",
                view.status, view.connected, view.pipeline, stats.tid, stats.cpuCore);
        telemetry.addData("Limelight loop ms", "last=%.2f max=%.2f overruns=%d",
                stats.lastLoopMs, stats.maxLoopMs, stats.overruns);
        telemetry.addData("Limelight thread CPU ms", stats.lastCpuMs < 0
                ? "unavailable" : String.format(java.util.Locale.US, "%.2f", stats.lastCpuMs));
        telemetry.addData("Limelight affinity", stats.affinityDetail);
        telemetry.addData("Limelight frame", "target=%s pollen=%d tracks=%d sequence=%d",
                view.has_target(), view.pollen.size(), view.mapTracks, view.frameSequence);
        telemetry.addData("Limelight health", "temp=%.1f CPU=%.1f FPS=%.1f",
                view.result.temp, view.result.cpu, view.result.fps);
        if (!view.error.isEmpty()) telemetry.addData("Limelight error", view.error);
    }
}
