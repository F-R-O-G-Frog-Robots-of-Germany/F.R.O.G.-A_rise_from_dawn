package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single-exposure localization from a rigid tag, its stationary hinge and gravity. */
public final class HivePoseEstimator {
    private static final double EPSILON = 1e-9;
    private static final double ROTATION_TOLERANCE = 1e-5;
    private static final double[] DOWN = {0, 0, -1};
    private static final FrameConfig DEFAULT_FRAME_CONFIG = new FrameConfig(6.0, 10.0, 10.0);

    /** R_CT maps native tag coordinates into camera forward/left/up; lengths are inches. */
    public static final class Observation {
        public final int tagId;
        /** Exposure estimate on the caller's monotonic millisecond clock, never camera uptime. */
        public final long captureTimeMs;
        public final boolean timingAssumed;
        private final double[] rotationCameraTag;
        private final double[] translationCameraTag;

        public Observation(int tagId, double[] rotationCameraTag,
                           double[] translationCameraTag, long captureTimeMs) {
            this(tagId, rotationCameraTag, translationCameraTag, captureTimeMs, false);
        }

        public Observation(int tagId, double[] rotationCameraTag,
                           double[] translationCameraTag, long captureTimeMs,
                           boolean timingAssumed) {
            this.tagId = tagId;
            this.rotationCameraTag = copy_size(rotationCameraTag, 9);
            this.translationCameraTag = copy_size(translationCameraTag, 3);
            this.captureTimeMs = captureTimeMs;
            this.timingAssumed = timingAssumed;
        }

        public double[] rotation_camera_tag() { return rotationCameraTag.clone(); }
        public double[] translation_camera_tag() { return translationCameraTag.clone(); }
    }

    /** One particular hinge point, signed horizontal axis and their native-tag coordinates. */
    public static final class TagModel {
        public final int tagId;
        public final boolean usesAssumedGeometry;
        public final String provenance;
        private final double[] pivotWorld;
        private final double[] axisWorld;
        private final double[] pivotTag;
        private final double[] axisTag;

        public TagModel(int tagId, double[] pivotWorld, double[] axisWorld,
                        double[] pivotTag, double[] axisTag,
                        boolean usesAssumedGeometry, String provenance) {
            this.tagId = tagId;
            this.pivotWorld = copy_size(pivotWorld, 3);
            this.pivotTag = copy_size(pivotTag, 3);
            this.axisWorld = unit(copy_size(axisWorld, 3));
            this.axisTag = unit(copy_size(axisTag, 3));
            if (!finite(this.pivotWorld) || !finite(this.pivotTag)
                    || this.axisWorld == null || this.axisTag == null
                    || Math.abs(this.axisWorld[2]) > EPSILON) {
                throw new IllegalArgumentException("Finite geometry and a horizontal signed hinge axis required");
            }
            this.usesAssumedGeometry = usesAssumedGeometry;
            this.provenance = provenance == null ? "" : provenance;
        }

        public double[] pivot_world() { return pivotWorld.clone(); }
        public double[] pivot_tag() { return pivotTag.clone(); }
        public double[] axis_world() { return axisWorld.clone(); }
        public double[] axis_tag() { return axisTag.clone(); }
    }

    /** Camera FLU into robot FLU, row-major rotation and robot-to-camera offset in inches. */
    public static final class CameraMount {
        public final boolean usesAssumedGeometry;
        private final double[] rotationRobotCamera;
        private final double[] translationRobotCamera;

        public CameraMount(double[] rotationRobotCamera, double[] translationRobotCamera,
                           boolean usesAssumedGeometry) {
            this.rotationRobotCamera = copy_size(rotationRobotCamera, 9);
            this.translationRobotCamera = copy_size(translationRobotCamera, 3);
            if (!rotation(this.rotationRobotCamera) || !finite(this.translationRobotCamera)) {
                throw new IllegalArgumentException("Proper camera rotation and finite mounting offset required");
            }
            this.usesAssumedGeometry = usesAssumedGeometry;
        }
    }

    /** Centered world inches, counterclockwise heading radians; no automatic Pedro conversion. */
    public static final class Result {
        public final int tagId;
        public final long captureTimeMs;
        public final boolean usesAssumedValues;
        public final double x;
        public final double y;
        /** Estimated robot-reference height above tiles, retained as a geometry residual. */
        public final double z;
        public final double heading;
        public final double axisErrorDeg;

        private Result(Observation observation, boolean usesAssumedValues,
                       double[] position, double heading, double axisErrorDeg) {
            this.tagId = observation.tagId;
            this.captureTimeMs = observation.captureTimeMs;
            this.usesAssumedValues = usesAssumedValues;
            this.x = position[0];
            this.y = position[1];
            this.z = position[2];
            this.heading = heading;
            this.axisErrorDeg = axisErrorDeg;
        }

    }

    /** Pairwise agreement limits for frame fusion; defaults require hardware tuning. */
    public static final class FrameConfig {
        public final double maxPositionDifferenceIn;
        public final double maxHeadingDifferenceDeg;
        public final double maxAxisErrorDeg;

        public FrameConfig(double maxPositionDifferenceIn, double maxHeadingDifferenceDeg,
                           double maxAxisErrorDeg) {
            if (!Double.isFinite(maxPositionDifferenceIn) || maxPositionDifferenceIn <= 0
                    || !Double.isFinite(maxHeadingDifferenceDeg) || maxHeadingDifferenceDeg <= 0
                    || maxHeadingDifferenceDeg >= 90
                    || !Double.isFinite(maxAxisErrorDeg) || maxAxisErrorDeg < 0
                    || maxAxisErrorDeg >= 90) {
                throw new IllegalArgumentException("Positive distance, heading in (0,90), axis error in [0,90) required");
            }
            this.maxPositionDifferenceIn = maxPositionDifferenceIn;
            this.maxHeadingDifferenceDeg = maxHeadingDifferenceDeg;
            this.maxAxisErrorDeg = maxAxisErrorDeg;
        }
    }

    /**
     * Equal-weight pose consensus in the same centered field frame as Result.
     * Spreads describe disagreement among accepted tags, not calibrated measurement uncertainty.
     * Shared camera, mounting and gravity errors can affect every tag together.
     */
    public static final class FrameResult {
        public final long captureTimeMs;
        public final boolean usesAssumedValues;
        public final double x;
        public final double y;
        public final double z;
        public final double heading;
        public final int tagCount;
        /** Tags with a valid pose and acceptable axis error before consensus selection. */
        public final int usableTagCount;
        /** Input list size, including null, unknown or unusable observations. */
        public final int observationCount;
        /** Root-mean-square 3D distance from accepted poses to the mean, in inches. */
        public final double positionSpreadIn;
        /** Root-mean-square wrapped heading difference from the circular mean, in degrees. */
        public final double headingSpreadDeg;
        public final double maxAxisErrorDeg;
        private final int[] tagIds;

        private FrameResult(List<Result> accepted, int usableTagCount, int observationCount) {
            this.captureTimeMs = accepted.get(0).captureTimeMs;
            this.tagCount = accepted.size();
            this.usableTagCount = usableTagCount;
            this.observationCount = observationCount;
            this.tagIds = new int[tagCount];
            double meanX = 0, meanY = 0, meanZ = 0, meanSin = 0, meanCos = 0;
            double maxAxisError = 0;
            boolean assumed = false;
            for (int i = 0; i < tagCount; i++) {
                Result pose = accepted.get(i);
                tagIds[i] = pose.tagId;
                meanX += pose.x / tagCount;
                meanY += pose.y / tagCount;
                meanZ += pose.z / tagCount;
                meanSin += Math.sin(pose.heading) / tagCount;
                meanCos += Math.cos(pose.heading) / tagCount;
                maxAxisError = Math.max(maxAxisError, pose.axisErrorDeg);
                assumed |= pose.usesAssumedValues;
            }
            this.x = meanX;
            this.y = meanY;
            this.z = meanZ;
            this.heading = Math.atan2(meanSin, meanCos);
            this.maxAxisErrorDeg = maxAxisError;
            this.usesAssumedValues = assumed;
            double positionNorm = 0, headingNorm = 0;
            for (Result pose : accepted) {
                double distance = Math.hypot(Math.hypot(pose.x - x, pose.y - y), pose.z - z);
                positionNorm = Math.hypot(positionNorm, distance);
                headingNorm = Math.hypot(headingNorm, heading_difference(pose.heading, heading));
            }
            this.positionSpreadIn = positionNorm / Math.sqrt(tagCount);
            this.headingSpreadDeg = Math.toDegrees(headingNorm / Math.sqrt(tagCount));
        }

        /** Accepted unique IDs, sorted ascending. */
        public int[] tag_ids() { return tagIds.clone(); }
    }

    private final TagModel[] models;
    private final CameraMount mount;
    public HivePoseEstimator(TagModel[] models, CameraMount mount) {
        if (models == null || mount == null) {
            throw new IllegalArgumentException("Tag models and mounting required");
        }
        this.models = models.clone();
        for (int i = 0; i < models.length; i++) {
            if (models[i] == null) throw new IllegalArgumentException("Null tag model");
            for (int j = 0; j < i; j++) {
                if (models[i].tagId == models[j].tagId) throw new IllegalArgumentException("Duplicate tag ID");
            }
        }
        this.mount = mount;
    }

    /**
     * Gravity is downward in robot FLU at exposure time. Returns null for unusable inputs.
     * The caller owns frame freshness, gravity time alignment and repeated-frame handling.
     */
    public Result estimate(Observation observation, double[] gravityRobot) {
        return estimate(observation, gravityRobot, false);
    }

    public Result estimate(Observation observation, double[] gravityRobot, boolean gravityAssumed) {
        if (observation == null || !rotation(observation.rotationCameraTag)
                || !finite(observation.translationCameraTag)
                || observation.captureTimeMs < 0) {
            return null;
        }
        TagModel model = null;
        for (TagModel candidate : models) {
            if (candidate.tagId == observation.tagId) { model = candidate; break; }
        }
        if (model == null) return null;
        boolean assumed = model.usesAssumedGeometry || mount.usesAssumedGeometry
                || observation.timingAssumed || gravityAssumed;
        double[] gravity = gravityRobot == null || gravityRobot.length != 3 ? null : unit(gravityRobot);
        if (gravity == null) return null;

        double[] pivotCamera = add(observation.translationCameraTag,
                apply(observation.rotationCameraTag, model.pivotTag));
        double[] axisCamera = apply(observation.rotationCameraTag, model.axisTag);
        double[] gravityCamera = apply(transpose(mount.rotationRobotCamera), gravity);
        double vertical = Math.max(-1, Math.min(1, dot(axisCamera, gravityCamera)));
        double axisErrorDeg = Math.toDegrees(Math.asin(Math.abs(vertical)));
        axisCamera = unit(subtract(axisCamera, scale(gravityCamera, vertical)));
        if (axisCamera == null) return null;

        double[] worldFrame = columns(model.axisWorld, cross(DOWN, model.axisWorld), DOWN);
        double[] cameraFrame = columns(axisCamera, cross(gravityCamera, axisCamera), gravityCamera);
        double[] rotationWorldCamera = multiply(worldFrame, transpose(cameraFrame));
        double[] rotationWorldRobot = multiply(rotationWorldCamera, transpose(mount.rotationRobotCamera));
        double[] position = subtract(subtract(model.pivotWorld, apply(rotationWorldCamera, pivotCamera)),
                apply(rotationWorldRobot, mount.translationRobotCamera));
        double heading = Math.atan2(rotationWorldRobot[3], rotationWorldRobot[0]);
        if (!finite(position) || !Double.isFinite(heading)) {
            return null;
        }
        return new Result(observation, assumed, position, heading, axisErrorDeg);
    }

    /**
     * Fuses per-tag poses from one physical exposure. Defaults are 6 inches in 3D,
     * 10 degrees of heading disagreement and 10 degrees of hinge/gravity disagreement.
     * The caller supplies exposure-aligned gravity and handles freshness and repeated frames.
     */
    public FrameResult estimate_frame(List<Observation> observations, double[] gravityRobot,
                                      boolean gravityAssumed) {
        return estimate_frame(observations, gravityRobot, gravityAssumed, DEFAULT_FRAME_CONFIG);
    }

    /**
     * Returns null for no usable tags, mixed timestamps, duplicate IDs or ambiguous consensus.
     * Unknown/invalid poses and poses above the axis-error limit are excluded before voting.
     * A single usable tag is returned with tagCount == 1. Otherwise a unique strict majority
     * must agree pairwise in 3D position and wrapped heading. Tied or chained groups are rejected.
     * All accepted poses receive equal weight; no per-tag covariance is supplied by the SDK.
     *
     * <p>Matching timestamps cannot establish physical frame identity by themselves. Pass the
     * observations copied by HiveLimelightObservation.from_result from a single LLResult.
     */
    public FrameResult estimate_frame(List<Observation> observations, double[] gravityRobot,
                                      boolean gravityAssumed, FrameConfig config) {
        if (config == null) throw new IllegalArgumentException("Frame configuration required");
        if (observations == null || observations.isEmpty()) return null;
        Set<Integer> seenIds = new HashSet<>();
        long captureTimeMs = -1;
        List<Result> candidates = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation == null) continue;
            if (observation.captureTimeMs < 0 || !seenIds.add(observation.tagId)) return null;
            if (captureTimeMs >= 0 && observation.captureTimeMs != captureTimeMs) return null;
            captureTimeMs = observation.captureTimeMs;
            Result pose = estimate(observation, gravityRobot, gravityAssumed);
            if (pose != null && pose.axisErrorDeg <= config.maxAxisErrorDeg) candidates.add(pose);
        }
        if (candidates.isEmpty()) return null;
        // Stable ordering makes equal-weight floating-point sums independent of input order.
        candidates.sort((first, second) -> Integer.compare(first.tagId, second.tagId));
        int count = candidates.size();
        boolean[][] agrees = new boolean[count][count];
        for (int i = 0; i < count; i++) {
            Result first = candidates.get(i);
            for (int j = 0; j <= i; j++) {
                Result second = candidates.get(j);
                double distance = Math.hypot(Math.hypot(first.x - second.x, first.y - second.y),
                        first.z - second.z);
                boolean compatible = distance <= config.maxPositionDifferenceIn
                        && Math.abs(heading_difference(first.heading, second.heading))
                        <= Math.toRadians(config.maxHeadingDifferenceDeg);
                agrees[i][j] = agrees[j][i] = compatible;
            }
        }
        boolean[] best = null;
        int bestCount = 0;
        boolean ambiguous = false;
        for (boolean[] group : agrees) {
            int support = 0;
            for (boolean member : group) if (member) support++;
            if (support > bestCount) {
                best = group;
                bestCount = support;
                ambiguous = false;
            } else if (support == bestCount && !Arrays.equals(best, group)) {
                ambiguous = true;
            }
        }
        if (ambiguous || bestCount <= count / 2) return null;
        List<Result> accepted = new ArrayList<>(bestCount);
        for (int i = 0; i < count; i++) {
            if (!best[i]) continue;
            for (int j = 0; j < i; j++) {
                if (best[j] && !agrees[i][j]) return null;
            }
            accepted.add(candidates.get(i));
        }
        FrameResult result = new FrameResult(accepted, count, observations.size());
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)
                || !Double.isFinite(result.positionSpreadIn) || !Double.isFinite(result.headingSpreadDeg)) {
            return null;
        }
        return result;
    }

    private static double heading_difference(double first, double second) {
        return Math.atan2(Math.sin(first - second), Math.cos(first - second));
    }

    private static double[] copy_size(double[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Expected " + size + " values");
        return values.clone();
    }

    private static boolean finite(double[] values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double[] unit(double[] vector) {
        if (!finite(vector)) return null;
        double length = Math.hypot(Math.hypot(vector[0], vector[1]), vector[2]);
        return length > EPSILON && Double.isFinite(length) ? scale(vector, 1 / length) : null;
    }

    private static boolean rotation(double[] matrix) {
        if (!finite(matrix)) return false;
        for (int row = 0; row < 3; row++) {
            for (int other = 0; other < 3; other++) {
                double product = 0;
                for (int col = 0; col < 3; col++) product += matrix[row * 3 + col] * matrix[other * 3 + col];
                if (Math.abs(product - (row == other ? 1 : 0)) > ROTATION_TOLERANCE) return false;
            }
        }
        double determinant = matrix[0] * (matrix[4] * matrix[8] - matrix[5] * matrix[7])
                - matrix[1] * (matrix[3] * matrix[8] - matrix[5] * matrix[6])
                + matrix[2] * (matrix[3] * matrix[7] - matrix[4] * matrix[6]);
        return Math.abs(determinant - 1) <= ROTATION_TOLERANCE;
    }

    private static double dot(double[] a, double[] b) { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]; }
    private static double[] scale(double[] a, double factor) { return new double[]{a[0]*factor,a[1]*factor,a[2]*factor}; }
    private static double[] add(double[] a, double[] b) { return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]}; }
    private static double[] subtract(double[] a, double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
    }
    private static double[] columns(double[] a, double[] b, double[] c) {
        return new double[]{a[0],b[0],c[0],a[1],b[1],c[1],a[2],b[2],c[2]};
    }
    private static double[] apply(double[] matrix, double[] vector) {
        return new double[]{matrix[0]*vector[0]+matrix[1]*vector[1]+matrix[2]*vector[2],
                matrix[3]*vector[0]+matrix[4]*vector[1]+matrix[5]*vector[2],
                matrix[6]*vector[0]+matrix[7]*vector[1]+matrix[8]*vector[2]};
    }
    private static double[] transpose(double[] matrix) {
        return new double[]{matrix[0],matrix[3],matrix[6],matrix[1],matrix[4],matrix[7],matrix[2],matrix[5],matrix[8]};
    }
    private static double[] multiply(double[] a, double[] b) {
        double[] result = new double[9];
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            for (int k = 0; k < 3; k++) result[row*3+col] += a[row*3+k]*b[k*3+col];
        }
        return result;
    }
}
