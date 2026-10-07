// PRELOAD->SHOOTLOWERHIVE->FAR_FLOWER->SHOOTUPPERHIVE->MID_FLOWER->SHOOTUPPERHIVE->PARK
package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.FarMidParkRoute;

import static org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "RED | FAR_MID_PARK", group = "Red")
public class FarMidPark extends AbstractAuto {
    private static final double SHOT_DURATION_SECONDS = 1.25;
    // Seconds after path start; tune flower lowering delays on the robot.
    private static final double FAR_FLOWER_LOWER_AFTER_SECONDS = 1.0;
    private static final double MID_FLOWER_LOWER_AFTER_SECONDS = 1.0;

    // Field corrections apply to RED geometry before BLUE mirroring.
    private final Pose SHOOT_IN_LOWER_HIVE = hotfix(0, 0, 0)
            .plus(OptimalPoses.SHOOT_IN_LOWER_HIVE);
    private final Pose SHOOT_IN_UPPER_HIVE = hotfix(0, 0, 0)
            .plus(OptimalPoses.SHOOT_IN_UPPER_HIVE);
    private final Pose START = SHOOT_IN_LOWER_HIVE;

    private final Pose FAR_FLOWER_PICK_UP = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.FAR_FLOWER_PICK_UP);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.FAR_FLOWER_PICK_UP_CONTROL_1);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.FAR_FLOWER_PICK_UP_CONTROL_2);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL_3 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.FAR_FLOWER_PICK_UP_CONTROL_3);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_2);
    private final Pose MID_FLOWER_PICK_UP = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.MID_FLOWER_PICK_UP);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.MID_FLOWER_PICK_UP_CONTROL_1);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.MID_FLOWER_PICK_UP_CONTROL_2);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_3 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.MID_FLOWER_PICK_UP_CONTROL_3);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.SHOOT_FROM_MID_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.SHOOT_FROM_MID_FLOWER_CONTROL_2);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL_3 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.SHOOT_FROM_MID_FLOWER_CONTROL_3);
    private final Pose PARK = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.PARK);
    private final Pose PARK_CONTROL_1 = hotfix(0, 0, 0)
            .plus(FarMidParkRoute.PARK_CONTROL_1);

    @Override
    protected Pose starting_pose() {
        return field_pose(START);
    }

    @Override
    protected void build_autonomous() {
        queue_shot(SHOT_DURATION_SECONDS);

        queue_intake_path(tangent_path(START, FAR_FLOWER_PICK_UP_CONTROL_1,
                FAR_FLOWER_PICK_UP_CONTROL_2, FAR_FLOWER_PICK_UP_CONTROL_3, FAR_FLOWER_PICK_UP), FAR_FLOWER_LOWER_AFTER_SECONDS);

        queue_shoot_path(shoot_in_upper_hive(FAR_FLOWER_PICK_UP, SHOOT_FROM_FAR_FLOWER_CONTROL_1,
                SHOOT_FROM_FAR_FLOWER_CONTROL_2, SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(tangent_path(SHOOT_IN_UPPER_HIVE, MID_FLOWER_PICK_UP_CONTROL_1,
                MID_FLOWER_PICK_UP_CONTROL_2, MID_FLOWER_PICK_UP_CONTROL_3, MID_FLOWER_PICK_UP), MID_FLOWER_LOWER_AFTER_SECONDS);

        queue_shoot_path(shoot_in_upper_hive(MID_FLOWER_PICK_UP, SHOOT_FROM_MID_FLOWER_CONTROL_1,
                SHOOT_FROM_MID_FLOWER_CONTROL_2, SHOOT_FROM_MID_FLOWER_CONTROL_3, SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_path(tangent_path(SHOOT_IN_UPPER_HIVE, PARK_CONTROL_1, PARK));
    }
}
