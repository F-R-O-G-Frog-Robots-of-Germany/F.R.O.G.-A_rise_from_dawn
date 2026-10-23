package org.firstinspires.ftc.teamcode.limelight;

/** Single-exposure localization from a rigid tag, its stationary hinge and gravity. */
public final class HivePoseEstimator {
    private static final double EPSILON = 1e-9;
    private static final double ROTATION_TOLERANCE = 1e-5;
    private static final double[] DOWN = {0, 0, -1};

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
