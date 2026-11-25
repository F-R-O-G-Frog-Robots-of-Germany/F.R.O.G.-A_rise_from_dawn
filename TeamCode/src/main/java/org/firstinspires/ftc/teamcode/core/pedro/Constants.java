package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

public final class Constants {
    private Constants() {
    }

    // Change these names to match the Robot Controller configuration.
    private static final String FRONT_LEFT_MOTOR = "left_front";
    private static final String BACK_LEFT_MOTOR = "left_back";
    private static final String FRONT_RIGHT_MOTOR = "right_front";
    private static final String BACK_RIGHT_MOTOR = "right_back";
    private static final String PINPOINT_NAME = "pinpoint";
    private static final boolean PREFER_COMPACT_PINPOINT_BULK_READS = true;

    public static final MecanumConfig drivetrainConfig = new MecanumConfig(c -> {
        c.frontLeftName.set(FRONT_LEFT_MOTOR);
        c.backLeftName.set(BACK_LEFT_MOTOR);
        c.frontRightName.set(FRONT_RIGHT_MOTOR);
        c.backRightName.set(BACK_RIGHT_MOTOR);

        c.frontLeftDirection.set(DcMotorSimple.Direction.REVERSE);
        c.backLeftDirection.set(DcMotorSimple.Direction.REVERSE);
        c.frontRightDirection.set(DcMotorSimple.Direction.FORWARD);
        c.backRightDirection.set(DcMotorSimple.Direction.FORWARD);
        c.manualBrakeMode.set(true);
    });

    public static final PinpointConfig localizerConfig = new PinpointConfig(c -> {
        c.name.set(PINPOINT_NAME);
        // The forward (X) pod is right of center, and the strafe (Y) pod is ahead of center.
        c.xPodOffset.set(DistanceUnit.INCH.fromUnit(DistanceUnit.CM, -20.0));
        c.yPodOffset.set(DistanceUnit.INCH.fromUnit(DistanceUnit.CM, 10.5));
        c.offsetUnits.set(DistanceUnit.INCH);
        c.globalDistanceUnit.set(DistanceUnit.INCH);
        c.encoderResolutionUnit.set(DistanceUnit.INCH);
        c.podType.set(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
        c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
    });

    public static final ForesightConfig foresightConfig = new ForesightConfig(c -> {
        Controller translationalForward = Controller.proportional(0.078);
        Controller translationalLateral = Controller.proportional(0.078);

        c.forwardTranslational.set(translationalForward);
        c.strafeTranslational.set(translationalLateral);
        c.headingFeedback.set(Controller.proportional(1.11));
        c.coast.set(Controller.proportionalFeedforward(0.038));
        c.brake.set(Controller.proportionalFeedforward(0.025));

        c.headingBrakeCoefficients.set(Vector2D.cartesian(0.025, 0.0));
        c.linearBrakeCoefficients.set(Matrix.diag(0.01, 0.01));
        c.quadraticBrakeCoefficients.set(Matrix.diag(0.00008, 0.00008));

        c.maxBrakingPower.set(0.2);
        c.maxAccelerationConstraint.set(200.0);
        c.maxVelocityConstraint.set(75.0);
        c.maxDecelerationConstraint.set(79.0);
        c.maxDecelerationScale.set(1.0);
        c.brakeAggression.set(1.0);
        c.coastDownToVelocity.set(0.0);

        c.maxAchievableForwardVelocity.set(75.0);
        c.maxAchievableStrafeVelocity.set(59.0);
        c.naturalForwardDeceleration.set(46.0);
        c.naturalStrafeDeceleration.set(79.0);

        c.parametricTConstraint.set(0.002);
        c.velocityConstraint.set(0.1);
        c.translationalConstraint.set(0.1);
        c.headingConstraint.set(0.007);
        c.timeoutConstraint.set(150.0);
    });

    public static Follower create_follower(HardwareMap hardwareMap) {
        return new Follower(
                PinpointBulkReads.create(hardwareMap, localizerConfig, PREFER_COMPACT_PINPOINT_BULK_READS),
                new Mecanum(hardwareMap, drivetrainConfig),
                new Foresight(foresightConfig)
        );
    }

    public static PinpointLocalizer create_tuning_localizer(HardwareMap hardwareMap, PinpointConfig config) {
        return PinpointBulkReads.create(hardwareMap, config, false);
    }

    public static PinpointLocalizer create_optional_pinpoint_localizer(HardwareMap hardwareMap) {
        if (hardwareMap.tryGet(GoBildaPinpointDriver.class, localizerConfig.name.get()) == null) return null;
        return PinpointBulkReads.create(hardwareMap, localizerConfig, PREFER_COMPACT_PINPOINT_BULK_READS);
    }
}
