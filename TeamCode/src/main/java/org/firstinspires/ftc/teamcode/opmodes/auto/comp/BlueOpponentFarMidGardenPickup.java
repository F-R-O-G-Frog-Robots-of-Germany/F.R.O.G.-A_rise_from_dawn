package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | OPPONENT_FAR_MID_GARDEN_PICKUP", group = "Blue")
public class BlueOpponentFarMidGardenPickup extends OpponentFarMidGardenPickup {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

