package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | FAR_MID_PARK", group = "Blue")
public class BlueFarMidPark extends FarMidPark {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

