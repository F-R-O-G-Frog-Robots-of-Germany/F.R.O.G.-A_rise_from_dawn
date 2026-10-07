package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | FAR_MID_GARDEN_PARK", group = "Blue")
public class BlueFarMidGardenPark extends FarMidGardenPark {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

