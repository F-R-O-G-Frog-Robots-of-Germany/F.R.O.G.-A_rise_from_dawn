package org.firstinspires.ftc.teamcode.limelight;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Copies full per-tag Limelight poses into the hinge estimator; performs no camera I/O.
 *
 * <p>Camera coordinates become forward, left, up, with distances in inches. Tag coordinates
 * remain Limelight's native proper-rotation target coordinates. Tag-to-hinge geometry must
 * use that same native target basis. The published coordinate page's "right/down/out"
 * wording is handedness-ambiguous; the physical sticker-to-native mapping is recorded as
 * an assumption in HiveGeometry rather than silently reflecting a rotation matrix here.
 * This is a change of the camera basis only: R_camera_tag = S * R_optical_tag, not S*R*S^T.
 *
 * <p>The FTC 12 SDK copies Limelight's raw [roll, pitch, yaw] into a
 * YawPitchRollAngles container without converting axes. The conversion below follows
 * Limelight's own LimelightHelpers.toPose3D: Rz(yaw)*Ry(pitch)*Rx(roll). It deliberately
 * does not use the generic FTC YawPitchRollAngles robot-axis interpretation.
 * Sources:
 * https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-coordinate-systems
 * https://github.com/LimelightVision/limelightlib-wpijava/blob/main/LimelightHelpers.java
 *
 * <p>Enable full 3D AprilTag tracking with the correct 3.25 inch tag size. These are relative
 * target poses, not MegaTag field poses; a static field map of the moving Hive is unnecessary.
 */
public final class HiveLimelightObservation {
    private HiveLimelightObservation() { }

    /**
     * Converts a single detected tag. captureTimeMs is supplied by the caller in the
     * estimator's monotonic clock domain, aligned with the accompanying gravity measurement.
     * SDK Pose3D acquisition times are zero; LLResult staleness is not full exposure age.
     * This adapter therefore does not invent an exposure timestamp or establish freshness.
     * @throws IllegalArgumentException when the full pose is absent, nonfinite, or behind the camera
     */
    public static HivePoseEstimator.Observation from_fiducial(
            LLResultTypes.FiducialResult fiducial, long captureTimeMs) {
        return from_fiducial(fiducial, captureTimeMs, false);
    }

    /** timingAssumed must be true when the caller estimated exposure time from receipt/latencies. */
    public static HivePoseEstimator.Observation from_fiducial(
            LLResultTypes.FiducialResult fiducial, long captureTimeMs, boolean timingAssumed) {
        if (fiducial == null) throw new IllegalArgumentException("Missing fiducial");
        return from_target_pose(fiducial.getFiducialId(),
                fiducial.getTargetPoseCameraSpace(), captureTimeMs, timingAssumed);
    }

    /** Converts a target-in-camera pose from the FTC Limelight SDK, not a field or robot pose. */
    public static HivePoseEstimator.Observation from_target_pose(
            int tagId, Pose3D targetPoseCameraSpace, long captureTimeMs) {
        return from_target_pose(tagId, targetPoseCameraSpace, captureTimeMs, false);
    }

    /** Same conversion with explicit provenance for an estimated exposure timestamp. */
    public static HivePoseEstimator.Observation from_target_pose(
            int tagId, Pose3D targetPoseCameraSpace, long captureTimeMs, boolean timingAssumed) {
        if (targetPoseCameraSpace == null || targetPoseCameraSpace.getPosition() == null
                || targetPoseCameraSpace.getOrientation() == null) {
            throw new IllegalArgumentException("Missing full target pose");
        }
        Position position = targetPoseCameraSpace.getPosition();
        if (position.unit == null) throw new IllegalArgumentException("Missing distance unit");
        double x = DistanceUnit.INCH.fromUnit(position.unit, position.x);
        double y = DistanceUnit.INCH.fromUnit(position.unit, position.y);
        double z = DistanceUnit.INCH.fromUnit(position.unit, position.z);
        YawPitchRollAngles orientation = targetPoseCameraSpace.getOrientation();
        double roll = orientation.getRoll(AngleUnit.RADIANS);
        double pitch = orientation.getPitch(AngleUnit.RADIANS);
        double yaw = orientation.getYaw(AngleUnit.RADIANS);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(roll) || !Double.isFinite(pitch)
                || !Double.isFinite(yaw) || z <= 0) {
            // The SDK substitutes a zero pose when full 3D data are missing.
            throw new IllegalArgumentException("Invalid or unavailable full target pose");
        }

        double cr = Math.cos(roll), sr = Math.sin(roll);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        // First form optical Rz*Ry*Rx, then select its rows as [rowZ, -rowX, -rowY].
        double[] rotationCameraTag = {
                -sp, cp * sr, cp * cr,
                -cy * cp, -(cy * sp * sr - sy * cr), -(cy * sp * cr + sy * sr),
                -sy * cp, -(sy * sp * sr + cy * cr), -(sy * sp * cr - cy * sr)
        };
        return new HivePoseEstimator.Observation(tagId, rotationCameraTag,
                new double[]{z, -x, -y}, captureTimeMs, timingAssumed);
    }

    /**
     * Copies usable individual tag poses from one frame. The caller still owns frame identity,
     * freshness checks, clock alignment and the decision whether an observation is accepted.
     * Malformed individual poses are skipped; unknown tag IDs are left for the geometry lookup.
     */
    public static List<HivePoseEstimator.Observation> from_result(
            LLResult result, long captureTimeMs) {
        return from_result(result, captureTimeMs, false);
    }

    /** Same frame copy with explicit provenance for an estimated exposure timestamp. */
    public static List<HivePoseEstimator.Observation> from_result(
            LLResult result, long captureTimeMs, boolean timingAssumed) {
        if (result == null || !result.isValid()) return Collections.emptyList();
        List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();
        if (fiducials == null || fiducials.isEmpty()) return Collections.emptyList();
        List<HivePoseEstimator.Observation> observations = new ArrayList<>(fiducials.size());
        for (LLResultTypes.FiducialResult fiducial : fiducials) {
            try {
                observations.add(from_fiducial(fiducial, captureTimeMs, timingAssumed));
            } catch (IllegalArgumentException ignored) {
                // One tag without a usable 3D result must not hide other tags in this image.
            }
        }
        return Collections.unmodifiableList(observations);
    }
}
