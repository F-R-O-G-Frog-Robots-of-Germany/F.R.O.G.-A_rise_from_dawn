import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.LLStatus;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.teamcode.limelight.*;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real worker/camera/map code, injected transport only. Never included in the Android app. */
public final class LimelightWorkerAudit {
    private static int checks;
    private static final VisionPose POSE = new VisionPose(20, 20, 0);

    public static void main(String[] args) throws Exception {
        async_lifecycle_and_commands();
        stale_snapshot_and_blocked_stop();
        stale_odometry_and_mapping_pause();
        slow_health_preserves_capture_history();
        invalid_input_and_pipeline_history();
        invalid_epoch_cannot_replay_unconsumed_frame();
        concurrent_map_access();
        failed_init();
        async_runner();
        corrected_pose_starts_detector_history();
        System.out.println("Limelight worker audit checks passed: " + checks);
    }

    private static void async_lifecycle_and_commands() throws Exception {
        TestCamera device = new TestCamera();
        device.startGate = new CountDownLatch(1);
        Context context = new Context(device);
        try {
            require(context.worker.start(), "async start accepted");
            require(device.startEntered.await(2, TimeUnit.SECONDS), "camera init executes in worker");
            require(!context.worker.start(), "second owner cannot start");
            require(context.worker.request_pipeline(-1) == 0 && context.worker.request_pipeline(10) == 0,
                    "invalid pipeline requests rejected");
            long old = context.worker.request_pipeline(1);
            long newest = context.worker.request_pipeline(0);
            require(old > 0 && newest > old, "pipeline requests carry monotonic tokens");
            require(context.worker.request_snapshot("one") && !context.worker.request_snapshot("two"),
                    "only one capture may wait while camera init blocks");
            require(!context.worker.request_snapshot(null) && !context.worker.request_snapshot(""),
                    "invalid snapshot names rejected");
            for (int i = 0; i < 10000; i++) context.worker.submit_pose(new VisionPose(20 + i / 10000.0, 20, 0));
            Field count = LimelightWorker.class.getDeclaredField("poseCount");
            count.setAccessible(true);
            require(count.getInt(context.worker) <= 64, "fast robot input stays bounded");
            device.startGate.countDown();
            await(() -> context.worker.pipeline_applied(newest, 0), "coalesced latest pipeline applied");
            await(() -> "one".equals(device.snapshot), "queued snapshot captured");
            require(device.owner != Thread.currentThread() && !device.wrongOwner,
                    "all SDK methods stay on one worker, away from caller");
            require(context.worker.stats().loops > 0, "loop statistics published");
        } finally { device.release(); context.worker.stop(); }
        require(!context.worker.is_running(), "stop completes clean lifecycle");
        require(context.worker.request_pipeline(1) == 0 && !context.worker.request_snapshot("stopped"),
                "stopped worker rejects commands");
        require(context.worker.start(), "fully stopped camera may restart");
        await(() -> context.worker.stats().loops > 0 && context.worker.snapshot().running,
                "restarted worker runs");
        context.worker.stop();
    }

    private static void stale_snapshot_and_blocked_stop() throws Exception {
        TestCamera device = new TestCamera();
        Context context = new Context(device);
        context.camera.maxFrameAgeMs = 80;
        try {
            context.worker.start();
            context.worker.submit_pose(POSE);
            await(() -> context.worker.snapshot().has_target(), "fresh target published");
            LimelightWorker.Snapshot held = context.worker.snapshot();
            device.readGate = new CountDownLatch(1);
            require(device.readEntered.await(2, TimeUnit.SECONDS), "SDK read blocked for lifecycle test");
            Thread.sleep(120);
            require(!held.has_target() && held.get_tx() == 0 && held.get_pose() == null,
                    "held snapshot expires without further worker publications");
            int id = context.map.inject(new VisionPose(46, 20, 0));
            require(id >= 0 && context.map.remove(id), "map lock is never held across blocked SDK I/O");
            long started = System.nanoTime();
            require(!context.worker.stop(), "bounded stop reports blocked owner");
            require((System.nanoTime() - started) < 1_000_000_000L, "cleanup join remains bounded");
            require(!context.worker.snapshot().has_target(), "stop immediately invalidates cached evidence");
            require(!context.worker.start(), "restart cannot race an uninterruptible old SDK owner");
            device.readGate.countDown();
            await(() -> !context.worker.stats().running, "blocked owner eventually completes stop");
        } finally { device.release(); context.worker.stop(); }
        require(!device.wrongOwner, "stop also executes on original owner");
    }

    private static void stale_odometry_and_mapping_pause() throws Exception {
        TestCamera device = new TestCamera();
        device.frame = frame(1, true);
        Context context = new Context(device);
        try {
            context.worker.start();
            context.worker.submit_pose(POSE);
            await(() -> context.map.size() == 1, "detector maps to field target");
            Pollen pollen = context.map.snapshot().get(0);
            require(Math.abs(pollen.position.x - 46) < 1e-9 && Math.abs(pollen.position.y - 20) < 1e-9,
                    "worker uses actual pose20,20 plus calibrated26inch range");
            long sequence = context.worker.snapshot().frameSequence;
            Thread.sleep(140);
            device.frame = frame(2, false);
            Thread.sleep(40);
            require(context.map.size() == 1 && context.worker.snapshot().frameSequence == sequence,
                    "new empty image cannot clear map with odometry older100ms");
            context.worker.set_mapping_enabled(false);
            context.worker.submit_pose(POSE);
            await(() -> context.worker.snapshot().frameSequence > sequence, "current pose permits detector publication");
            require(context.map.size() == 1, "paused mapping preserves synthetic/bench targets");
            context.worker.set_mapping_enabled(true);
            await(() -> context.map.size() == 0, "resuming mapping consumes latest authoritative frame");
        } finally { device.release(); context.worker.stop(); }
    }

    private static void slow_health_preserves_capture_history() throws Exception {
        TestCamera device = new TestCamera();
        device.statusGate = new CountDownLatch(1);
        Context context = new Context(device);
        try {
            context.worker.start();
            require(device.statusEntered.await(2, TimeUnit.SECONDS), "slow health request starts on worker");
            long captureStamp = 0;
            for (int i = 0; i <= 30; i++) {
                if (i == 12) captureStamp = now_ms();
                context.worker.submit_pose(new VisionPose(20 + i, 20, 0));
                Thread.sleep(10);
            }
            LLResult delayed = frame(1, true);
            delayed.capture = now_ms() - captureStamp;
            device.frame = delayed;
            device.statusGate.countDown();
            await(() -> context.map.size() == 1, "frame received after slow health status maps successfully");
            require(Math.abs(context.map.snapshot().get(0).position.x - 58) < 1.5,
                    "capture pose32 plus range26 comes from buffered history, not latest pose50");
            Field count = Limelight.class.getDeclaredField("poseCount"); count.setAccessible(true);
            require(count.getInt(context.camera) >= 25, "slow status cannot discard robot-loop capture history");
        } finally { device.release(); context.worker.stop(); }
    }

    private static void invalid_input_and_pipeline_history() throws Exception {
        TestCamera device = new TestCamera();
        Context context = new Context(device);
        try {
            context.worker.start();
            context.worker.submit_pose(POSE);
            await(() -> context.worker.snapshot().frameSequence > 0, "initial frame has pose evidence");
            int id = context.map.inject(new VisionPose(46, 20, 0));
            context.worker.submit_pose(null);
            device.frame = frame(2, false);
            Thread.sleep(30);
            require(context.map.snapshot().get(0).id == id, "null odometry immediately disables map clearing");
            context.worker.submit_pose(new VisionPose(Double.NaN, 20, 0));
            device.frame = frame(3, false);
            Thread.sleep(30);
            require(context.map.size() == 1, "nonfinite odometry cannot reuse last valid sample");
            context.worker.submit_pose(new VisionPose(30, 20, 0));
            device.readGate = new CountDownLatch(1);
            device.blockReadPipeline = 1;
            long token = context.worker.request_pipeline(1);
            require(device.readEntered.await(2, TimeUnit.SECONDS), "switched pipeline blocks after trail reset");
            Field count = Limelight.class.getDeclaredField("poseCount"); count.setAccessible(true);
            require(count.getInt(context.camera) <= 1, "pipeline switch retains only newest odometry origin");
            require(!context.worker.pipeline_applied(token, 1), "switch request alone is not camera pipeline confirmation");
            LLResult tagPipeline = frame(4, false); tagPipeline.pipeline = 1; device.frame = tagPipeline;
            device.readGate.countDown();
            await(() -> context.worker.pipeline_applied(token, 1), "fresh actual pipeline confirms switch");
        } finally { device.release(); context.worker.stop(); }
        LimelightWorker invalid = new Context(new TestCamera()).worker;
        invalid.preferredCore = -2;
        boolean rejected = false;
        try { invalid.start(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "invalid preferred core fails before launching a worker");
    }

    private static void invalid_epoch_cannot_replay_unconsumed_frame() throws Exception {
        TestCamera device = new TestCamera();
        Context context = new Context(device);
        try {
            context.worker.start(); context.worker.submit_pose(POSE);
            await(() -> context.worker.snapshot().frameSequence > 0, "epoch race starts with valid frame");
            context.map.inject(new VisionPose(50, 20, 0));
            device.readGate = new CountDownLatch(1);
            require(device.readEntered.await(2, TimeUnit.SECONDS), "old-pose read blocks before invalidation");
            context.worker.submit_pose(null);
            device.frame = frame(2, true);
            device.readGate.countDown();
            await(() -> context.worker.snapshot().frameSequence >= 2, "old-pose frame completed while invalidated");
            context.worker.submit_pose(new VisionPose(30, 20, 0));
            Thread.sleep(40);
            require(context.map.size() == 1 && Math.abs(context.map.snapshot().get(0).position.x - 50) < 1e-9,
                    "new valid epoch cannot replay previously unconsumed old-pose frame46");
            device.frame = frame(3, true);
            await(() -> Math.abs(context.map.snapshot().get(0).position.x - 56) < 1e-9,
                    "next genuinely new image uses new30+26 origin56");
        } finally { device.release(); context.worker.stop(); }
    }

    private static long now_ms() throws Exception {
        java.lang.reflect.Method clock = VisionPose.class.getDeclaredMethod("now_ms");
        clock.setAccessible(true);
        return (Long) clock.invoke(null);
    }

    private static void concurrent_map_access() throws Exception {
        TestCamera device = new TestCamera();
        Context context = new Context(device);
        final Throwable[] failure = new Throwable[1];
        Thread mutation = new Thread(() -> {
            try {
                for (int i = 0; i < 500; i++) {
                    int id = context.map.inject(new VisionPose(80, 80 + i % 5, 0));
                    List<Pollen> snapshot = context.map.snapshot();
                    Set<Integer> ids = new HashSet<>();
                    for (Pollen pollen : snapshot) if (!ids.add(pollen.id)) throw new AssertionError("duplicate id");
                    if (snapshot.size() > 32) throw new AssertionError("capacity exceeded");
                    context.map.remove(id);
                    context.map.stats();
                }
            } catch (Throwable error) { failure[0] = error; }
        }, "audit-map-caller");
        try {
            context.worker.start();
            mutation.start();
            for (int i = 0; i < 30; i++) {
                context.worker.submit_pose(POSE);
                device.frame = frame(i + 1, i % 2 == 0);
                Thread.sleep(2);
            }
            mutation.join(3000);
            require(!mutation.isAlive() && failure[0] == null, "simultaneous detection and map mutations stay atomic");
            List<PollenMap.Track> view = context.map.tracks();
            boolean immutable = false;
            try { view.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable, "track snapshots do not expose worker mutation");
        } finally { device.release(); context.worker.stop(); }
    }

    private static void failed_init() throws Exception {
        TestCamera device = new TestCamera();
        device.failStart = true;
        Context context = new Context(device);
        context.worker.start();
        await(() -> context.worker.snapshot().status == Limelight.Status.INIT_FAILED, "failed camera init remains visible");
        await(() -> !context.worker.is_running(), "init failure releases owner");
        require(!context.worker.snapshot().error.isEmpty() && !context.worker.snapshot().connected,
                "missing/failed camera exposes error without main-thread SDK call");
        require(!device.wrongOwner, "failed init cleanup respects single owner");
    }

    private static void async_runner() throws Exception {
        TestCamera device = new TestCamera();
        Context context = new Context(device);
        TestDrive drive = new TestDrive();
        LimelightRunner runner = new LimelightRunner(context.worker, context.map, drive);
        try {
            require(runner.init(), "runner starts camera asynchronously");
            runner.update();
            await(() -> context.worker.snapshot().connected, "runner observes cached camera health");
            require(runner.relocalize(12, .05, 0), "relocalization queues camera switch");
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (runner.is_busy() && System.nanoTime() < deadline) { runner.update(); Thread.sleep(5); }
            require(!runner.is_busy() && context.worker.snapshot().pipeline == 0,
                    "look timeout confirms detector restore before completion");
            require(drive.wrongThread == false, "all drive interactions remain on caller thread");
            drive.fail = true;
            boolean thrown = false;
            try { runner.update(); } catch (IllegalStateException expected) { thrown = true; }
            require(thrown && !context.worker.is_running(), "drive failure requests nonblocking worker shutdown");
        } finally { device.release(); runner.stop(); }
    }

    private static void corrected_pose_starts_detector_history() throws Exception {
        TestCamera device = new TestCamera();
        LLResult tags = frame(2, false); tags.pipeline = 1; tags.tags = 1;
        tags.botpose = new org.firstinspires.ftc.robotcore.external.navigation.Pose3D(
                new org.firstinspires.ftc.robotcore.external.navigation.Position(
                        org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.METER,
                        (25 - 72) * .0254, (20 - 72) * .0254, 0, 0),
                new org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles(
                        org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES, 0, 0, 0, 0));
        device.tagFrame = tags;
        Context context = new Context(device);
        TestDrive drive = new TestDrive();
        LimelightRunner runner = new LimelightRunner(context.worker, context.map, drive);
        try {
            runner.init(); runner.update();
            await(() -> context.worker.snapshot().connected, "correction test camera ready");
            require(runner.relocalize(12, 1, 0), "correction test starts look");
            await(() -> context.worker.snapshot().get_pose() != null, "worker publishes tag correction");
            LLResult after = frame(3, true); after.capture = 50; device.frame = after;
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (runner.is_busy() && System.nanoTime() < deadline) { runner.update(); Thread.sleep(2); }
            require(runner.stats().applied == 1 && Math.abs(drive.position.x - 25) < 1e-9,
                    "tag fix applied only by caller drive");
            await(() -> context.map.size() == 1, "detector resumes after correction");
            require(Math.abs(context.map.snapshot().get(0).position.x - 51) < 1e-9,
                    "new camera epoch uses corrected25 plus range26, never previous20 origin");
            require(!drive.wrongThread, "corrected pose and follower calls stay on caller thread");
        } finally { device.release(); runner.stop(); }
    }

    private static final class Context {
        final Limelight camera;
        final PollenMap map;
        final LimelightWorker worker;
        Context(TestCamera device) {
            HardwareMap hardware = new HardwareMap(); hardware.device = device;
            camera = new Limelight(hardware); map = new PollenMap(camera);
            worker = new LimelightWorker(camera, map); worker.preferredCore = -1;
        }
    }

    private static final class TestDrive implements ButineRunner.Drive {
        final Thread caller = Thread.currentThread();
        boolean wrongThread, fail;
        VisionPose position = POSE;
        private void check() { wrongThread |= caller != Thread.currentThread(); if (fail) throw new IllegalStateException("drive failed"); }
        @Override public VisionPose pose() { check(); return position; }
        @Override public boolean is_busy() { check(); return false; }
        @Override public void follow(ButineRunner.Leg leg, double speedScale) { check(); }
        @Override public void stop() { check(); }
        @Override public void set_pose(VisionPose pose) { check(); position = pose; }
    }

    private static final class TestCamera extends Limelight3A {
        volatile Thread owner;
        volatile boolean wrongOwner;
        volatile LLResult frame = frame(1, false), tagFrame;
        volatile CountDownLatch startGate, readGate, statusGate;
        volatile int blockReadPipeline = -1;
        final CountDownLatch startEntered = new CountDownLatch(1), readEntered = new CountDownLatch(1),
                statusEntered = new CountDownLatch(1);
        private void check() { if (owner == null) owner = Thread.currentThread(); wrongOwner |= owner != Thread.currentThread(); }
        @Override public boolean pipelineSwitch(int index) { check(); return super.pipelineSwitch(index); }
        @Override public void setPollRateHz(int hz) { check(); super.setPollRateHz(hz); }
        @Override public void start() { check(); startEntered.countDown(); block(startGate); super.start(); }
        @Override public void stop() { check(); super.stop(); }
        @Override public boolean isConnected() { check(); return super.isConnected(); }
        @Override public LLStatus getStatus() {
            check(); statusEntered.countDown(); block(statusGate); return super.getStatus();
        }
        @Override public LLResult getLatestResult() {
            check(); CountDownLatch gate = readGate;
            if (gate != null && (blockReadPipeline < 0 || pipeline == blockReadPipeline)) {
                readEntered.countDown(); block(gate);
            }
            return pipeline == 1 && tagFrame != null ? tagFrame : frame;
        }
        @Override public boolean captureSnapshot(String name) { check(); return super.captureSnapshot(name); }
        void release() {
            if (startGate != null) startGate.countDown(); if (readGate != null) readGate.countDown();
            if (statusGate != null) statusGate.countDown();
        }
        private static void block(CountDownLatch gate) {
            if (gate == null) return;
            boolean done = false;
            while (!done) {
                try { gate.await(); done = true; }
                catch (InterruptedException deliberatelyUninterruptibleTransport) { }
            }
        }
    }

    private static LLResult frame(long timestamp, boolean pollen) {
        LLResult frame = new LLResult(); frame.ts = timestamp; frame.hub = timestamp;
        if (pollen) frame.detectors.add(new LLResultTypes.DetectorResult());
        return frame;
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        require(condition.getAsBoolean(), message);
    }
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}

