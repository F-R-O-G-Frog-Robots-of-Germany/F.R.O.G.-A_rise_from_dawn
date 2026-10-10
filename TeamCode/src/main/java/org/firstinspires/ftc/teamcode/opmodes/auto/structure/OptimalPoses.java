package org.firstinspires.ftc.teamcode.opmodes.auto.structure;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.math.Pose;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;

/**
 * Ideal, unmirrored RED field geometry for autonomous routes. Change these
 * coordinates only when the route changes. Put field-specific corrections in
 * the corresponding op mode as {@code hotfix(dx, dy, ddeg).plus(idealPose)}.
 * The correction is applied before mirroring for BLUE.
 */
public final class OptimalPoses {
    // Switch off to compare every route against its ideal geometry without
    // deleting any field tuning. Java inlines this compile-time constant.
    public static final boolean FIELD_FIX = true;

    private static final PoseFactory DEGREES = PoseFactory.degrees();

    // Shared shooting positions for both routes, facing the nearest RED hive.
    public static final Pose SHOOT_IN_LOWER_HIVE = DEGREES.of(59.25, 9, 90);
    public static final Pose SHOOT_IN_UPPER_HIVE = DEGREES.of(59.25, 135, -90);

    private OptimalPoses() {
    }

    /** Inches in x/y and degrees in heading, relative to an ideal pose. */
    public static final class Delta {
        private final double dx;
        private final double dy;
        private final double ddeg;

        private Delta(double dx, double dy, double ddeg) {
            if (!SensorReadings.are_valid(dx, dy, ddeg)) {
                throw new IllegalArgumentException("Hotfix values must be finite");
            }
            this.dx = dx;
            this.dy = dy;
            this.ddeg = ddeg;
        }

        public Pose plus(Pose BASE) {
            if (!FIELD_FIX || (dx == 0.0 && dy == 0.0 && ddeg == 0.0)) return BASE;
            return new Pose(BASE.x() + dx, BASE.y() + dy,
                    BASE.heading() + Math.toRadians(ddeg));
        }
    }

/** Java's equivalent of {@code hotfix(dx, dy, ddeg) + idealPose}. */
    public static Delta hotfix(double dx, double dy, double ddeg) {
        return new Delta(dx, dy, ddeg);
    }

    public static final class BothFlowersNoGardenRoute {
        private BothFlowersNoGardenRoute() {
        }

        public static final Pose START = SHOOT_IN_LOWER_HIVE;

        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(48, 132, 90.9767);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 25, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(85, 70, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(48, 100, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);

        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -179.0231);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(34, 113, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = DEGREES.of(34, 113, 0);

        public static final Pose PARK = DEGREES.of(8, 120, -174.3889);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 125, 0);
    }

    public static final class SoloWithStealRoute {
        private SoloWithStealRoute() {
        }

        public static final Pose START = SHOOT_IN_LOWER_HIVE;

        public static final Pose OPPONENT_PICK_UP = DEGREES.of(94, 8, -89.0822);
        public static final Pose OPPONENT_PICK_UP_CONTROL_1 = DEGREES.of(59, 22, 0);
        public static final Pose OPPONENT_PICK_UP_CONTROL_2 = DEGREES.of(94, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_CONTROL_1 = DEGREES.of(94, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_CONTROL_2 = DEGREES.of(59, 22, 0);
        public static final Pose SHOOT_FROM_OPPONENT_CONTROL_3 = DEGREES.of(68.0231884057971, 104.10144927536234, 0);

        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4058);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);

        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -179.023);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(34, 113, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 47, 0);

        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL = DEGREES.of(58.1928, 49.7987, 0);

        public static final Pose INTAKE_GARDEN = DEGREES.of(9, 9, 178.8599);
        public static final Pose SHOOT_GARDEN_CONTROL_1 = DEGREES.of(41.155, 13.7674, 0);
        public static final Pose PARK = DEGREES.of(11.8243, 89.2248, 99.2759);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(16.5116, 59.4842, 0);
    }

    // Routes imported from the Pedro Visualizer; shared hive poses stay above.
    public static final class OpponentFarMidPickupParkRoute {
        private OpponentFarMidPickupParkRoute() {
        }

        public static final Pose OPPONENT_FLOWER_PICK_UP = DEGREES.of(94, 8, -89.0822);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 22, 0);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(94, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1 = DEGREES.of(94, 50, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2 = DEGREES.of(72, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3 = DEGREES.of(59, 100, 0);
        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4058);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -178.9422);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(47, 47, 0);
        public static final Pose PARK = DEGREES.of(8, 93, 180);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(40, 70, 0);
    }

    public static final class OpponentFarMidGardenPickupRoute {
        private OpponentFarMidGardenPickupRoute() {
        }

        public static final Pose OPPONENT_FLOWER_PICK_UP = DEGREES.of(94, 8, -89.0822);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 22, 0);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(94, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1 = DEGREES.of(94, 50, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2 = DEGREES.of(72, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3 = DEGREES.of(59, 100, 0);
        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4058);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -178.9422);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = DEGREES.of(59.25, 17, 0);
        public static final Pose GARDEN_PICK_UP = DEGREES.of(8, 8, -179.9008);
        public static final Pose GARDEN_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 17, 0);
        public static final Pose GARDEN_PICK_UP_CONTROL_2 = DEGREES.of(60, 8, 0);
    }

    public static final class OpponentFarGardenParkRoute {
        private OpponentFarGardenParkRoute() {
        }

        public static final Pose OPPONENT_FLOWER_PICK_UP = DEGREES.of(94, 8, -89.0822);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 22, 0);
        public static final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(94, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1 = DEGREES.of(94, 50, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2 = DEGREES.of(72, 30, 0);
        public static final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3 = DEGREES.of(59, 100, 0);
        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4058);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);
        public static final Pose GARDEN_PICK_UP = DEGREES.of(8, 8, -179.584);
        public static final Pose GARDEN_PICK_UP_CONTROL_1 = DEGREES.of(94, 8, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_1 = DEGREES.of(60, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_2 = DEGREES.of(59.25, 17, 0);
        public static final Pose PARK = DEGREES.of(8, 92, 90.7126);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 17, 0);
        public static final Pose PARK_CONTROL_2 = DEGREES.of(8, 51, 0);
    }

    public static final class GardenFarMidParkRoute {
        private GardenFarMidParkRoute() {
        }

        public static final Pose GARDEN_PICK_UP = DEGREES.of(8, 8, 179.0083);
        public static final Pose GARDEN_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 17, 0);
        public static final Pose GARDEN_PICK_UP_CONTROL_2 = DEGREES.of(60, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_1 = DEGREES.of(70, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_2 = DEGREES.of(-25, 110, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_3 = DEGREES.of(61, 90, 0);
        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 132, 90.4689);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 117, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 117, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -178.9422);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = DEGREES.of(59.25, 45, 0);
        public static final Pose PARK = DEGREES.of(8, 100, 90.5069);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 30, 0);
        public static final Pose PARK_CONTROL_2 = DEGREES.of(8, 42, 0);
    }

    public static final class FarMidParkRoute {
        private FarMidParkRoute() {
        }

        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(48, 132, 90.9767);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 25, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(85, 70, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(48, 100, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -178.9422);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_3 = DEGREES.of(59.25, 115, 0);
        public static final Pose PARK = DEGREES.of(8, 120, -174.3889);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 125, 0);
    }

    public static final class FarMidGardenParkRoute {
        private FarMidGardenParkRoute() {
        }

        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(48, 132, 90.9767);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 25, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(85, 70, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(48, 100, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 115, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59, 115, 0);

        public static final Pose MID_FLOWER_PICK_UP = DEGREES.of(8, 47, -178.9422);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose MID_FLOWER_PICK_UP_CONTROL_3 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = DEGREES.of(47, 47, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = DEGREES.of(4, 94, 0);
        public static final Pose SHOOT_FROM_MID_FLOWER_CONTROL_3 = DEGREES.of(59.25, 115, 0);

        public static final Pose GARDEN_PICK_UP = DEGREES.of(8, 8, -179.4841);
        public static final Pose GARDEN_PICK_UP_CONTROL_1 = DEGREES.of(61, 90, 0);
        public static final Pose GARDEN_PICK_UP_CONTROL_2 = DEGREES.of(-25, 110, 0);
        public static final Pose GARDEN_PICK_UP_CONTROL_3 = DEGREES.of(70, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_1 = DEGREES.of(70, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_2 = DEGREES.of(-25, 110, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_3 = DEGREES.of(61, 90, 0);

        public static final Pose PARK = DEGREES.of(8, 120, -179.9326);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 120, 0);
    }

    public static final class GardenFarParkRoute {
        private GardenFarParkRoute() {
        }

        public static final Pose GARDEN_PICK_UP = DEGREES.of(8, 8, 179.0083);
        public static final Pose GARDEN_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 17, 0);
        public static final Pose GARDEN_PICK_UP_CONTROL_2 = DEGREES.of(60, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_1 = DEGREES.of(70, 7, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_2 = DEGREES.of(-25, 110, 0);
        public static final Pose SHOOT_FROM_GARDEN_CONTROL_3 = DEGREES.of(61, 90, 0);
        public static final Pose FAR_FLOWER_PICK_UP = DEGREES.of(47, 133, 90.4396);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = DEGREES.of(59.25, 115, 0);
        public static final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = DEGREES.of(47, 117, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = DEGREES.of(47, 117, 0);
        public static final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = DEGREES.of(59.25, 115, 0);
        public static final Pose PARK = DEGREES.of(8, 125, -179.9607);
        public static final Pose PARK_CONTROL_1 = DEGREES.of(59.25, 125, 0);
    }
}
