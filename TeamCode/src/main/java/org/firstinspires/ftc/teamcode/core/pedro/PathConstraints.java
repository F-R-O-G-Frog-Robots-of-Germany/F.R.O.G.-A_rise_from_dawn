package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.algorithm.ForesightConfig;

public abstract class PathConstraints {
    public static final ForesightConfig autoPilot = new ForesightConfig(c -> {
        c.brakeAggression.set(1.0);
        c.maxBrakingPower.set(0.2);
        c.maxAccelerationConstraint.set(200.0);
        c.maxVelocityConstraint.set(0.998);
        c.maxDecelerationConstraint.set(0.001);
        c.maxDecelerationScale.set(0.1);
        c.coastDownToVelocity.set(0.007);
    });
}
