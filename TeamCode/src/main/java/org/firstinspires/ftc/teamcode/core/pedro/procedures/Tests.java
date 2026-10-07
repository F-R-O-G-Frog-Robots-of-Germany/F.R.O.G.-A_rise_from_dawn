package org.firstinspires.ftc.teamcode.core.pedro.procedures;

import com.pedropathing.algorithm.Algorithm;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.interpolator.Interpolator;
import com.pedropathing.tuning.autotune.DisplayName;
import com.pedropathing.tuning.autotune.Inputs;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.TuningOpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.pedropathing.api.Paths.curve;
import static com.pedropathing.api.Paths.line;

/**
 * Pedro Pathing Quickstart v3.0.0 follower tests, adapted to this project's package.
 * Licensed under the Pedro Pathing BSD 3-Clause Clear License.
 */
public class Tests extends Procedure {
    private enum Test {
        @DisplayName("Hold Test") HOLD,
        @DisplayName("Line Test") LINE,
        @DisplayName("Curve Test") CURVED,
        @DisplayName("Interpolation Test") INTERPOLATION_CURVED,
        @DisplayName("Localization Test") LOCALIZATION,
        @DisplayName("Driving Test") DRIVING,
        @DisplayName("Pose Test") POSE
    }

    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Supplier<Algorithm> algorithmSupplier;
    private Function<HardwareMap, Follower> followerFunction;

    public Tests(Function<HardwareMap, Drivetrain> drivetrainFunction,
                 Function<HardwareMap, Localizer> localizerFunction,
                 Supplier<Algorithm> algorithmSupplier) {
        super("Tests", "A procedure for testing the Follower.");
        this.drivetrainFunction = drivetrainFunction;
        this.localizerFunction = localizerFunction;
        this.algorithmSupplier = algorithmSupplier;
    }

    @Override
    public void run() throws InterruptedException {
        boolean hasAlgorithm = algorithmSupplier != null;
        boolean hasLocalizer = localizerFunction != null;
        boolean hasDrivetrain = drivetrainFunction != null;
        boolean completed = false;

        if (hasAlgorithm && hasLocalizer && hasDrivetrain) {
            followerFunction = hardwareMap -> new Follower(
                    localizerFunction.apply(hardwareMap),
                    drivetrainFunction.apply(hardwareMap),
                    algorithmSupplier.get());
        }

        Inputs inputs = inputs("Select", "Select a follower test.");
        Inputs.Field<Test> selectedTest = inputs.e("Test", Test.class).withDefault(Test.LINE);
        Inputs.Field<Double> distance = inputs.d("Distance [in]").withDefault(48.0);
        awaitInputs(inputs);

        switch (selectedTest.get()) {
            case HOLD:
                require(hasAlgorithm, "Algorithm is required for Hold Test.");
                completed = runOpMode(new TestsHold(followerFunction));
                break;
            case LINE:
                require(hasAlgorithm, "Algorithm is required for Line Test.");
                completed = runOpMode(new TestsLine(followerFunction, distance.get()));
                break;
            case CURVED:
                require(hasAlgorithm, "Algorithm is required for Curve Test.");
                completed = runOpMode(new TestsCurve(followerFunction, distance.get()));
                break;
            case INTERPOLATION_CURVED:
                require(hasAlgorithm, "Algorithm is required for Interpolation Test.");
                completed = runOpMode(new TestsInterpolation(followerFunction, distance.get()));
                break;
            case LOCALIZATION:
                require(hasDrivetrain, "Drivetrain is required for Localization Test.");
                require(hasLocalizer, "Localizer is required for Localization Test.");
                completed = runOpMode(new TestsLocalization(drivetrainFunction, localizerFunction));
                break;
            case POSE:
                require(hasLocalizer, "Localizer is required for Pose Test.");
                completed = runOpMode(new TestsPose(localizerFunction));
                break;
            case DRIVING:
                require(hasDrivetrain, "Drivetrain is required for Driving Test.");
                completed = runOpMode(new TestsDriving(drivetrainFunction));
                break;
        }
        result("Completed", completed);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

class TestsHold extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Follower> followerFunction;

    TestsHold(Function<HardwareMap, Follower> followerFunction) {
        super("Hold Test", "Tests the Follower's ability to hold a position.", true);
        this.followerFunction = followerFunction;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Follower follower = followerFunction.apply(hardwareMap);
        follower.setPose(Pose.zero());
        waitForStart();
        follower.hold(Pose.zero());
        while (opModeIsActive()) {
            follower.update();
        }
        return true;
    }
}

class TestsLine extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Follower> followerFunction;
    private final double distance;

    TestsLine(Function<HardwareMap, Follower> followerFunction, double distance) {
        super("Line Test", "Tests the Follower's ability to follow a line.", true);
        this.followerFunction = followerFunction;
        this.distance = distance;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Follower follower = followerFunction.apply(hardwareMap);
        Pose START = Pose.zero();
        Pose END = new Pose(distance, 0, 0);
        Path forwardPath = line(START, END).constant(0);
        Path reversePath = line(END, START).constant(0);
        boolean forward = true;
        follower.setPose(START);
        waitForStart();
        follower.follow(forwardPath);
        while (opModeIsActive()) {
            follower.update();
            if (follower.atParametricEnd()) {
                forward = !forward;
                follower.follow(forward ? forwardPath : reversePath);
            }
        }
        return true;
    }
}

class TestsCurve extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Follower> followerFunction;
    private final double distance;

    TestsCurve(Function<HardwareMap, Follower> followerFunction, double distance) {
        super("Curve Test", "Tests the Follower's ability to follow a quarter-circle-like curve.", true);
        this.followerFunction = followerFunction;
        this.distance = distance;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Follower follower = followerFunction.apply(hardwareMap);
        Pose START = Pose.zero();
        Pose CORNER = new Pose(distance, 0);
        Pose END = new Pose(distance, distance);
        Path forwardPath = curve(START, CORNER, END).tangent();
        Path reversePath = curve(END, CORNER, START).tangent();
        boolean forward = true;
        follower.setPose(START);
        waitForStart();
        follower.follow(forwardPath);
        while (opModeIsActive()) {
            follower.update();
            if (follower.atParametricEnd()) {
                forward = !forward;
                follower.follow(forward ? forwardPath : reversePath);
            }
        }
        return true;
    }
}

class TestsInterpolation extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Follower> followerFunction;
    private final double distance;

    TestsInterpolation(Function<HardwareMap, Follower> followerFunction, double distance) {
        super("Interpolation Curve Test", "Tests curved paths with different heading interpolations.", true);
        this.followerFunction = followerFunction;
        this.distance = distance;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Follower follower = followerFunction.apply(hardwareMap);
        Pose START = Pose.zero();
        Pose CORNER = new Pose(distance, 0);
        Pose END = new Pose(distance, distance);
        Path forwardPath = curve(START, CORNER, END).heading((path, t) -> Math.PI);
        Path reversePath = curve(END, CORNER, START)
                .heading(Interpolator.piecewise().until(0.5, Interpolator.tangent).until(1.0, Interpolator.constant(0)));
        boolean forward = true;
        follower.setPose(START);
        waitForStart();
        follower.follow(forwardPath);
        while (opModeIsActive()) {
            follower.update();
            if (follower.atParametricEnd()) {
                forward = !forward;
                follower.follow(forward ? forwardPath : reversePath);
            }
        }
        return true;
    }
}

class TestsLocalization extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final Function<HardwareMap, Localizer> localizerFunction;

    TestsLocalization(Function<HardwareMap, Drivetrain> drivetrainFunction,
                      Function<HardwareMap, Localizer> localizerFunction) {
        super("Localization Test", "Verifies localization and manual control.", true);
        this.drivetrainFunction = drivetrainFunction;
        this.localizerFunction = localizerFunction;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        localizer.setPose(Pose.zero());
        waitForStart();
        while (opModeIsActive()) {
            drivetrain.drive(new DrivePowers(-gamepad1.left_stick_y, -gamepad1.left_stick_x, -gamepad1.right_stick_x), false);
            localizer.update();
            telemetry.addData("Pose", localizer.pose());
            telemetry.update();
        }
        return true;
    }
}

class TestsDriving extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;

    TestsDriving(Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Driving Test", "Tests raw drivetrain control without localization.", true);
        this.drivetrainFunction = drivetrainFunction;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        waitForStart();
        while (opModeIsActive()) {
            drivetrain.drive(new DrivePowers(-gamepad1.left_stick_y, -gamepad1.left_stick_x, -gamepad1.right_stick_x), true);
        }
        return true;
    }
}

class TestsPose extends TuningOpMode<Boolean> {
    private final Function<HardwareMap, Localizer> localizerFunction;

    TestsPose(Function<HardwareMap, Localizer> localizerFunction) {
        super("Pose Test", "Displays localizer pose while the robot is moved by hand.", true);
        this.localizerFunction = localizerFunction;
    }

    @Override
    protected Boolean runTuningOpMode() throws InterruptedException {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        waitForStart();
        localizer.setPose(Pose.zero());
        while (opModeIsActive()) {
            localizer.update();
            telemetry.addData("Pose", localizer.pose());
            telemetry.update();
        }
        return true;
    }
}
