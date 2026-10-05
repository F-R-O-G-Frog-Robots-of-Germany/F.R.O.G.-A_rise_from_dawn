package org.firstinspires.ftc.teamcode.opModes.auto.structure;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.math.Pose;

/**
 * Ideal, unmirrored RED field geometry for autonomous routes. Change these
 * coordinates only when the route changes. Put field-specific corrections in
 * the corresponding op mode as {@code hotfix(dx, dy, ddeg).apply(idealPose)}.
 * The correction is applied before mirroring for BLUE.
 */
public final class OptimalPoses {
    // Switch off to compare every route against its ideal geometry without
    // deleting any field tuning. Java inlines this compile-time constant.
    public static final boolean FIELD_FIX = true;

    private static final PoseFactory DEGREES = PoseFactory.degrees();

    private OptimalPoses() {
    }

    /** Inches in x/y and degrees in heading, relative to an ideal pose. */
    public static final class Delta {
        private final double dx;
        private final double dy;
        private final double ddeg;

        private Delta(double dx, double dy, double ddeg) {
            if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(ddeg)) {
                throw new IllegalArgumentException("Hotfix values must be finite");
            }
            this.dx = dx;
            this.dy = dy;
            this.ddeg = ddeg;
        }

        public Pose apply(Pose base) {
            if (!FIELD_FIX || (dx == 0.0 && dy == 0.0 && ddeg == 0.0)) return base;
            return new Pose(base.x() + dx, base.y() + dy,
                    base.heading() + Math.toRadians(ddeg));
        }
    }

    public static Delta hotfix() {
        return hotfix(0.0, 0.0, 0.0);
    }

    public static Delta hotfix(double dx, double dy, double ddeg) {
        return new Delta(dx, dy, ddeg);
    }

    public static final class SoloWithStealRoute {
        private SoloWithStealRoute() {
        }

        public static final Pose SHOOT_IN_LOWER_HIVE = DEGREES.of(59.25, 9, 90);
        public static final Pose SHOOT_IN_UPPER_HIVE = DEGREES.of(59.25, 135, -90);
        public static final Pose START = SHOOT_IN_LOWER_HIVE;

        public static final Pose OPPONENT_PICK_UP = DEGREES.of(94, 8, -89.0822);
        public static final Pose OPPONENT_PICK_UP_CONTROL_1 = DEGREES.of(59, 22, 0);
        public static final Pose OPPONENT_PICK_UP_CONTROL_2 = DEGREES.of(94, 30, 0);
        public static final Pose OPPONENT_DROP_OFF = SHOOT_IN_UPPER_HIVE;
        public static final Pose OPPONENT_DROP_OFF_CONTROL_1 = DEGREES.of(94, 50, 0);
        public static final Pose OPPONENT_DROP_OFF_CONTROL_2 = DEGREES.of(72, 30, 0);
        public static final Pose OPPONENT_DROP_OFF_CONTROL_3 = DEGREES.of(59, 100, 0);

        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4058);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 115, 0);
        public static final Pose FAR_FLOWER_DROP_OFF = SHOOT_IN_UPPER_HIVE;
        public static final Pose FAR_FLOWER_DROP_OFF_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose FAR_FLOWER_DROP_OFF_CONTROL_2 = DEGREES.of(59, 115, 0);

        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -179.023);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(34, 113, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 47, 0);

        public static final Pose SHOOT_IN_UPPER_HIVE_CONTROL = DEGREES.of(58.1928, 49.7987, 0);

        public static final Pose INTAKE_GARDEN = DEGREES.of(9, 9, 178.8599);
        public static final Pose PARK = DEGREES.of(11.8243, 89.2248, 99.2759);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(16.5116, 59.4842, 0);
    }
}
