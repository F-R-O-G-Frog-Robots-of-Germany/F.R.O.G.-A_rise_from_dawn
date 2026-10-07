package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.Tuner;

import org.firstinspires.ftc.teamcode.core.pedro.procedures.ForesightTuner;
import org.firstinspires.ftc.teamcode.core.pedro.procedures.MecanumTuner;
import org.firstinspires.ftc.teamcode.core.pedro.procedures.PinpointTuner;
import org.firstinspires.ftc.teamcode.core.pedro.procedures.Tests;

/**
 * Pedro Pathing AutoTune procedure registration for this robot.
 *
 * <p>Adapted to this project's package and Constants from the Pedro-Pathing
 * Quickstart v3.0.0. Hardware names and calibration values still need to be
 * verified on the assembled robot.</p>
 */
public final class Tuning {
    private Tuning() {
    }

    @Tuner
    public static Procedure mecanum_tuner() {
        return new MecanumTuner();
    }

    @Tuner
    public static Procedure pinpoint_tuner() {
        return new PinpointTuner();
    }

    @Tuner
    public static Procedure foresight_tuner() {
        return new ForesightTuner(
                hardwareMap -> new PinpointLocalizer(hardwareMap, Constants.localizerConfig),
                hardwareMap -> new Mecanum(hardwareMap, Constants.drivetrainConfig)
        );
    }

    @Tuner
    public static Procedure tests() {
        return new Tests(
                hardwareMap -> new Mecanum(hardwareMap, Constants.drivetrainConfig),
                hardwareMap -> new PinpointLocalizer(hardwareMap, Constants.localizerConfig),
                () -> new Foresight(Constants.foresightConfig)
        );
    }
}
