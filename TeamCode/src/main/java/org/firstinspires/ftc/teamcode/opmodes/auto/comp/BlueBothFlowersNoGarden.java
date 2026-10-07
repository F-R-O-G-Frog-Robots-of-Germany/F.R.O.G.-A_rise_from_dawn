package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | BothFlowersNoGarden", group = "Blue")
public class BlueBothFlowersNoGarden extends BothFlowersNoGarden {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

