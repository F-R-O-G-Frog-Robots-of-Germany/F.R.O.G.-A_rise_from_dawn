package org.firstinspires.ftc.teamcode.opModes.auto.comp;

import com.pedropathing.api.Paths;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.core.pedro.PoseMirroring;
import org.firstinspires.ftc.teamcode.opModes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opModes.auto.structure.OptimalPoses;

import static org.firstinspires.ftc.teamcode.opModes.auto.structure.OptimalPoses.hotfix;

@Autonomous(name = "SoloWithSteal", group = "Autonomous")
public class SoloWithSteal extends AbstractAuto {
    private static final double SHOT_DURATION_SECONDS = 1.25;

    // RED field coordinates. Edit only the Hotfix(...) values for a particular
    // field; the ideal route stays in OptimalPoses. Every control point gets its
    // own explicit correction, including points that currently need none.
    private final Pose shootInLowerHive = hotfix().apply(OptimalPoses.SoloWithStealRoute.SHOOT_IN_LOWER_HIVE);
    private final Pose shootInUpperHive = hotfix().apply(OptimalPoses.SoloWithStealRoute.SHOOT_IN_UPPER_HIVE);
    private final Pose start = shootInLowerHive;

    private final Pose opponentPickUp = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP);
    private final Pose opponentPickUpControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP_CONTROL_1);
    private final Pose opponentPickUpControl2 = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_PICK_UP_CONTROL_2);
    private final Pose opponentDropOff = shootInUpperHive;
    private final Pose opponentDropOffControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_DROP_OFF_CONTROL_1);
    private final Pose opponentDropOffControl2 = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_DROP_OFF_CONTROL_2);
    private final Pose opponentDropOffControl3 = hotfix().apply(OptimalPoses.SoloWithStealRoute.OPPONENT_DROP_OFF_CONTROL_3);
    private final Pose farFlowerPickUp = hotfix().apply(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP);
    private final Pose farFlowerPickUpControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP_CONTROL_1);
    private final Pose farFlowerPickUpControl2 = hotfix().apply(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_PICK_UP_CONTROL_2);
    private final Pose farFlowerDropOff = shootInUpperHive;
    private final Pose farFlowerDropOffControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_DROP_OFF_CONTROL_1);
    private final Pose farFlowerDropOffControl2 = hotfix().apply(OptimalPoses.SoloWithStealRoute.FAR_FLOWER_DROP_OFF_CONTROL_2);
    private final Pose midFlowerPickUp = hotfix().apply(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP);
    private final Pose midFlowerPickUpControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP_CONTROL_1);
    private final Pose midFlowerPickUpControl2 = hotfix().apply(OptimalPoses.SoloWithStealRoute.MID_FLOWER_PICK_UP_CONTROL_2);

    private final Pose shootInUpperHiveControl = hotfix().apply(OptimalPoses.SoloWithStealRoute.SHOOT_IN_UPPER_HIVE_CONTROL);

    private final Pose intakeGarden = hotfix().apply(OptimalPoses.SoloWithStealRoute.INTAKE_GARDEN);
    private final Pose park = hotfix().apply(OptimalPoses.SoloWithStealRoute.PARK);
    private final Pose parkControl1 = hotfix().apply(OptimalPoses.SoloWithStealRoute.PARK_CONTROL_1);

    private Pose fieldPose(Pose redPose) {
        return PoseMirroring.mirror_if_blue(redPose, currentAlliance());
    }

    @Override
    protected Pose startingPose() {
        return fieldPose(start);
    }

    @Override
    protected void runAutonomous() {
        shootAtCurrentPosition(SHOT_DURATION_SECONDS);

        followIntakePath(opponentPickUp());
        followShootPath(opponentDropOff(), SHOT_DURATION_SECONDS);

        followIntakePath(farFlowerPickUp());
        followShootPath(farFlowerDropOff(), SHOT_DURATION_SECONDS);

        followIntakePath(midFlowerPickUp());
        followShootPath(shootInUpperHive(), SHOT_DURATION_SECONDS);

        followIntakePath(intakeGarden());
        followShootPath(shootInLowerHive(), SHOT_DURATION_SECONDS);

        followPath(park());
    }

    public Path opponentPickUp() {
        return Paths.curve(fieldPose(start), fieldPose(opponentPickUpControl1),
                fieldPose(opponentPickUpControl2), fieldPose(opponentPickUp)).tangent();
    }

    public Path opponentDropOff() {
        return Paths.curve(fieldPose(opponentPickUp), fieldPose(opponentDropOffControl1),
                fieldPose(opponentDropOffControl2), fieldPose(opponentDropOffControl3),
                fieldPose(opponentDropOff)).reverseTangent();
    }

    public Path farFlowerPickUp() {
        return Paths.curve(fieldPose(opponentDropOff), fieldPose(farFlowerPickUpControl1),
                fieldPose(farFlowerPickUpControl2), fieldPose(farFlowerPickUp)).tangent();
    }

    public Path farFlowerDropOff() {
        return Paths.curve(fieldPose(farFlowerPickUp), fieldPose(farFlowerDropOffControl1),
                fieldPose(farFlowerDropOffControl2), fieldPose(farFlowerDropOff)).reverseTangent();
    }

    public Path midFlowerPickUp() {
        return Paths.curve(fieldPose(farFlowerDropOff), fieldPose(midFlowerPickUpControl1),
                fieldPose(midFlowerPickUpControl2), fieldPose(midFlowerPickUp)).tangent();
    }

    public Path shootInUpperHive() {
        return Paths.curve(fieldPose(midFlowerPickUp), fieldPose(shootInUpperHiveControl),
                fieldPose(shootInUpperHive)).reverseTangent();
    }
    public Path intakeGarden() {
        return Paths.line(fieldPose(shootInUpperHive), fieldPose(intakeGarden)).tangent();
    }

    public Path shootInLowerHive() {
        return Paths.line(fieldPose(intakeGarden), fieldPose(shootInLowerHive)).tangent();
    }

    public Path park() {
        return Paths.curve(fieldPose(shootInLowerHive), fieldPose(parkControl1),
                fieldPose(park)).tangent();
    }
}
