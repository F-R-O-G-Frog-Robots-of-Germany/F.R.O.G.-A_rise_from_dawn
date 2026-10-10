package org.firstinspires.ftc.teamcode.limelight;

import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Camera/map lifecycle and a nonblocking AprilTag correction between drive legs. */
public final class LimelightRunner {
    public static final class Stats {
        public int attempts, applied, rejectedJump, rejectedBounds, rejectedMoving, noSolve;
        public double lastJumpIn;
    }

    private enum State { IDLE, LOOKING, SETTLING }
    private final Limelight camera;
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

    public LimelightRunner(Limelight camera, PollenMap map, ButineRunner.Drive drive) {
        if (camera == null || map == null || drive == null) {
            throw new IllegalArgumentException("camera, map and drive are required");
        }
        this.camera = camera;
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
        if (!camera.is_connected()) {
            stats.noSolve++;
            lastFix = "no solve: camera disconnected";
            return false;
        }
        this.maxJumpIn = maxJumpIn;
        this.lookSeconds = lookSeconds;
        this.settleSeconds = settleSeconds;
        stationaryPose = startPose;
        movedDuringLook = false;
        initialFrameTimestamp = camera.get_result().frameTimestamp;
        if (!camera.set_pipeline(Limelight.APRILTAG_PIPELINE)) {
            stats.noSolve++;
            lastFix = "no solve: pipeline switch failed";
            camera.set_pipeline(Limelight.DETECTOR_PIPELINE);
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
            if (state == State.LOOKING) restore_detector(System.nanoTime());
            throw exception;
        }
    }

    private void update_vision() {
        long now = System.nanoTime();
        VisionPose pose = drive.pose();
        camera.update(pose);
        if (now - fullUpdateNanos >= 250_000_000L) {
            camera.update_full(pose);
            fullUpdateNanos = now;
        }
        map.update(camera);
        if (state == State.LOOKING) {
            movedDuringLook |= drive.is_busy() || pose == null || !pose.is_finite()
                    || stationaryPose == null || !stationaryPose.is_finite()
                    || pose.distance_to(stationaryPose) > 0.5
                    || Math.abs(VisionPose.wrap_pi(pose.heading - stationaryPose.heading)) > Math.toRadians(5);
            VisionPose fix = camera.get_pose();
            if ((now - startedNanos) / 1e9 >= lookSeconds) {
                stats.noSolve++;
                lastFix = "no solve: no fresh AprilTag pose within look deadline";
                restore_detector(now);
            } else if (camera.pipeline() == Limelight.APRILTAG_PIPELINE && fix != null
                    && (initialFrameTimestamp <= 0 || camera.get_result().frameTimestamp != initialFrameTimestamp)) {
                apply_fix(fix);
                restore_detector(now);
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
        stats.applied++;
        stats.lastJumpIn = jump;
        lastFix = "applied " + jump + " in correction";
    }

    private void restore_detector(long now) {
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

    public void stop() {
        state = State.IDLE;
        camera.set_pipeline(Limelight.DETECTOR_PIPELINE);
        camera.stop();
    }

    public void telemetry(Telemetry telemetry) {
        camera.telemetry(telemetry);
        telemetry.addData("Map tracks", map.tracks().size());
        telemetry.addData("Relocalization", "%d/%d %s", stats.applied, stats.attempts, lastFix);
        telemetry.addData("Reloc rejects", "jump=%d bounds=%d moving=%d no solve=%d",
                stats.rejectedJump, stats.rejectedBounds, stats.rejectedMoving, stats.noSolve);
    }
}
