package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;

import static org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "RED | BothFlowersNoGarden", group = "Red")
public class BothFlowersNoGarden extends AbstractAuto {
    private static final double SHOT_DURATION_SECONDS = 1.25;
    // Placeholder seconds from each path start; tune these delays on the robot.
    // AbstractAuto raises the flower intake one second after lowering it,
    // or sooner when the path ends.
    private static final double FAR_LOWER_FLOWERINTAKE_AFTER = 1.0;
    private static final double MID_LOWER_FLOWERINTAKE_AFTER = 1.0;

    // RED field coordinates. Edit only the hotfix(...) values for a particular
    // field; the ideal route stays in OptimalPoses. Corrections precede BLUE mirroring.
    private final Pose SHOOT_IN_LOWER_HIVE = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_LOWER_HIVE);
    private final Pose SHOOT_IN_UPPER_HIVE = hotfix(0, 0, 0).plus(OptimalPoses.SHOOT_IN_UPPER_HIVE);
    private final Pose START = SHOOT_IN_LOWER_HIVE;

    private final Pose FAR_FLOWER_PICK_UP = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.FAR_FLOWER_PICK_UP);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.FAR_FLOWER_PICK_UP_CONTROL_1);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.FAR_FLOWER_PICK_UP_CONTROL_2);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL3 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.FAR_FLOWER_PICK_UP_CONTROL_3);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_2);

    private final Pose MID_FLOWER_PICK_UP = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.MID_FLOWER_PICK_UP);
    private final Pose MID_FLOWER_PICK_UP_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.MID_FLOWER_PICK_UP_CONTROL_1);
    private final Pose MID_FLOWER_PICK_UP_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.MID_FLOWER_PICK_UP_CONTROL_2);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.SHOOT_FROM_MID_FLOWER_CONTROL);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL2 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.SHOOT_FROM_MID_FLOWER_CONTROL_2);

    private final Pose PARK = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.PARK);
    private final Pose PARK_CONTROL1 = hotfix(0, 0, 0).plus(OptimalPoses.BothFlowersNoGardenRoute.PARK_CONTROL_1);

    @Override
    protected Pose starting_pose() {
        return field_pose(START);
    }

    @Override
    protected void build_autonomous() {
        // The starting hive is half filled: the preload completes its first tilt.
        queue_shot(SHOT_DURATION_SECONDS);

        // Switch to the upper hive and deliver both flower loads before parking.
        queue_intake_path(both_flowers_no_garden_paths.far_flower_pick_up(START,
                FAR_FLOWER_PICK_UP_CONTROL1, FAR_FLOWER_PICK_UP_CONTROL2,
                FAR_FLOWER_PICK_UP_CONTROL3, FAR_FLOWER_PICK_UP), FAR_LOWER_FLOWERINTAKE_AFTER);
        queue_shoot_path(shoot_in_upper_hive(FAR_FLOWER_PICK_UP,
                SHOOT_FROM_FAR_FLOWER_CONTROL1, SHOOT_FROM_FAR_FLOWER_CONTROL2,
                SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(both_flowers_no_garden_paths.mid_flower_pick_up(SHOOT_IN_UPPER_HIVE,
                MID_FLOWER_PICK_UP_CONTROL1, MID_FLOWER_PICK_UP_CONTROL2,
                MID_FLOWER_PICK_UP), MID_LOWER_FLOWERINTAKE_AFTER);
        queue_shoot_path(shoot_in_upper_hive(MID_FLOWER_PICK_UP,
                SHOOT_FROM_MID_FLOWER_CONTROL, SHOOT_FROM_MID_FLOWER_CONTROL2,
                SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_path(both_flowers_no_garden_paths.park(SHOOT_IN_UPPER_HIVE, PARK_CONTROL1, PARK));
    }
}
