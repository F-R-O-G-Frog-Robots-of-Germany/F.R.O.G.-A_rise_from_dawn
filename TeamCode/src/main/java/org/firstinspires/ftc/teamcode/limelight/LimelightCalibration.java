package org.firstinspires.ftc.teamcode.limelight;

/** Flat-floor pinhole projection. Positive pitch points the camera down. */
public final class LimelightCalibration {
    public double cameraHeightIn = LimelightGeometry.CAMERA_HEIGHT_IN;
    public double cameraPitchDeg = LimelightGeometry.CAMERA_PITCH_DEG;

    boolean has_valid_mounting() {
        return Double.isFinite(cameraHeightIn) && cameraHeightIn > 0
                && Double.isFinite(cameraPitchDeg) && cameraPitchDeg >= 0 && cameraPitchDeg < 90;
    }

    /** Returns NaN for an angle outside the vertical field of view or an unusable floor ray. */
    public double forward_distance(double tyDeg) {
        if (!Double.isFinite(tyDeg) || Math.abs(tyDeg) > LimelightGeometry.VERTICAL_FOV_DEG / 2
                || !has_valid_mounting()) return Double.NaN;
        double depression = Math.toRadians(cameraPitchDeg - tyDeg);
        if (depression <= 1e-3 || depression >= Math.PI / 2) return Double.NaN;
        double forward = cameraHeightIn / Math.tan(depression);
        return Double.isFinite(forward) && forward > 0
                && forward <= LimelightGeometry.FAR_FORWARD_IN * 2 ? forward : Double.NaN;
    }

    /** Projects optical-axis angles. Positive tx is camera right and positive ty is up. */
    public VisionPose project(double txDeg, double tyDeg, VisionPose capturePose) {
        if (capturePose == null || !capturePose.is_finite()
                || !Double.isFinite(txDeg) || Math.abs(txDeg) > LimelightGeometry.HORIZONTAL_FOV_DEG / 2) {
            return null;
        }
        double forward = forward_distance(tyDeg);
        if (!Double.isFinite(forward)) return null;
        double pitch = Math.toRadians(cameraPitchDeg);
        double floorDown = Math.sin(pitch) - Math.tan(Math.toRadians(tyDeg)) * Math.cos(pitch);
        if (!(floorDown > 0) || !Double.isFinite(floorDown)) return null;
        double left = -cameraHeightIn * Math.tan(Math.toRadians(txDeg)) / floorDown;
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
