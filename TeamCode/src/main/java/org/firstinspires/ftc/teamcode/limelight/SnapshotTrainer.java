package org.firstinspires.ftc.teamcode.limelight;

import java.util.UUID;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Capture training images on the camera; names cannot overwrite an earlier session. */
public final class SnapshotTrainer {
    private final Limelight camera;
    private final String sessionId = System.currentTimeMillis() + "_"
            + UUID.randomUUID().toString().substring(0, 8);
    private int attempts, captured, failed;
    private boolean autoCapture;
    private long lastCaptureNanos;
    private String lastFilename = "none";

    public SnapshotTrainer(Limelight camera) {
        if (camera == null) throw new IllegalArgumentException("camera is required");
        this.camera = camera;
    }

    public boolean take_snapshot() {
        lastFilename = "img_" + sessionId + "_" + (++attempts);
        lastCaptureNanos = System.nanoTime();
        boolean success = camera.capture_snapshot(lastFilename);
        if (success) captured++;
        else failed++;
        return success;
    }

    public void set_auto_capture(boolean enabled) {
        autoCapture = enabled;
        lastCaptureNanos = System.nanoTime();
    }

    public void toggle_auto_capture() { set_auto_capture(!autoCapture); }

    public void update() {
        if (autoCapture && System.nanoTime() - lastCaptureNanos >= 2_000_000_000L) take_snapshot();
    }

    public boolean auto_capture_active() { return autoCapture; }
    public String session_id() { return sessionId; }
    public int image_count() { return captured; }
    public int failed_count() { return failed; }
    public String last_filename() { return lastFilename; }

    public void telemetry(Telemetry telemetry) {
        telemetry.addData("Snapshot session", sessionId);
        telemetry.addData("Auto capture", autoCapture);
        telemetry.addData("Snapshots", "%d captured, %d failed", captured, failed);
        telemetry.addData("Last snapshot", lastFilename);
    }
}
