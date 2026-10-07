package org.firstinspires.ftc.teamcode.core.pedro.procedures;

import com.pedropathing.math.Pose;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.pedropathing.tuning.autotune.Inputs;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.TuningOpMode;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Pedro Pathing Quickstart v3.0.0 Pinpoint tuner, adapted to this project's package.
 * Licensed under the Pedro Pathing BSD 3-Clause Clear License.
 */
public class PinpointTuner extends Procedure {
    // List.of requires Android API 30+. Support for older devices is intentionally omitted
    // for the team's selected devices; the tuning algorithm remains unchanged.
    private enum PodType {
        SWING_ARM,
        FOUR_BAR,
        CUSTOM
    }

    public PinpointTuner() {
        super("Pinpoint Tuner", "A procedure for tuning the Pinpoint localizer.");
    }

    @Override
    public void run() throws InterruptedException {
        Inputs inputs = inputs("Setup", "Set Pinpoint HardwareMap Name and Odometry Pod Type");
        Inputs.Field<String> pinpointName = inputs.s("HardwareMap Name").withDefault("pinpoint");
        Inputs.Field<PodType> podType = inputs.e("Odometry Pod Type", PodType.class).withDefault(PodType.FOUR_BAR);
        awaitInputs(inputs);

        OptionalDouble customPodScalar = OptionalDouble.empty();
        if (podType.get() == PodType.CUSTOM) {
            Inputs customInputs = inputs("Custom Scalar Identification Push Distance", "Set the distance you will push your robot forward in inches");
            Inputs.Field<Double> distance = customInputs.d("Distance").withDefault(48.0);
            awaitInputs(customInputs);
            customPodScalar = OptionalDouble.of(runOpMode(new PinpointCustomPodScalar(distance.get(), pinpointName.get())));
        }

        boolean forwardPodReversed = runOpMode(new PinpointForwardDirection(pinpointName.get(), podType.get(), customPodScalar));
        boolean strafePodReversed = runOpMode(new PinpointStrafeDirection(pinpointName.get(), podType.get(), customPodScalar));
        List<Double> offsets = runOpMode(new PinpointOffsets(pinpointName.get(), podType.get(), customPodScalar, forwardPodReversed, strafePodReversed));

        result("name", pinpointName.get());
        if (customPodScalar.isPresent()) {
            result("podType", "Custom");
            result("ticksPerUnit", customPodScalar.getAsDouble());
        } else {
            result("podType", podType.get() == PodType.SWING_ARM
                    ? GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD
                    : GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        }
        result("xPodDirection", forwardPodReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
        result("yPodDirection", strafePodReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
        result("xPodOffset", offsets.get(0));
        result("yPodOffset", offsets.get(1));

        code(Language.JAVA, "public static PinpointConfig localizerConfig = new PinpointConfig(c -> {\n" +
                " c.name.set(\"" + pinpointName.get() + "\");\n" +
                (customPodScalar.isPresent()
                        ? " c.ticksPerUnit.set(OptionalDouble.of(" + customPodScalar.getAsDouble() + "));\n"
                        : " c.podType.set(" + (podType.get() == PodType.SWING_ARM
                                ? "GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD"
                                : "GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD") + ");\n") +
                " c.xPodOffset.set(" + offsets.get(0) + ");\n" +
                " c.yPodOffset.set(" + offsets.get(1) + ");\n" +
                " c.xPodDirection.set(" + (forwardPodReversed ? "GoBildaPinpointDriver.EncoderDirection.REVERSED" : "GoBildaPinpointDriver.EncoderDirection.FORWARD") + ");\n" +
                " c.yPodDirection.set(" + (strafePodReversed ? "GoBildaPinpointDriver.EncoderDirection.REVERSED" : "GoBildaPinpointDriver.EncoderDirection.FORWARD") + ");\n" +
                " c.globalDistanceUnit.set(DistanceUnit.INCH);\n" +
                " c.offsetUnits.set(DistanceUnit.INCH);\n" +
                "});");
    }

    private static PinpointConfig create_config(String name, PodType podType, OptionalDouble customPodScalar,
                                                boolean xReversed, boolean yReversed, double xOffset, double yOffset) {
        return new PinpointConfig(c -> {
            c.name.set(name);
            c.xPodDirection.set(xReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.yPodDirection.set(yReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.xPodOffset.set(xOffset);
            c.yPodOffset.set(yOffset);
            c.globalDistanceUnit.set(DistanceUnit.INCH);
            c.offsetUnits.set(DistanceUnit.INCH);
            if (customPodScalar.isPresent()) {
                c.encoderResolutionUnit.set(DistanceUnit.INCH);
                c.ticksPerUnit.set(OptionalDouble.of(customPodScalar.getAsDouble()));
            } else {
                c.podType.set(podType == PodType.SWING_ARM
                        ? GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD
                        : GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
            }
        });
    }

    private static class PinpointCustomPodScalar extends TuningOpMode<Double> {
        private final String name;
        private final double distance;

        PinpointCustomPodScalar(double distance, String name) {
            super("Custom Scalar Identification", "Push the robot forward " + distance + " inches, then stop the OpMode.", true);
            this.name = name;
            this.distance = distance;
        }

        @Override
        protected Double runTuningOpMode() {
            PinpointConfig config = create_config(name, PodType.CUSTOM, OptionalDouble.of(1.0), false, false, 0.0, 0.0);
            PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap, config);
            localizer.setPose(new Pose(0, 0));
            waitForStart();
            while (!isStopRequested()) {
                localizer.update();
            }
            return Math.abs(localizer.pose().x() / distance);
        }
    }

    private static class PinpointForwardDirection extends TuningOpMode<Boolean> {
        private final String name;
        private final PodType podType;
        private final OptionalDouble customPodScalar;

        PinpointForwardDirection(String name, PodType podType, OptionalDouble customPodScalar) {
            super("Forward Direction Identification", "Push the robot straight forward, then stop the OpMode.", true);
            this.name = name;
            this.podType = podType;
            this.customPodScalar = customPodScalar;
        }

        @Override
        protected Boolean runTuningOpMode() {
            PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap,
                    create_config(name, podType, customPodScalar, false, false, 0.0, 0.0));
            localizer.setPose(new Pose(0, 0));
            waitForStart();
            while (!isStopRequested()) {
                localizer.update();
            }
            return localizer.pose().x() < 0;
        }
    }

    private static class PinpointStrafeDirection extends TuningOpMode<Boolean> {
        private final String name;
        private final PodType podType;
        private final OptionalDouble customPodScalar;

        PinpointStrafeDirection(String name, PodType podType, OptionalDouble customPodScalar) {
            super("Strafe Direction Identification", "Push the robot to the left, then stop the OpMode.", true);
            this.name = name;
            this.podType = podType;
            this.customPodScalar = customPodScalar;
        }

        @Override
        protected Boolean runTuningOpMode() {
            PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap,
                    create_config(name, podType, customPodScalar, false, false, 0.0, 0.0));
            localizer.setPose(new Pose(0, 0));
            waitForStart();
            while (!isStopRequested()) {
                localizer.update();
            }
            return localizer.pose().y() < 0;
        }
    }

    private static class PinpointOffsets extends TuningOpMode<List<Double>> {
        private final String name;
        private final PodType podType;
        private final OptionalDouble customPodScalar;
        private final boolean forwardPodReversed;
        private final boolean strafePodReversed;
        private Pose PREVIOUS = Pose.zero();

        PinpointOffsets(String name, PodType podType, OptionalDouble customPodScalar,
                        boolean forwardPodReversed, boolean strafePodReversed) {
            super("Offsets Identification", "Rotate the robot in place 180 degrees counterclockwise, then stop the OpMode.", true);
            this.name = name;
            this.podType = podType;
            this.customPodScalar = customPodScalar;
            this.forwardPodReversed = forwardPodReversed;
            this.strafePodReversed = strafePodReversed;
        }

        @Override
        protected List<Double> runTuningOpMode() {
            PinpointConfig config = create_config(name, podType, customPodScalar,
                    forwardPodReversed, strafePodReversed, 0.0, 0.0);
            if (customPodScalar.isPresent()) {
                config = new PinpointConfig(c -> {
                    c.name.set(name);
                    c.xPodDirection.set(forwardPodReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
                    c.yPodDirection.set(strafePodReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
                    c.encoderResolutionUnit.set(DistanceUnit.INCH);
                    c.ticksPerUnit.set(OptionalDouble.of(customPodScalar.getAsDouble()));
                    c.resetMode.set(PinpointLocalizer.ResetMode.RESET_AND_RECALIBRATE_IMU);
                    c.xPodOffset.set(0.0);
                    c.yPodOffset.set(0.0);
                    c.globalDistanceUnit.set(DistanceUnit.INCH);
                    c.offsetUnits.set(DistanceUnit.INCH);
                });
            }
            PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap, config);
            if (customPodScalar.isPresent()) {
                localizer.reset();
            }
            localizer.setPose(Pose.zero());
            localizer.update();
            waitForStart();
            localizer.setPose(Pose.zero());
            while (!isStopRequested()) {
                PREVIOUS = localizer.pose();
                localizer.update();
                telemetry.addData("heading", localizer.pose().heading());
                telemetry.addData("pose", localizer.pose());
                telemetry.addData("previous", PREVIOUS);
                telemetry.update();
            }
            if (localizer.pose().x() != Pose.zero().x() || localizer.pose().y() != Pose.zero().y()) {
                PREVIOUS = localizer.pose();
            }
            return List.of(-PREVIOUS.y() / 2.0, -PREVIOUS.x() / 2.0);
        }
    }
}
