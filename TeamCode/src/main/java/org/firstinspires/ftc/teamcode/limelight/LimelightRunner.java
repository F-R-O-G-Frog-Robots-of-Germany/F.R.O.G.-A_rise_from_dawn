package org.firstinspires.ftc.teamcode.limelight;

import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Camera/map lifecycle and a nonblocking AprilTag correction between drive legs. */
public final class LimelightRunner {
    public static final class Stats {
        public int attempts, applied, rejectedJump, rejectedBounds, rejectedMoving, noSolve;
        public double lastJumpIn;
    }

    private enum State { IDLE, LOOKING, RESTORING, SETTLING }
    private final Limelight camera;
    private final LimelightWorker worker;
    private final PollenMap map;
    private final ButineRunner.Drive drive;
    private final Stats stats = new Stats();
    private State state = State.IDLE;
    private String lastFix = "not attempted";
    private long startedNanos, fullUpdateNanos;
    private double maxJumpIn, lookSeconds, settleSeconds;
    private double initialFrameTimestamp;
    private VisionPose stationaryPose;
    private boolean movedDuringLook;
    private long tagPipelineToken, restorePipelineToken;

    public LimelightRunner(Limelight camera, PollenMap map, ButineRunner.Drive drive) {
        if (camera == null || map == null || drive == null) {
            throw new IllegalArgumentException("camera, map and drive are required");
        }
        this.camera = camera;
        this.worker = null;
        this.map = map;
        this.drive = drive;
    }

    /** Async camera path: all follower and drive calls still execute on the calling robot thread. */
    public LimelightRunner(LimelightWorker worker, PollenMap map, ButineRunner.Drive drive) {
        if (worker == null || map == null || drive == null) {
            throw new IllegalArgumentException("worker, map and drive are required");
        }
        this.camera = null;
        this.worker = worker;
        this.map = map;
        this.drive = drive;
    }

    public boolean init() {
        state = State.IDLE;
        lastFix = "not attempted";
        fullUpdateNanos = 0;
        stats.attempts = stats.applied = stats.rejectedJump = stats.rejectedBounds = 0;
        stats.rejectedMoving = stats.noSolve = 0;
        stats.lastJumpIn = 0;
        if (worker != null) {
            if (!worker.start()) return false;
            map.init();
            return true; // Accepted asynchronously; worker.snapshot().status reports camera init.
        }
        map.init();
        return camera.init();
    }

    public boolean relocalize() { return relocalize(12.0, 0.6, 0.2); }

    public boolean relocalize(double maxJumpIn, double lookSeconds, double settleSeconds) {
        if (!Double.isFinite(maxJumpIn) || maxJumpIn < 0 || !Double.isFinite(lookSeconds)
                || lookSeconds < 0 || !Double.isFinite(settleSeconds) || settleSeconds < 0) {
            throw new IllegalArgumentException("relocalization limits must be finite and nonnegative");
        }
        if (state != State.IDLE) return false;
        stats.attempts++;
        if (drive.is_busy()) {
            stats.rejectedMoving++;
            lastFix = "rejected: still driving";
            return false;
        }
        VisionPose startPose = drive.pose();
        if (startPose == null || !startPose.is_finite()) {
            stats.rejectedMoving++;
            lastFix = "rejected: invalid odometry pose";
            return false;
        }
        if (!(worker == null ? camera.is_connected() : worker.snapshot().connected)) {
            stats.noSolve++;
            lastFix = "no solve: camera disconnected";
            return false;
        }
        this.maxJumpIn = maxJumpIn;
        this.lookSeconds = lookSeconds;
        this.settleSeconds = settleSeconds;
        stationaryPose = startPose;
        movedDuringLook = false;
        initialFrameTimestamp = worker == null ? camera.get_result().frameTimestamp
                : worker.snapshot().result.frameTimestamp;
        boolean switched;
        if (worker == null) switched = camera.set_pipeline(Limelight.APRILTAG_PIPELINE);
        else {
            tagPipelineToken = worker.request_pipeline(Limelight.APRILTAG_PIPELINE);
            switched = tagPipelineToken != 0;
        }
        if (!switched) {
            stats.noSolve++;
            lastFix = "no solve: pipeline switch failed";
            if (worker == null) camera.set_pipeline(Limelight.DETECTOR_PIPELINE);
            else worker.request_stop();
            return false;
        }
        startedNanos = System.nanoTime();
        state = State.LOOKING;
        return true;
    }

    /** Call once each robot loop after localization updates; this method never waits. */
    public void update() {
        try { update_vision(); }
        catch (RuntimeException exception) {
            lastFix = "vision runner failure: " + exception;
            if (worker != null) {
                worker.request_stop();
                state = State.IDLE;
            } else if (state == State.LOOKING) restore_detector(System.nanoTime());
            throw exception;
        }
    }

    private void update_vision() {
        long now = System.nanoTime();
        VisionPose pose = drive.pose();
        LimelightWorker.Snapshot view = null;
        if (worker != null) {
            worker.submit_pose(pose);
            view = worker.snapshot();
        } else {
            camera.update(pose);
            if (now - fullUpdateNanos >= 250_000_000L) {
                camera.update_full(pose);
                fullUpdateNanos = now;
            }
            map.update(camera);
        }
        if (state == State.LOOKING) {
            movedDuringLook |= drive.is_busy() || pose == null || !pose.is_finite()
                    || stationaryPose == null || !stationaryPose.is_finite()
                    || pose.distance_to(stationaryPose) > 0.5
                    || Math.abs(VisionPose.wrap_pi(pose.heading - stationaryPose.heading)) > Math.toRadians(5);
            VisionPose fix = worker == null ? camera.get_pose() : view.get_pose();
            int pipeline = worker == null ? camera.pipeline() : view.pipeline;
            double frameTimestamp = worker == null ? camera.get_result().frameTimestamp
                    : view.result.frameTimestamp;
            boolean tagReady = worker == null
                    || (view.pipelineAckToken >= tagPipelineToken && view.pipelineAckSuccess);
            if ((now - startedNanos) / 1e9 >= lookSeconds) {
                stats.noSolve++;
                lastFix = "no solve: no fresh AprilTag pose within look deadline";
                restore_detector(now);
            } else if (tagReady && pipeline == Limelight.APRILTAG_PIPELINE && fix != null
                    && (initialFrameTimestamp <= 0 || frameTimestamp != initialFrameTimestamp)) {
                apply_fix(fix);
                restore_detector(now);
            }
        } else if (state == State.RESTORING) {
            if (worker.pipeline_applied(restorePipelineToken, Limelight.DETECTOR_PIPELINE)) {
                startedNanos = now;
                state = State.SETTLING;
            } else if (!worker.is_running() || now - startedNanos >= 2_000_000_000L
                    || (view.pipelineAckToken >= restorePipelineToken && !view.pipelineAckSuccess)) {
                lastFix += "; detector restore failed or timed out";
                worker.request_stop();
                state = State.IDLE;
            }
        } else if (state == State.SETTLING && (now - startedNanos) / 1e9 >= settleSeconds) {
            state = State.IDLE;
        }
    }

    private void apply_fix(VisionPose fix) {
        VisionPose current = drive.pose();
        if (movedDuringLook || drive.is_busy() || current == null || stationaryPose == null
                || !stationaryPose.is_finite()
                || !current.is_finite() || current.distance_to(stationaryPose) > 0.5
                || Math.abs(VisionPose.wrap_pi(current.heading - stationaryPose.heading)) > Math.toRadians(5)) {
            stats.rejectedMoving++;
            lastFix = "rejected: moved during capture";
            return;
        }
        double field = LimelightGeometry.FIELD_WIDTH_IN;
        if (!fix.is_finite() || fix.x < -6 || fix.x > field + 6 || fix.y < -6 || fix.y > field + 6) {
            stats.rejectedBounds++;
            lastFix = "rejected: pose outside field";
            return;
        }
        double jump = current.distance_to(fix);
        if (jump > maxJumpIn) {
            stats.rejectedJump++;
            lastFix = "rejected: " + jump + " in jump";
            return;
        }
        drive.set_pose(fix);
        synchronized (map) {
            if (worker != null) worker.submit_pose(null);
            map.clear();
            if (worker != null) worker.submit_pose(fix);
        }
        stats.applied++;
        stats.lastJumpIn = jump;
        lastFix = "applied " + jump + " in correction";
    }

    private void restore_detector(long now) {
        if (worker != null) {
            restorePipelineToken = worker.request_pipeline(Limelight.DETECTOR_PIPELINE);
            startedNanos = now;
            if (restorePipelineToken == 0) {
                lastFix += "; detector restore request rejected";
                worker.request_stop();
                state = State.IDLE;
            } else state = State.RESTORING;
            return;
        }
        if (!camera.set_pipeline(Limelight.DETECTOR_PIPELINE)) {
            lastFix += "; detector restore failed";
            camera.stop();
        }
        startedNanos = now;
        state = State.SETTLING;
    }

    public boolean is_busy() { return state != State.IDLE; }
    public Stats stats() { return stats; }
    public String last_fix() { return lastFix; }

    /** Cancel an in-flight correction without applying a later fix; async SDK work stays on its owner. */
    public void cancel() {
        if (state == State.IDLE) return;
        state = State.IDLE;
        lastFix = "cancelled";
        if (worker != null) {
            if (worker.request_pipeline(Limelight.DETECTOR_PIPELINE) == 0) worker.request_stop();
        } else if (!camera.set_pipeline(Limelight.DETECTOR_PIPELINE)) camera.stop();
    }

    public void stop() {
        state = State.IDLE;
        if (worker != null) worker.stop();
        else {
            camera.set_pipeline(Limelight.DETECTOR_PIPELINE);
            camera.stop();
        }
    }

    public void telemetry(Telemetry telemetry) {
        if (telemetry == null) return;
        if (worker == null) camera.telemetry(telemetry);
        else worker.telemetry(telemetry);
        telemetry.addData("Map tracks", map.tracks().size());
        telemetry.addData("Relocalization", "%d/%d %s", stats.applied, stats.attempts, lastFix);
        telemetry.addData("Reloc rejects", "jump=%d bounds=%d moving=%d no solve=%d",
                stats.rejectedJump, stats.rejectedBounds, stats.rejectedMoving, stats.noSolve);
    }
}
