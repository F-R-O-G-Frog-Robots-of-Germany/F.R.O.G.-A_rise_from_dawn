package org.firstinspires.ftc.teamcode.core.pedro.procedures;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.localization.Localizer;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Vector2D;
import com.pedropathing.tuning.autotune.Inputs;
import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.TuningOpMode;
import com.pedropathing.utils.Angle;
import com.pedropathing.utils.Utils;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.pedropathing.utils.Utils.linearFit;
import static com.pedropathing.utils.Utils.quadraticFit;

/**
 * Pedro Pathing Quickstart v3.0.0 Foresight AutoTuner, adapted to this project's package.
 * Licensed under the Pedro Pathing BSD 3-Clause Clear License.
 */
public class ForesightTuner extends Procedure {
    // List.of requires Android API 30+. Older-device compatibility is intentionally deferred
    // because the team targets its selected newer devices; keep the Pedro procedures intact.
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;

    public ForesightTuner(Function<HardwareMap, Localizer> localizerFunction,
                          Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Foresight Tuner", "A procedure for tuning the Foresight Algorithm.");
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
    }

    @Override
    public void run() throws InterruptedException {
        Inputs distanceInput = inputs("Distance", "Distance in inches for maximum forward and strafe velocity tests.");
        Inputs.Field<Double> distance = distanceInput.d("Distance").withDefault(48.0);
        awaitInputs(distanceInput);

        double forwardVelocity = runOpMode(new ForwardVelocity(localizerFunction, drivetrainFunction, distance.get()));
        double strafeVelocity = runOpMode(new StrafeVelocity(localizerFunction, drivetrainFunction, distance.get()));

        Inputs velocityInput = inputs("Velocity", "Target velocity for the forward and strafe deceleration tests, in inches per second.");
        Inputs.Field<Double> velocity = velocityInput.d("Velocity").withDefault(30.0);
        awaitInputs(velocityInput);

        double forwardDeceleration = runOpMode(new ForwardDeceleration(localizerFunction, drivetrainFunction, velocity.get()));
        double strafeDeceleration = runOpMode(new StrafeDeceleration(localizerFunction, drivetrainFunction, velocity.get()));
        List<Double> headingBraking = runOpMode(new HeadingBraking(localizerFunction, drivetrainFunction));
        double heading = runOpMode(new HeadingTuner(localizerFunction, drivetrainFunction));
        double headingLinear = headingBraking.get(0);
        double headingQuadratic = headingBraking.get(1);

        Inputs brakingInput = inputs("Braking Distance", "Distance for forward/strafe braking tests. Ensure clear space in all directions.");
        Inputs.Field<Double> brakingDistance = brakingInput.d("Distance").withDefault(36.0);
        awaitInputs(brakingInput);

        List<Double> forwardBraking = runOpMode(new ForwardBraking(localizerFunction, drivetrainFunction,
                headingLinear, headingQuadratic, heading, brakingDistance.get()));
        List<Double> strafeBraking = runOpMode(new StrafeBraking(localizerFunction, drivetrainFunction,
                headingLinear, headingQuadratic, heading, brakingDistance.get()));
        List<Double> forwardTranslational = runOpMode(new ForwardTranslational(localizerFunction, drivetrainFunction));
        List<Double> strafeTranslational = runOpMode(new StrafeTranslational(localizerFunction, drivetrainFunction));

        double forwardLinear = forwardBraking.get(0);
        double forwardQuadratic = forwardBraking.get(1);
        double strafeLinear = strafeBraking.get(0);
        double strafeQuadratic = strafeBraking.get(1);
        double forwardPrimary = forwardTranslational.get(0);
        double forwardSecondary = forwardTranslational.get(1);
        double coast = forwardTranslational.get(2);
        double brake = forwardTranslational.get(3);
        double strafePrimary = strafeTranslational.get(0);
        double strafeSecondary = strafeTranslational.get(1);

        result("maxAchievableForwardVelocity", forwardVelocity);
        result("maxAchievableStrafeVelocity", strafeVelocity);
        result("naturalForwardDeceleration", forwardDeceleration);
        result("naturalStrafeDeceleration", strafeDeceleration);
        result("headingBrakingLinearCoefficient", headingLinear);
        result("headingBrakingQuadraticCoefficient", headingQuadratic);
        result("heading kP", heading);
        result("forwardBrakingLinearCoefficient", forwardLinear);
        result("forwardBrakingQuadraticCoefficient", forwardQuadratic);
        result("strafeBrakingLinearCoefficient", strafeLinear);
        result("strafeBrakingQuadraticCoefficient", strafeQuadratic);
        result("forwardTranslationalPrimary kP", forwardPrimary);
        result("forwardTranslationalSecondary kP", forwardSecondary);
        result("strafeTranslationalPrimary kP", strafePrimary);
        result("strafeTranslationalSecondary kP", strafeSecondary);
        result("coast kV", coast);
        result("brake kV", brake);

        code(Language.JAVA,
                "public static ForesightConfig foresightConfig = new ForesightConfig(c -> {\n" +
                " Controller primaryTranslationalForward = Controller.proportional(" + forwardPrimary + ");\n" +
                " Controller secondaryTranslationalForward = Controller.proportional(" + forwardSecondary + ");\n" +
                " Controller primaryTranslationalLateral = Controller.proportional(" + strafePrimary + ");\n" +
                " Controller secondaryTranslationalLateral = Controller.proportional(" + strafeSecondary + ");\n" +
                " c.forwardTranslational.set(Controller.piecewise(secondaryTranslationalForward).put(2.5, primaryTranslationalForward));\n" +
                " c.strafeTranslational.set(Controller.piecewise(secondaryTranslationalLateral).put(2.5, primaryTranslationalLateral));\n" +
                " c.coast.set(Controller.proportionalFeedforward(" + coast + "));\n" +
                " c.brake.set(Controller.proportionalFeedforward(" + brake + "));\n" +
                " c.headingFeedback.set(Controller.proportional(" + heading + "));\n" +
                " c.headingBrakeCoefficients.set(Vector2D.cartesian(" + headingLinear + ", " + headingQuadratic + "));\n" +
                " c.linearBrakeCoefficients.set(Matrix.diag(" + forwardLinear + ", " + strafeLinear + "));\n" +
                " c.quadraticBrakeCoefficients.set(Matrix.diag(" + forwardQuadratic + ", " + strafeQuadratic + "));\n" +
                " c.maxAchievableForwardVelocity.set(" + forwardVelocity + ");\n" +
                " c.maxAchievableStrafeVelocity.set(" + strafeVelocity + ");\n" +
                " c.naturalForwardDeceleration.set(" + forwardDeceleration + ");\n" +
                " c.naturalStrafeDeceleration.set(" + strafeDeceleration + ");\n" +
                "});");
    }
}

class ForwardVelocity extends TuningOpMode<Double> {
    private static final int RECORD_NUMBER = 10;
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final double distance;

    ForwardVelocity(Function<HardwareMap, Localizer> localizerFunction,
                    Function<HardwareMap, Drivetrain> drivetrainFunction, double distance) {
        super("Max Forward Velocity", "Drives forward for " + distance + " inches, then may coast past that point.", false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
        this.distance = distance;
    }

    @Override
    protected Double runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        ArrayDeque<Double> velocities = new ArrayDeque<>();
        for (int i = 0; i < RECORD_NUMBER; i++) velocities.add(0.0);
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        while (!isStopRequested() && Math.abs(localizer.pose().x()) <= distance) {
            drivetrain.drive(new DrivePowers(1, 0, 0), true);
            localizer.update();
            velocities.addLast(Math.abs(localizer.twist().toVector2D().x()));
            velocities.removeFirst();
        }
        drivetrain.stop();
        return average(velocities);
    }

    private static double average(ArrayDeque<Double> values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.size();
    }
}

class StrafeVelocity extends TuningOpMode<Double> {
    private static final int RECORD_NUMBER = 10;
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final double distance;

    StrafeVelocity(Function<HardwareMap, Localizer> localizerFunction,
                   Function<HardwareMap, Drivetrain> drivetrainFunction, double distance) {
        super("Max Strafe Velocity", "Drives left for " + distance + " inches, then may coast past that point.", false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
        this.distance = distance;
    }

    @Override
    protected Double runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        ArrayDeque<Double> velocities = new ArrayDeque<>();
        for (int i = 0; i < RECORD_NUMBER; i++) velocities.add(0.0);
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        while (!isStopRequested() && Math.abs(localizer.pose().y()) <= distance) {
            drivetrain.drive(new DrivePowers(0, 1, 0), false);
            localizer.update();
            velocities.addLast(Math.abs(localizer.twist().toVector2D().y()));
            velocities.removeFirst();
        }
        drivetrain.stop();
        double sum = 0.0;
        for (double value : velocities) sum += value;
        return sum / velocities.size();
    }
}

abstract class DecelerationTuner extends TuningOpMode<Double> {
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final double targetVelocity;
    private final boolean forward;

    DecelerationTuner(String name, String description, Function<HardwareMap, Localizer> localizerFunction,
                      Function<HardwareMap, Drivetrain> drivetrainFunction, double targetVelocity, boolean forward) {
        super(name, description, false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
        this.targetVelocity = targetVelocity;
        this.forward = forward;
    }

    @Override
    protected Double runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        ArrayList<Double> accelerations = new ArrayList<>();
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        drivetrain.drive(forward ? new DrivePowers(1, 0, 0) : new DrivePowers(0, 1, 0), false);
        double previousVelocity = 0.0;
        long previousTime = 0L;
        while (!isStopRequested()) {
            localizer.update();
            double velocity = forward ? localizer.twist().toVector2D().x() : localizer.twist().toVector2D().y();
            if (previousTime == 0L && Math.abs(velocity) > targetVelocity) {
                previousVelocity = velocity;
                previousTime = System.nanoTime();
                drivetrain.stop(false);
            } else if (previousTime != 0L) {
                long now = System.nanoTime();
                double dt = (now - previousTime) / 1e9;
                if (dt > 0) accelerations.add((velocity - previousVelocity) / dt);
                previousVelocity = velocity;
                previousTime = now;
                if (Math.abs(velocity) <= 1.0) break;
            }
        }
        drivetrain.stop(false);
        if (accelerations.isEmpty()) return 0.0;
        double total = 0.0;
        for (double acceleration : accelerations) total += acceleration;
        return Math.abs(total / accelerations.size());
    }
}

class ForwardDeceleration extends DecelerationTuner {
    ForwardDeceleration(Function<HardwareMap, Localizer> localizerFunction,
                        Function<HardwareMap, Drivetrain> drivetrainFunction, double velocity) {
        super("Forward Deceleration", "Drives forward to the selected velocity, then measures natural deceleration.", localizerFunction, drivetrainFunction, velocity, true);
    }
}

class StrafeDeceleration extends DecelerationTuner {
    StrafeDeceleration(Function<HardwareMap, Localizer> localizerFunction,
                       Function<HardwareMap, Drivetrain> drivetrainFunction, double velocity) {
        super("Strafe Deceleration", "Strafes left to the selected velocity, then measures natural deceleration.", localizerFunction, drivetrainFunction, velocity, false);
    }
}

class HeadingBraking extends TuningOpMode<List<Double>> {
    private static final double MAX_BRAKE_TIME = 3.0;
    private static final int TRIALS = 12;
    private static final double MAX_POWER = 1.0;
    private static final double MIN_POWER = 0.2;
    private static final double BIAS = 1.5;
    private static final double BRAKING_POWER = 0.001;
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final ElapsedTime timer = new ElapsedTime();

    HeadingBraking(Function<HardwareMap, Localizer> localizerFunction,
                   Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Heading Braking", "Turns back and forth at varying speeds to identify heading braking coefficients.", false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
    }

    @Override
    protected List<Double> runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        List<double[]> samples = new ArrayList<>();
        double[] powers = biased_gradient(TRIALS, MAX_POWER, MIN_POWER, BIAS);
        double totalHeading = 0.0;
        double previousHeading = 0.0;
        double startHeading = 0.0;
        double measuredVelocity = 0.0;
        int iteration = 0;
        boolean braking = false;
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        timer.reset();
        while (!isStopRequested() && iteration < powers.length) {
            localizer.update();
            double currentHeading = localizer.pose().heading();
            totalHeading += Angle.normalizeSigned(currentHeading - previousHeading);
            previousHeading = currentHeading;
            double direction = iteration % 2 == 0 ? 1.0 : -1.0;
            if (!braking) {
                drivetrain.drive(new DrivePowers(0, 0, powers[iteration] * direction), false);
                if (timer.seconds() > 2.0) {
                    startHeading = totalHeading;
                    measuredVelocity = Math.abs(localizer.velocity().omega);
                    braking = true;
                    timer.reset();
                }
            } else {
                drivetrain.drive(new DrivePowers(0, 0, -BRAKING_POWER * direction), false);
                if (Math.abs(localizer.velocity().omega) <= 0.001 || timer.seconds() >= MAX_BRAKE_TIME) {
                    samples.add(new double[]{measuredVelocity, Math.abs(totalHeading - startHeading)});
                    iteration++;
                    braking = false;
                    timer.reset();
                }
            }
        }
        drivetrain.stop();
        if (samples.size() < 2) return List.of(0.0, 0.0);
        double[] coefficients = quadraticFit(samples);
        return List.of(coefficients[0], coefficients[1]);
    }

    private static double[] biased_gradient(int count, double max, double min, double bias) {
        double[] values = new double[count];
        if (count == 1) {
            values[0] = max;
            return values;
        }
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            values[i] = min + (1.0 - Math.pow(t, bias)) * (max - min);
        }
        return values;
    }
}

class HeadingTuner extends TuningOpMode<Double> {
    private static final double POWER = 0.4;
    private static final double RUNTIME = 1.2;
    private static final int SAMPLES = 15;
    private static final double ALPHA = 18.25;
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final ElapsedTime timer = new ElapsedTime();

    HeadingTuner(Function<HardwareMap, Localizer> localizerFunction,
                 Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Heading Tuner", "Spins the robot briefly to identify heading feedback kP.", false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
    }

    @Override
    protected Double runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        List<Double> times = new ArrayList<>();
        List<Double> velocities = new ArrayList<>();
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        timer.reset();
        drivetrain.drive(new DrivePowers(0, 0, POWER), false);
        while (opModeIsActive() && timer.seconds() < RUNTIME) {
            localizer.update();
            times.add(timer.seconds());
            velocities.add(Math.abs(localizer.velocity().omega));
        }
        drivetrain.stop();
        if (times.size() < 4) return 0.0;
        int start = Math.max(0, times.size() - SAMPLES);
        double average = 0.0;
        for (int i = start; i < velocities.size(); i++) average += velocities.get(i);
        double k = average / (velocities.size() - start) / POWER;
        if (k <= 0.0) return 0.0;

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            double velocity = velocities.get(i) / POWER;
            if (velocity <= 0.1 * k || velocity >= 0.8 * k) continue;
            x.add(times.get(i));
            y.add(Math.log(k - velocity));
        }
        if (x.size() < 2) return 0.0;
        double[] fit = linearFit(x.toArray(new Double[0]), y.toArray(new Double[0]));
        if (fit[1] == 0.0) return 0.0;
        double tau = -1.0 / fit[1];
        return tau * ALPHA * ALPHA / k;
    }
}

abstract class BrakingTuner extends TuningOpMode<List<Double>> {
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final double headingLinear;
    private final double headingQuadratic;
    private final double headingKp;
    private final double distance;
    private final boolean forward;

    BrakingTuner(String name, String description, Function<HardwareMap, Localizer> localizerFunction,
                 Function<HardwareMap, Drivetrain> drivetrainFunction, double headingLinear,
                 double headingQuadratic, double headingKp, double distance, boolean forward) {
        super(name, description, false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
        this.headingLinear = headingLinear;
        this.headingQuadratic = headingQuadratic;
        this.headingKp = headingKp;
        this.distance = distance;
        this.forward = forward;
    }

    @Override
    protected List<Double> runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        List<double[]> samples = new ArrayList<>();
        int trials = 5;
        double[] powers = biased_gradient(trials, 0.7, 0.3, 1.5);
        ElapsedTime timer = new ElapsedTime();
        double start = 0.0;
        double velocity = 0.0;
        boolean braking = false;
        int iteration = 0;
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        timer.reset();
        while (opModeIsActive() && iteration < powers.length) {
            localizer.update();
            double position = forward ? localizer.pose().x() : localizer.pose().y();
            double signedVelocity = forward ? localizer.velocity().toVector2D().x() : localizer.velocity().toVector2D().y();
            double direction = iteration % 2 == 0 ? 1.0 : -1.0;
            if (!braking) {
                double target = iteration % 2 == 0 ? distance : 12.0;
                boolean reached = iteration % 2 == 0 ? Math.abs(position) >= target : Math.abs(position) <= target;
                drivetrain.drive(forward ? new DrivePowers(powers[iteration] * direction, 0, heading_power(localizer))
                        : new DrivePowers(0, powers[iteration] * direction, heading_power(localizer)), false);
                if (reached) {
                    start = position;
                    velocity = Math.abs(signedVelocity);
                    braking = true;
                    timer.reset();
                }
            } else {
                drivetrain.drive(forward ? new DrivePowers(-0.2 * direction, 0, heading_power(localizer))
                        : new DrivePowers(0, -0.2 * direction, heading_power(localizer)), false);
                if (Math.abs(signedVelocity) < 0.25 || timer.seconds() > 7.0) {
                    samples.add(new double[]{velocity, Math.abs(position - start)});
                    iteration++;
                    braking = false;
                    timer.reset();
                }
            }
        }
        drivetrain.stop();
        if (samples.size() < 2) return List.of(0.0, 0.0);
        double[] fit = quadraticFit(samples);
        return List.of(fit[0], fit[1]);
    }

    private double heading_power(Localizer localizer) {
        double angularVelocity = localizer.velocity().omega;
        double predictedBrake = headingLinear * angularVelocity
                + headingQuadratic * angularVelocity * angularVelocity * Math.signum(angularVelocity);
        double error = Angle.normalizeSigned(-localizer.pose().heading()) - predictedBrake;
        return Utils.clamp(headingKp * error, -0.3, 1.0) / 2.0;
    }

    private static double[] biased_gradient(int count, double max, double min, double bias) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.0 : (double) i / (count - 1);
            values[i] = min + (1.0 - Math.pow(t, bias)) * (max - min);
        }
        return values;
    }
}

class ForwardBraking extends BrakingTuner {
    ForwardBraking(Function<HardwareMap, Localizer> localizerFunction, Function<HardwareMap, Drivetrain> drivetrainFunction,
                   double headingLinear, double headingQuadratic, double headingKp, double distance) {
        super("Forward Braking", "Drives forward/backward to identify braking coefficients; leave substantial clear space.",
                localizerFunction, drivetrainFunction, headingLinear, headingQuadratic, headingKp, distance, true);
    }
}

class StrafeBraking extends BrakingTuner {
    StrafeBraking(Function<HardwareMap, Localizer> localizerFunction, Function<HardwareMap, Drivetrain> drivetrainFunction,
                  double headingLinear, double headingQuadratic, double headingKp, double distance) {
        super("Strafe Braking", "Strafes left/right to identify braking coefficients; leave substantial clear space.",
                localizerFunction, drivetrainFunction, headingLinear, headingQuadratic, headingKp, distance, false);
    }
}

class TranslationalTuner extends TuningOpMode<List<Double>> {
    private final Function<HardwareMap, Localizer> localizerFunction;
    private final Function<HardwareMap, Drivetrain> drivetrainFunction;
    private final boolean forward;

    TranslationalTuner(String name, String description, Function<HardwareMap, Localizer> localizerFunction,
                       Function<HardwareMap, Drivetrain> drivetrainFunction, boolean forward) {
        super(name, description, false);
        this.localizerFunction = localizerFunction;
        this.drivetrainFunction = drivetrainFunction;
        this.forward = forward;
    }

    @Override
    protected List<Double> runTuningOpMode() {
        Localizer localizer = localizerFunction.apply(hardwareMap);
        Drivetrain drivetrain = drivetrainFunction.apply(hardwareMap);
        List<Double> times = new ArrayList<>();
        List<Double> velocities = new ArrayList<>();
        ElapsedTime timer = new ElapsedTime();
        localizer.setPose(Pose.zero());
        localizer.update();
        waitForStart();
        timer.reset();
        drivetrain.drive(forward ? new DrivePowers(0.4, 0, 0) : new DrivePowers(0, 0.4, 0), false);
        while (opModeIsActive() && timer.seconds() < 1.2) {
            localizer.update();
            times.add(timer.seconds());
            double velocity = forward ? localizer.twist().toVector2D().x() : localizer.twist().toVector2D().y();
            velocities.add(Math.abs(velocity));
        }
        drivetrain.stop();
        if (times.size() < 4) return forward ? List.of(0.0, 0.0, 0.0, 0.0) : List.of(0.0, 0.0);
        int start = Math.max(0, times.size() - 15);
        double average = 0.0;
        for (int i = start; i < velocities.size(); i++) average += velocities.get(i);
        double k = average / (velocities.size() - start) / 0.4;
        if (k <= 0.0) return forward ? List.of(0.0, 0.0, 0.0, 0.0) : List.of(0.0, 0.0);
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            double normalizedVelocity = velocities.get(i) / 0.4;
            if (normalizedVelocity <= 0.1 * k || normalizedVelocity >= 0.8 * k) continue;
            x.add(times.get(i));
            y.add(Math.log(k - normalizedVelocity));
        }
        if (x.size() < 2) return forward ? List.of(0.0, 0.0, 0.0, 0.0) : List.of(0.0, 0.0);
        double[] fit = linearFit(x.toArray(new Double[0]), y.toArray(new Double[0]));
        if (fit[1] == 0.0) return forward ? List.of(0.0, 0.0, 0.0, 0.0) : List.of(0.0, 0.0);
        double tau = -1.0 / fit[1];
        double kpPrimary = tau * 10.2 * 10.2 / k;
        double kpSecondary = tau * 6.2 * 6.2 / k;
        if (forward) {
            double feedforward = 1.0 / k;
            return List.of(kpPrimary, kpSecondary, feedforward, feedforward * 0.85);
        }
        return List.of(kpPrimary, kpSecondary);
    }
}

class ForwardTranslational extends TranslationalTuner {
    ForwardTranslational(Function<HardwareMap, Localizer> localizerFunction,
                         Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Forward Translational", "Moves forward briefly to identify translational forward gains.", localizerFunction, drivetrainFunction, true);
    }
}

class StrafeTranslational extends TranslationalTuner {
    StrafeTranslational(Function<HardwareMap, Localizer> localizerFunction,
                        Function<HardwareMap, Drivetrain> drivetrainFunction) {
        super("Strafe Translational", "Strafes briefly to identify translational strafe gains.", localizerFunction, drivetrainFunction, false);
    }
}
