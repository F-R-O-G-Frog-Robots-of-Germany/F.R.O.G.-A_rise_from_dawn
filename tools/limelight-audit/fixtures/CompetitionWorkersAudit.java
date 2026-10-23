import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.limelight.*;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Actual competition facade/workers/runners; only SDK camera transport is replaced. */
public final class CompetitionWorkersAudit {
    private static int checks;
    private static final VisionPose START = new VisionPose(20, 20, 0);

    public static void main(String[] args) throws Exception {
        disabled_is_optional();
        one_start_and_async_init();
        failed_init_preserves_static_drive();
        collection_arbitration_and_invalid_pose();
        cancelled_correction_cannot_apply_later_fix();
        stale_vision_rejects_and_cancels_collection();
        runner_failure_is_contained();
        blocked_stop_does_not_touch_drive();
        cross_facade_camera_ownership();
        finishing_planner_retains_facade_lease();
        pose_origin_changes_invalidate_map();
        accepted_correction_invalidates_old_origin();
        System.out.println("Competition worker audit checks passed: " + checks);
    }

    private static void disabled_is_optional() {
        CompetitionWorkers workers = new CompetitionWorkers(null, null, false, false);
        require(workers.camera() == null && workers.map() == null && workers.vision() == null
                && workers.planner() == null, "disabled mode constructs no camera/map/workers");
        require(workers.start() && !workers.start(), "disabled start remains a single lifecycle attempt");
        workers.update(); workers.cancel_drive();
        require(!workers.ready() && !workers.owns_drive()
                && !workers.request_collection(START, parameters(), 1)
                && !workers.request_relocalization(), "disabled assistance cannot claim drive");
        workers.request_stop();
        require(workers.stop(), "disabled shutdown is safe");
    }

    private static void one_start_and_async_init() throws Exception {
        Session s = new Session(false);
        s.device.startGate = new CountDownLatch(1);
        s.map.inject(new VisionPose(5, 5, 0));
        try {
            require(s.workers.start(), "vision owner launch accepted");
            require(s.device.startEntered.await(2, TimeUnit.SECONDS), "camera initialization runs asynchronously");
            require(s.workers.vision().stats().affinityDetail.equals("not started"),
                    "camera SDK startup happens before binding its caller worker");
            require(s.map.size() == 0, "map initialized before camera ownership begins");
            require(!s.workers.ready() && !s.workers.owns_drive(), "launch alone is not camera readiness or motion");
            int id = s.map.inject(new VisionPose(5, 5, 0));
            require(!s.workers.start() && s.map.size() == 1 && s.map.snapshot().get(0).id == id,
                    "duplicate start does not clear map or launch another owner");
            s.device.release();
            service_until(s, () -> s.workers.ready(), "camera ready only after actual caller pose updates");
            require(s.device.started == 1 && s.drive.stops == 0 && s.drive.dispatches == 0,
                    "exactly one camera start, no implicit collection or motor commands");
        } finally { s.close(); }
        require(!s.device.wrongOwner && !s.drive.wrongThread,
                "all SDK work stays on camera thread and all drive work on caller");
    }

    private static void failed_init_preserves_static_drive() throws Exception {
        Session s = new Session(true); s.device.failStart = true; s.drive.busy = true;
        try {
            require(s.workers.start(), "failed camera launch is initially asynchronous");
            await(() -> s.workers.vision().snapshot().status == Limelight.Status.INIT_FAILED,
                    "camera initialization failure is observable");
            s.workers.update(); s.workers.cancel_drive();
            require(!s.workers.ready() && !s.workers.request_collection(START, parameters(), 1)
                    && !s.workers.request_relocalization(), "failed camera cannot assist");
            require(s.drive.busy && s.drive.stops == 0, "camera failure leaves independent static path running");
        } finally { s.close(); }
    }

    private static void collection_arbitration_and_invalid_pose() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            s.drive.busy = true;
            require(!s.workers.request_collection(START, parameters(), 1) && s.drive.stops == 0,
                    "static path cannot overlap collection and rejection leaves it alone");
            s.drive.busy = false;
            require(s.workers.request_collection(START, parameters(), 1) && s.workers.owns_drive(),
                    "explicit collection owns drive while planning");
            require(!s.workers.request_relocalization(), "collection excludes correction");
            s.workers.cancel_drive();
            s.workers.update();
            require(!s.workers.owns_drive() && s.drive.dispatches == 0,
                    "manual override invalidates plan before any late dispatch");
            require(s.workers.request_collection(START, parameters(), 1), "collection can be requested again");
            s.drive.position = null; s.workers.update();
            require(!s.workers.owns_drive() && s.drive.dispatches == 0,
                    "invalid localization cancels active collection without dispatch");
            Field latestPose = LimelightWorker.class.getDeclaredField("latestPose"); latestPose.setAccessible(true);
            require(latestPose.get(s.workers.vision()) == null, "invalid pose explicitly invalidates vision input");
            require(!s.workers.ready(), "pre-invalidation worker snapshot cannot advertise readiness");
            int stopped = s.drive.stops; s.drive.busy = true; s.workers.update();
            require(s.drive.stops == stopped && s.drive.busy,
                    "invalid pose while assistance idle does not interrupt static/manual drive");
            s.drive.position = START; s.drive.busy = false;
            s.device.next_frame();
            service_until(s, () -> s.workers.ready(), "fresh localization restores detector readiness");
            require(s.workers.request_collection(START, parameters(), 1), "collection active before shutdown");
            stopped = s.drive.stops; s.workers.request_stop(); s.workers.update();
            require(s.drive.stops == stopped && !s.workers.owns_drive() && s.drive.dispatches == 0,
                    "nonblocking shutdown invalidates requests without motor commands");
        } finally { s.close(); }
    }

    private static void cancelled_correction_cannot_apply_later_fix() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            require(s.workers.request_relocalization() && s.workers.owns_drive(),
                    "explicit stationary correction owns drive");
            require(!s.workers.request_collection(START, parameters(), 1), "correction excludes collection");
            await(() -> s.workers.vision().snapshot().get_pose() != null, "tag fix waits in worker publication");
            s.workers.cancel_drive();
            service_until(s, () -> s.workers.ready(), "cancel queues detector restore without waiting");
            require(s.drive.applied == 0 && !s.workers.owns_drive(),
                    "a fix published before cancellation cannot apply afterward");
        } finally { s.close(); }
    }

    private static void stale_vision_rejects_and_cancels_collection() throws Exception {
        Session s = new Session(true);
        try {
            s.camera.maxFrameAgeMs = 40;
            s.start_ready();
            require(s.workers.request_collection(START, parameters(), 1), "collection starts with fresh frame");
            s.device.readGate = new CountDownLatch(1);
            require(s.device.readEntered.await(2, TimeUnit.SECONDS), "camera read blocks for staleness");
            Thread.sleep(70); s.workers.update();
            require(!s.workers.ready() && !s.workers.owns_drive() && s.drive.dispatches == 0,
                    "expired held snapshot cancels collection before planner can dispatch");
            require(s.map.size() > 0 && !s.workers.request_collection(START, parameters(), 1),
                    "retained historical tracks cannot bypass fresh-frame request gate");
        } finally { s.close(); }
    }

    private static void blocked_stop_does_not_touch_drive() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            s.device.readGate = new CountDownLatch(1);
            require(s.device.readEntered.await(2, TimeUnit.SECONDS), "blocked owner enters SDK read");
            int stops = s.drive.stops;
            long at = System.nanoTime(); s.workers.request_stop();
            require(System.nanoTime() - at < 100_000_000L && s.drive.stops == stops,
                    "stop request does not wait for camera or touch motors");
            at = System.nanoTime();
            require(!s.workers.stop() && System.nanoTime() - at < 1_000_000_000L,
                    "cleanup reports blocked owner with bounded joins");
            require(!s.workers.start() && s.device.started == 1,
                    "competition facade cannot replace a still-owned camera");
        } finally { s.close(); }
    }

    private static void runner_failure_is_contained() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            s.drive.failFollow = true;
            require(s.workers.request_collection(START, parameters(), 1), "faulting collection starts asynchronously");
            service_until(s, () -> !s.workers.owns_drive(), "runner exception is contained by competition service");
            require(s.drive.dispatches == 1 && !s.workers.ready()
                    && s.workers.detail().contains("failed"), "faulted assistance releases drive and reports failure");
            int stops = s.drive.stops;
            s.drive.busy = true; s.workers.update(); s.workers.cancel_drive();
            require(s.drive.stops == stops && s.drive.busy,
                    "manual/static fallback remains available after runner failure");
        } finally { s.close(); }
    }

    private static void cross_facade_camera_ownership() throws Exception {
        Session first = new Session(true), denied = new Session(false, first.device);
        Session next = null, deniedAgain = null;
        try {
            first.start_ready();
            first.device.readGate = new CountDownLatch(1);
            require(first.device.readEntered.await(2, TimeUnit.SECONDS), "old competition camera enters blocked SDK read");
            first.workers.request_stop();
            require(!first.workers.stop() && first.workers.vision().has_owner()
                    && !first.workers.vision().is_running(), "requested stop retains blocked camera ownership");
            require(!denied.workers.start() && first.device.started == 1,
                    "another facade cannot start the same SDK camera while old owner is alive");
            CompetitionWorkers disabled = new CompetitionWorkers(null, null, false, false);
            require(disabled.start() && disabled.stop(), "disabled facade needs no shared worker lease");
            first.device.release();
            await(() -> !first.workers.vision().has_owner() && !first.workers.planner().has_owner(),
                    "old owner releases camera only after final shutdown finishes");
            next = new Session(false, first.device); next.start_ready();
            require(first.device.started == 2, "fresh facade starts after old owner actually exits");
            first.workers.stop();
            deniedAgain = new Session(false, first.device);
            require(!deniedAgain.workers.start() && first.device.started == 2,
                    "late cleanup from an old facade cannot release a newer facade lease");
        } finally {
            first.close(); denied.close();
            if (next != null) next.close(); if (deniedAgain != null) deniedAgain.close();
        }
    }

    private static void finishing_planner_retains_facade_lease() throws Exception {
        Session first = new Session(true), denied = new Session(false);
        CountDownLatch solveFinishes = new CountDownLatch(1);
        Thread finishing = new Thread(() -> Device.block(solveFinishes), "audit-finishing-solver");
        try {
            first.start_ready(); first.workers.request_stop();
            require(first.workers.vision().stop() && first.workers.planner().stop(),
                    "initial worker owners finish before injecting solver shutdown phase");
            // Model a finite solve ignoring interruption, without relying on machine-dependent solve duration.
            Field thread = ButineWorker.class.getDeclaredField("thread"); thread.setAccessible(true);
            thread.set(first.workers.planner(), finishing); finishing.start();
            require(!first.workers.planner().is_running() && first.workers.planner().has_owner(),
                    "cancelled planner with a live thread still owns its computation");
            require(!first.workers.stop() && !denied.workers.start(),
                    "a finishing planner alone retains competition lease after camera shutdown");
            solveFinishes.countDown(); finishing.join(2000);
            require(!first.workers.planner().has_owner(), "planner ownership ends when actual thread exits");
        } finally { solveFinishes.countDown(); finishing.join(2000); first.close(); denied.close(); }
    }

    private static void pose_origin_changes_invalidate_map() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            require(s.workers.request_collection(START, parameters(), 1), "collection active before origin reset");
            s.drive.position = new VisionPose(30, 20, 0); s.workers.pose_changed();
            require(!s.workers.owns_drive() && !s.workers.ready() && s.map.size() == 0,
                    "explicit origin reset cancels collection and clears old-origin target evidence");
            require(!s.workers.request_collection(START, parameters(), 1), "cached pre-reset frame cannot start collection");
            s.device.next_frame();
            service_until(s, () -> s.workers.ready() && s.map.size() > 0, "new actual frame rebuilds map in reset origin");
            require(Math.abs(s.map.snapshot().get(0).position.x - 56) < 1e-9,
                    "recovered map uses new30inch pose plus26inch observation");
        } finally { s.close(); }
    }

    private static void accepted_correction_invalidates_old_origin() throws Exception {
        Session s = new Session(true);
        try {
            s.start_ready();
            require(s.workers.request_relocalization(), "accepted-fix test starts correction");
            await(() -> s.workers.vision().snapshot().get_pose() != null, "accepted-fix tag published");
            s.device.readGate = new CountDownLatch(1); s.device.blockReadPipeline = 0;
            s.workers.update();
            require(s.drive.applied == 1 && s.map.size() == 0 && !s.workers.ready(),
                    "accepted AprilTag correction clears old origin before new detector evidence");
            require(s.device.readEntered.await(2, TimeUnit.SECONDS), "restored detector read waits after correction");
            s.device.next_frame(); s.device.release();
            service_until(s, () -> s.workers.ready() && !s.workers.owns_drive(),
                    "new detector publication restores readiness after correction settling");
            require(s.map.size() > 0 && Math.abs(s.map.snapshot().get(0).position.x - 51) < 1e-9,
                    "new mapped target uses corrected25inch origin plus26inch observation");
        } finally { s.close(); }
    }

    private static final class Session {
        final Device device;
        final Drive drive = new Drive();
        final Limelight camera;
        final PollenMap map;
        final CompetitionWorkers workers;
        Session(boolean planning) { this(planning, new Device()); }
        Session(boolean planning, Device device) {
            this.device = device;
            HardwareMap hardware = new HardwareMap(); hardware.device = device;
            camera = new Limelight(hardware); map = new PollenMap(camera);
            workers = new CompetitionWorkers(camera, map, drive, planning);
            workers.vision().preferredCore = -1;
            if (workers.planner() != null) workers.planner().preferredCore = -1;
        }
        void start_ready() throws Exception {
            require(workers.start(), "session starts");
            service_until(this, () -> workers.ready() && map.size() > 0, "fresh detector map available");
        }
        void close() { device.release(); workers.stop(); }
    }

    private static final class Drive implements ButineRunner.Drive {
        final Thread caller = Thread.currentThread();
        VisionPose position = START;
        boolean busy, wrongThread, failFollow;
        int stops, dispatches, applied;
        void check() { wrongThread |= Thread.currentThread() != caller; }
        public VisionPose pose() { check(); return position; }
        public boolean is_busy() { check(); return busy; }
        public void follow(ButineRunner.Leg leg, double scale) {
            check(); dispatches++;
            if (failFollow) throw new IllegalStateException("injected dispatch failure");
            busy = true;
        }
        public void stop() { check(); stops++; busy = false; }
        public void set_pose(VisionPose fix) { check(); position = fix; applied++; }
    }

    private static final class Device extends Limelight3A {
        volatile Thread owner;
        volatile boolean wrongOwner;
        volatile CountDownLatch startGate, readGate;
        volatile int blockReadPipeline = -1;
        final CountDownLatch startEntered = new CountDownLatch(1), readEntered = new CountDownLatch(1);
        volatile LLResult detector = new LLResult();
        final LLResult tag = new LLResult();
        Device() {
            detector.detectors.add(new LLResultTypes.DetectorResult());
            tag.pipeline = 1; tag.ts = 2; tag.hub = 2; tag.tags = 1;
            tag.botpose = new Pose3D(new Position(DistanceUnit.METER, (25 - 72) * .0254,
                    (20 - 72) * .0254, 0, 0),
                    new YawPitchRollAngles(AngleUnit.DEGREES, 0, 0, 0, 0));
        }
        void check() { if (owner == null) owner = Thread.currentThread(); wrongOwner |= owner != Thread.currentThread(); }
        @Override public boolean pipelineSwitch(int index) { check(); return super.pipelineSwitch(index); }
        @Override public void setPollRateHz(int hz) { check(); super.setPollRateHz(hz); }
        @Override public void start() { check(); startEntered.countDown(); block(startGate); super.start(); }
        @Override public void stop() { check(); super.stop(); }
        @Override public boolean isConnected() { check(); return super.isConnected(); }
        @Override public LLResult getLatestResult() {
            check();
            CountDownLatch gate = readGate;
            if (gate != null && (blockReadPipeline < 0 || pipeline == blockReadPipeline)) {
                readEntered.countDown(); block(gate);
            }
            return pipeline == 1 ? tag : detector;
        }
        void release() {
            if (startGate != null) startGate.countDown();
            if (readGate != null) readGate.countDown();
        }
        void next_frame() {
            LLResult next = new LLResult(); next.ts = detector.ts + 1; next.hub = detector.hub + 1;
            next.detectors.add(new LLResultTypes.DetectorResult()); detector = next;
        }
        static void block(CountDownLatch gate) {
            if (gate == null) return;
            boolean released = false;
            while (!released) {
                try { gate.await(); released = true; }
                catch (InterruptedException ignoredForBlockedSdkSimulation) { }
            }
        }
    }

    private static Butine.Params parameters() {
        Butine.Params params = new Butine.Params(); params.capacity = 1;
        params.sweepIntake = false; // Lifecycle/lease fixture deliberately uses its per-leg drive double.
        params.lookaheadRuns = 1; params.iterationCap = 0; params.localSearchPasses = 0;
        return params;
    }
    private static void service_until(Session s, BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { s.workers.update(); Thread.sleep(2); }
        require(condition.getAsBoolean(), message);
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        require(condition.getAsBoolean(), message);
    }
    private static void require(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}
