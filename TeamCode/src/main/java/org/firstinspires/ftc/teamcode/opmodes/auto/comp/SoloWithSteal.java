package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;

import static org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "RED | SoloWithSteal", group = "Red")
public class SoloWithSteal extends AbstractAuto {
    private static final double SHOT_DURATION_SECONDS = 1.25;
    // Seconds from each path start; tune these delays on the robot.
    private static final double OPPONENT_LOWER_FLOWERINTAKE_AFTER = 0.0;
    private static final double FAR_LOWER_FLOWERINTAKE_AFTER = 0.0;
    private static final double MID_LOWER_FLOWERINTAKE_AFTER = 0.0;

    // RED field coordinates. Edit only the hotfix(...) values for a particular
    // field; the ideal route stays in OptimalPoses. Every control point gets its
    // own explicit correction, including points that currently need none.
    private final Pose SHOOT_IN_LOWER_HIVE = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_LOWER_HIVE);
    private final Pose SHOOT_IN_UPPER_HIVE = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_UPPER_HIVE);
    private final Pose START = SHOOT_IN_LOWER_HIVE;

    private final Pose OPPONENT_PICK_UP = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP);
    private final Pose OPPONENT_PICK_UP_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP_CONTROL_1);
    private final Pose OPPONENT_PICK_UP_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP_CONTROL_2);
    private final Pose SHOOT_FROM_OPPONENT_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_OPPONENT_CONTROL_1);
    private final Pose SHOOT_FROM_OPPONENT_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_OPPONENT_CONTROL_2);
    private final Pose SHOOT_FROM_OPPONENT_CONTROL3 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_OPPONENT_CONTROL_3);
    private final Pose FAR_FLOWER_PICK_UP = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP_CONTROL_1);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP_CONTROL_2);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_2);
    private final Pose MID_FLOWER_PICK_UP = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP);
    private final Pose MID_FLOWER_PICK_UP_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP_CONTROL_1);
    private final Pose MID_FLOWER_PICK_UP_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP_CONTROL_2);

    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_FROM_MID_FLOWER_CONTROL);

    private final Pose INTAKE_GARDEN = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.INTAKE_GARDEN);
    private final Pose SHOOT_GARDEN_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.SHOOT_GARDEN_CONTROL_1);
    private final Pose PARK = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.PARK);
    private final Pose PARK_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.SoloWithStealRoute.PARK_CONTROL_1);

    @Override
    protected Pose starting_pose() {
        return field_pose(START);
    }

    @Override
    protected void build_autonomous() {
        // The half-filled lower hive tilts with the preload; switch to upper next.
        queue_shot(SHOT_DURATION_SECONDS);

        // Two consecutive loads into upper: opponent pollen, then the far flower.
        queue_intake_path(solo_with_steal_paths.opponent_pick_up(START, OPPONENT_PICK_UP_CONTROL1,
                OPPONENT_PICK_UP_CONTROL2, OPPONENT_PICK_UP), OPPONENT_LOWER_FLOWERINTAKE_AFTER);
        queue_shoot_path(shoot_in_upper_hive(OPPONENT_PICK_UP, SHOOT_FROM_OPPONENT_CONTROL1,
                SHOOT_FROM_OPPONENT_CONTROL2, SHOOT_FROM_OPPONENT_CONTROL3,
                SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(solo_with_steal_paths.far_flower_pick_up(SHOOT_IN_UPPER_HIVE, FAR_FLOWER_PICK_UP_CONTROL1,
                FAR_FLOWER_PICK_UP_CONTROL2, FAR_FLOWER_PICK_UP), FAR_LOWER_FLOWERINTAKE_AFTER);
        queue_shoot_path(shoot_in_upper_hive(FAR_FLOWER_PICK_UP, SHOOT_FROM_FAR_FLOWER_CONTROL1,
                SHOOT_FROM_FAR_FLOWER_CONTROL2, SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        // Upper has received both loads; switch to lower for mid flower and garden.
        queue_intake_path(solo_with_steal_paths.mid_flower_pick_up(SHOOT_IN_UPPER_HIVE, MID_FLOWER_PICK_UP_CONTROL1,
                MID_FLOWER_PICK_UP_CONTROL2, MID_FLOWER_PICK_UP), MID_LOWER_FLOWERINTAKE_AFTER);
        queue_shoot_path(shoot_in_lower_hive(MID_FLOWER_PICK_UP,
                SHOOT_FROM_MID_FLOWER_CONTROL, SHOOT_IN_LOWER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(solo_with_steal_paths.intake_garden(SHOOT_IN_LOWER_HIVE, INTAKE_GARDEN));
        queue_shoot_path(shoot_in_lower_hive(INTAKE_GARDEN,
                SHOOT_GARDEN_CONTROL1, SHOOT_IN_LOWER_HIVE), SHOT_DURATION_SECONDS);

        queue_path(solo_with_steal_paths.park(SHOOT_IN_LOWER_HIVE, PARK_CONTROL1, PARK));
    }
}
