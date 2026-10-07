// PRELOAD->SHOOTLOWERHIVE->OPPONENT_FLOWER->SHOOTUPPERHIVE->FAR_FLOWER->SHOOTUPPERHIVE->MID_FLOWER->SHOOTLOWERHIVE->GARDEN
package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.OpponentFarMidGardenPickupRoute;

import static org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "RED | OPPONENT_FAR_MID_GARDEN_PICKUP", group = "Red")
public class OpponentFarMidGardenPickup extends AbstractAuto {
    private static final double SHOT_DURATION_SECONDS = 1.25;
    // Seconds after path start; tune flower lowering delays on the robot.
    private static final double OPPONENT_FLOWER_LOWER_AFTER_SECONDS = 0.0;
    private static final double FAR_FLOWER_LOWER_AFTER_SECONDS = 0.0;
    private static final double MID_FLOWER_LOWER_AFTER_SECONDS = 1.0;

    // Field corrections apply to RED geometry before BLUE mirroring.
    private final Pose SHOOT_IN_LOWER_HIVE = hotfix(0, 0, 0)
            .plus(OptimalPoses.SHOOT_IN_LOWER_HIVE);
    private final Pose SHOOT_IN_UPPER_HIVE = hotfix(0, 0, 0)
            .plus(OptimalPoses.SHOOT_IN_UPPER_HIVE);
    private final Pose START = SHOOT_IN_LOWER_HIVE;

    private final Pose OPPONENT_FLOWER_PICK_UP = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.OPPONENT_FLOWER_PICK_UP);
    private final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.OPPONENT_FLOWER_PICK_UP_CONTROL_1);
    private final Pose OPPONENT_FLOWER_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.OPPONENT_FLOWER_PICK_UP_CONTROL_2);
    private final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2);
    private final Pose SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3);
    private final Pose FAR_FLOWER_PICK_UP = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.FAR_FLOWER_PICK_UP);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.FAR_FLOWER_PICK_UP_CONTROL_1);
    private final Pose FAR_FLOWER_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.FAR_FLOWER_PICK_UP_CONTROL_2);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_FAR_FLOWER_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_FAR_FLOWER_CONTROL_2);
    private final Pose MID_FLOWER_PICK_UP = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.MID_FLOWER_PICK_UP);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.MID_FLOWER_PICK_UP_CONTROL_1);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.MID_FLOWER_PICK_UP_CONTROL_2);
    private final Pose MID_FLOWER_PICK_UP_CONTROL_3 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.MID_FLOWER_PICK_UP_CONTROL_3);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_MID_FLOWER_CONTROL_1);
    private final Pose SHOOT_FROM_MID_FLOWER_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.SHOOT_FROM_MID_FLOWER_CONTROL_2);
    private final Pose GARDEN_PICK_UP = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.GARDEN_PICK_UP);
    private final Pose GARDEN_PICK_UP_CONTROL_1 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.GARDEN_PICK_UP_CONTROL_1);
    private final Pose GARDEN_PICK_UP_CONTROL_2 = hotfix(0, 0, 0)
            .plus(OpponentFarMidGardenPickupRoute.GARDEN_PICK_UP_CONTROL_2);

    @Override
    protected Pose starting_pose() {
        return field_pose(START);
    }

    @Override
    protected void build_autonomous() {
        queue_shot(SHOT_DURATION_SECONDS);

        queue_intake_path(tangent_path(START, OPPONENT_FLOWER_PICK_UP_CONTROL_1,
                OPPONENT_FLOWER_PICK_UP_CONTROL_2, OPPONENT_FLOWER_PICK_UP), OPPONENT_FLOWER_LOWER_AFTER_SECONDS);

        queue_shoot_path(shoot_in_upper_hive(OPPONENT_FLOWER_PICK_UP,
                SHOOT_FROM_OPPONENT_FLOWER_CONTROL_1, SHOOT_FROM_OPPONENT_FLOWER_CONTROL_2,
                SHOOT_FROM_OPPONENT_FLOWER_CONTROL_3, SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(tangent_path(SHOOT_IN_UPPER_HIVE, FAR_FLOWER_PICK_UP_CONTROL_1,
                FAR_FLOWER_PICK_UP_CONTROL_2, FAR_FLOWER_PICK_UP), FAR_FLOWER_LOWER_AFTER_SECONDS);

        queue_shoot_path(shoot_in_upper_hive(FAR_FLOWER_PICK_UP, SHOOT_FROM_FAR_FLOWER_CONTROL_1,
                SHOOT_FROM_FAR_FLOWER_CONTROL_2, SHOOT_IN_UPPER_HIVE), SHOT_DURATION_SECONDS);

        queue_intake_path(tangent_path(SHOOT_IN_UPPER_HIVE, MID_FLOWER_PICK_UP_CONTROL_1,
                MID_FLOWER_PICK_UP_CONTROL_2, MID_FLOWER_PICK_UP_CONTROL_3, MID_FLOWER_PICK_UP), MID_FLOWER_LOWER_AFTER_SECONDS);

        queue_shoot_path(shoot_in_lower_hive(MID_FLOWER_PICK_UP, SHOOT_FROM_MID_FLOWER_CONTROL_1,
                SHOOT_FROM_MID_FLOWER_CONTROL_2, SHOOT_IN_LOWER_HIVE), SHOT_DURATION_SECONDS);

        // Finish holding the GARDEN load; this export has no return or park path.
        queue_intake_path(tangent_path(SHOOT_IN_LOWER_HIVE, GARDEN_PICK_UP_CONTROL_1,
                GARDEN_PICK_UP_CONTROL_2, GARDEN_PICK_UP));
    }
}
