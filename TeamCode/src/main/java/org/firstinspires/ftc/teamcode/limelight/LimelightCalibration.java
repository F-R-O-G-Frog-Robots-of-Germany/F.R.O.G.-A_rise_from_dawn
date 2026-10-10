package org.firstinspires.ftc.teamcode.limelight;

/** Runtime range calibration. Positive pitch points the camera down. */
public final class LimelightCalibration {
    public double cameraHeightIn = LimelightGeometry.CAMERA_HEIGHT_IN;
    public double cameraPitchDeg = LimelightGeometry.CAMERA_PITCH_DEG;
    public boolean usePinholeRange = LimelightGeometry.USE_PINHOLE_RANGE;

    /** Returns NaN if the ray does not reach plausible floor in front of the camera. */
    public double forward_distance(double tyDeg) {
        if (!Double.isFinite(tyDeg)) return Double.NaN;
        double forward;
        if (usePinholeRange) {
            if (!Double.isFinite(cameraHeightIn) || cameraHeightIn <= 0
                    || !Double.isFinite(cameraPitchDeg)) return Double.NaN;
            double depression = Math.toRadians(cameraPitchDeg - tyDeg);
            if (depression <= 1e-3 || depression >= Math.PI / 2) return Double.NaN;
            forward = cameraHeightIn / Math.tan(depression);
        } else {
            double normalized = Math.max(0, Math.min(1,
                    (tyDeg + LimelightGeometry.VERTICAL_FOV_DEG / 2)
                            / LimelightGeometry.VERTICAL_FOV_DEG));
            forward = LimelightGeometry.NEAREST_FORWARD_IN
                    + LimelightGeometry.QUADRATIC_LINEAR_IN * normalized
                    + LimelightGeometry.QUADRATIC_CURVE_IN * normalized * normalized;
        }
        return Double.isFinite(forward) && forward > 0
                && forward <= LimelightGeometry.FAR_FORWARD_IN * 2 ? forward : Double.NaN;
    }

    /** Angular offsets come directly from the camera; positive tx means camera right. */
    public VisionPose project(double txDeg, double tyDeg, VisionPose capturePose) {
        if (capturePose == null || !capturePose.is_finite()
                || !Double.isFinite(txDeg) || Math.abs(txDeg) >= 90) return null;
        double forward = forward_distance(tyDeg);
        if (!Double.isFinite(forward)) return null;
        double left;
        if (usePinholeRange) {
            double pitch = Math.toRadians(cameraPitchDeg);
            double floorDown = Math.sin(pitch) - Math.tan(Math.toRadians(tyDeg)) * Math.cos(pitch);
            if (!(floorDown > 0) || !Double.isFinite(floorDown)) return null;
            left = -cameraHeightIn * Math.tan(Math.toRadians(txDeg)) / floorDown;
        } else {
            left = -forward * Math.tan(Math.toRadians(txDeg));
        }
        double yaw = Math.toRadians(LimelightGeometry.CAMERA_YAW_DEG);
        double localForward = LimelightGeometry.CAMERA_FORWARD_OFFSET_IN
                + forward * Math.cos(yaw) - left * Math.sin(yaw);
        double localLeft = LimelightGeometry.CAMERA_LEFT_OFFSET_IN
                + forward * Math.sin(yaw) + left * Math.cos(yaw);
        double cos = Math.cos(capturePose.heading);
        double sin = Math.sin(capturePose.heading);
        VisionPose position = new VisionPose(capturePose.x + localForward * cos - localLeft * sin,
                capturePose.y + localForward * sin + localLeft * cos, 0);
        return position.is_finite() ? position : null;
    }
}
