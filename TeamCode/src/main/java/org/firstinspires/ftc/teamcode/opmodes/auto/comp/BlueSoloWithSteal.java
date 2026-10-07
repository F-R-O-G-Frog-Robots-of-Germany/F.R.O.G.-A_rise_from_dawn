package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | SoloWithSteal", group = "Blue")
public class BlueSoloWithSteal extends SoloWithSteal {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

