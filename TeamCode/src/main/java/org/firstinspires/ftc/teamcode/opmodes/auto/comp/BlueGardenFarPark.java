package org.firstinspires.ftc.teamcode.opmodes.auto.comp;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

/** The same RED route geometry is mirrored once by AbstractAuto during INIT. */
@Autonomous(name = "BLUE | GARDEN_FAR_PARK", group = "Blue")
public class BlueGardenFarPark extends GardenFarPark {
    @Override
    protected Alliance autonomous_alliance() { return Alliance.BLUE; }
}

