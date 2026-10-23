package org.firstinspires.ftc.teamcode.core.control;

import com.pedropathing.follower.Follower;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.ButineWorker;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.LimelightRunner;
import org.firstinspires.ftc.teamcode.limelight.LimelightWorker;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/**
 * Per-OpMode camera/planner ownership. Call lifecycle, requests and updates only on the
 * control thread. Configure returned camera, map and worker settings before start();
 * afterward camera SDK calls belong exclusively to the vision worker. Published planner
 * plans (including their public arrays) are read-only to consumers.
 */
public final class CompetitionWorkers {
    private static final Object OWNER_LOCK = new Object();
    // Competition facades share one lease; standalone bench workers own their own lifecycles.
    private static CompetitionWorkers owner;
    private final Limelight camera;
    private final PollenMap map;
    private final ButineRunner.Drive drive;
    private final LimelightWorker vision;
    private final ButineWorker planner;
    private final LimelightRunner corrections;
    private final ButineRunner collection;
    private boolean attempted, started, stopping, visionFailed;
    private boolean launching;
    private long invalidatedPoseNanos = Long.MIN_VALUE;
    private String report = "not started";

    /** Dynamic planning requires vision; enabling planning also enables its camera owner. */
    public CompetitionWorkers(HardwareMap hardwareMap, Follower follower,
                              boolean visionEnabled, boolean planningEnabled) {
        this(visionEnabled || planningEnabled ? new Limelight(hardwareMap) : null,
                null, visionEnabled || planningEnabled ? new PedroButineDrive(follower) : null,
                planningEnabled);
    }

    /** Injectable transport/drive seam; null camera disables all workers without constructing them. */
    public CompetitionWorkers(Limelight camera, PollenMap map, ButineRunner.Drive drive,
                              boolean planningEnabled) {
        if (camera == null && planningEnabled) throw new IllegalArgumentException("planning requires vision");
        if (camera != null && drive == null) throw new IllegalArgumentException("vision requires a drive");
        this.camera = camera;
        this.map = camera == null ? null : map == null ? new PollenMap(camera) : map;
        this.drive = drive;
        vision = camera == null ? null : new LimelightWorker(camera, this.map);
        planner = planningEnabled ? new ButineWorker() : null;
        corrections = vision == null ? null : new LimelightRunner(vision, this.map, drive);
        collection = planner == null ? null : new ButineRunner(this.map, drive, planner);
        if (vision == null) report = "disabled";
    }

    public Limelight camera() { return camera; }
    public PollenMap map() { return map; }
    public LimelightWorker vision() { return vision; }
    public ButineWorker planner() { return planner; }

    /** One launch attempt per OpMode. Camera initialization outcome remains asynchronous. */
    public boolean start() {
        if (attempted || stopping) return false;
        attempted = true;
        if (vision == null) return true;
        synchronized (OWNER_LOCK) {
            if (owner != null && (owner.launching || owner.has_owner())) {
                report = "workers unavailable: previous competition owner has not finished";
                return false;
            }
            owner = this;
            launching = true;
        }
        try {
            map.init();
            started = vision.start();
            if (!started) { report = "vision owner did not start"; return false; }
            if (planner != null && !planner.start()) report = "planner owner did not start";
            else report = "workers launched; waiting for camera and localized detector frame";
            return planner == null || planner.is_running();
        } catch (RuntimeException failure) {
            report = "worker startup failed: " + failure;
            request_stop();
            return false;
        } finally {
            synchronized (OWNER_LOCK) { launching = false; release_idle_owner(); }
        }
    }

    private boolean has_owner() {
        return (vision != null && vision.has_owner()) || (planner != null && planner.has_owner());
    }

    /** Caller holds OWNER_LOCK; no camera I/O or worker joins run while the lease is locked. */
    private void release_idle_owner() {
        if (owner == this && !launching && !has_owner()) owner = null;
    }

    /** Call once after an actual localization update, including during INIT. Never waits for workers. */
    public void update() {
        if (!started || stopping || visionFailed) return;
        try {
            VisionPose pose = drive.pose();
            if (pose == null || !pose.is_finite()) {
                invalidate_pose();
                return;
            }
            // This is the sole normal pose submission; the runner also publishes accepted corrections.
            int applied = corrections.stats().applied;
            corrections.update();
            if (corrections.stats().applied != applied) invalidatedPoseNanos = System.nanoTime();
            if (collection != null && collection.is_busy()) {
                if (!ready()) {
                    cancel_drive();
                    report = "collection cancelled: vision unavailable or stale";
                } else collection.update();
            }
        } catch (RuntimeException failure) {
            report = "worker service failed: " + failure;
            cancel_drive();
            visionFailed = true;
            vision.request_stop();
            if (planner != null) planner.request_stop();
        }
    }

    public boolean request_collection(VisionPose depot, Butine.Params params, double speedScale) {
        if (collection == null || !ready() || !planner.is_running() || owns_drive()) {
            report = "collection unavailable: need idle drive, planner and fresh localized detector frame";
            return false;
        }
        try {
            if (!valid_pose()) return false;
            boolean accepted = collection.run_butine(depot, params, speedScale);
            report = collection.last_report();
            return accepted;
        } catch (RuntimeException failure) {
            cancel_drive();
            report = "collection request failed: " + failure;
            return false;
        }
    }

    /** Explicit stationary correction; startup and ordinary loop servicing never request one. */
    public boolean request_relocalization() {
        if (!started || stopping || visionFailed || owns_drive()) return false;
        try {
            if (!valid_pose() || !vision.snapshot().connected || drive.is_busy()) return false;
            drive.stop();
            boolean accepted = corrections.relocalize();
            report = corrections.last_fix();
            return accepted;
        } catch (RuntimeException failure) {
            cancel_drive();
            report = "relocalization request failed: " + failure;
            return false;
        }
    }

    private boolean valid_pose() {
        VisionPose pose = drive.pose();
        if (pose != null && pose.is_finite()) return true;
        invalidate_pose();
        return false;
    }

    private void invalidate_pose() {
        vision.submit_pose(null);
        invalidatedPoseNanos = System.nanoTime();
        cancel_drive();
        report = "assistance unavailable: invalid localized pose";
    }

    /** Explicit odometry-origin reset: old field targets and in-flight old-origin evidence are invalid. */
    public void pose_changed() {
        if (vision == null) return;
        synchronized (map) {
            vision.submit_pose(null);
            map.clear();
            invalidatedPoseNanos = System.nanoTime();
        }
        cancel_drive();
        report = "pose origin changed; waiting for a fresh localized detector frame";
    }

    public boolean owns_drive() {
        return !stopping && ((collection != null && collection.is_busy())
                || (corrections != null && corrections.is_busy()));
    }

    public boolean collection_intake_required() {
        return !stopping && collection != null && collection.intake_required();
    }

    public boolean return_to_depot_if_loaded(boolean loaded) {
        if (stopping || collection == null) return false;
        boolean changed = collection.return_to_depot_if_loaded(loaded);
        if (changed) report = collection.last_report();
        return changed;
    }

    /** Manual/static-path override cancels assistance before the caller commands Pedro again. */
    public void cancel_drive() {
        boolean collecting = collection != null && collection.is_busy();
        boolean correcting = corrections != null && corrections.is_busy();
        if (!collecting && !correcting) return;
        try {
            if (collecting) collection.stop();
            else drive.stop();
        } catch (RuntimeException failure) {
            report = "assistance stop failed: " + failure;
        } finally {
            if (correcting) {
                try { corrections.cancel(); }
                catch (RuntimeException failure) {
                    visionFailed = true;
                    vision.request_stop();
                    report = "correction cancellation failed: " + failure;
                }
            }
        }
    }

    /** Fresh mapped detector evidence is required even if the map still contains older tracks. */
    public boolean ready() {
        if (!started || stopping || visionFailed) return false;
        LimelightWorker.Snapshot view = vision.snapshot();
        return view.running && view.connected && view.status == Limelight.Status.OK
                && view.updatedNanos > invalidatedPoseNanos
                && view.has_pipeline_frame(Limelight.DETECTOR_PIPELINE) && view.has_pollen_frame();
    }

    public String detail() {
        if (vision != null && started && !vision.snapshot().error.isEmpty())
            return report + "; " + vision.snapshot().error;
        return report;
    }

    /** Invalidate work immediately; no motor commands, SDK shutdown or thread joins occur here. */
    public void request_stop() {
        stopping = true;
        try { if (planner != null) planner.request_stop(); }
        finally { if (vision != null) vision.request_stop(); }
    }

    /** Call after motor outputs are stopped. Original owners retain blocked SDK/solver work. */
    public boolean stop() {
        request_stop();
        boolean plannerStopped = true, visionStopped = true;
        try { if (planner != null) plannerStopped = planner.stop(); }
        finally {
            try { if (vision != null) visionStopped = vision.stop(); }
            finally { synchronized (OWNER_LOCK) { release_idle_owner(); } }
        }
        return plannerStopped && visionStopped;
    }

    public void telemetry(Telemetry telemetry) {
        if (telemetry == null) return;
        telemetry.addData("Competition workers", "%s; ready=%s", detail(), ready());
        if (corrections != null) corrections.telemetry(telemetry);
        if (collection != null) collection.telemetry(telemetry);
        if (planner != null) {
            ButineWorker.Stats stats = planner.stats();
            telemetry.addData("Planner worker", "running=%s TID=%d CPU=%d", stats.running, stats.tid, stats.cpuCore);
            telemetry.addData("Planner affinity", stats.affinityDetail);
        }
    }
}
