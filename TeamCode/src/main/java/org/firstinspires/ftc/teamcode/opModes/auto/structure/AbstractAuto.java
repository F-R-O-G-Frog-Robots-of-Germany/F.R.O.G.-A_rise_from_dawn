package org.firstinspires.ftc.teamcode.opModes.auto.structure;

import com.bylazar.panels.Panels;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.core.control.lastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.taskManager;
import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.intake;
import org.firstinspires.ftc.teamcode.subsystems.shooter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Shared path actions and hardware lifecycle for linear autonomous modes. */
public abstract class AbstractAuto extends LinearOpMode {
    private static final String FLYWHEEL_TASK = "AbstractAutoFlywheel";
    private static final String STOP_SHOOTING_TASK = "AbstractAutoStopShooting";
    private static final String START_INTAKE_TASK = "AbstractAutoStartIntake";
    private static final double SHOOT_SPEED_TOLERANCE_RPM = 70.0;

    protected Follower follower;
    protected shooter shooterSubsystem;
    protected intake intakeSubsystem;

    private Alliance alliance;
    private volatile double targetRpm;
    private final ElapsedTime loopTimer = new ElapsedTime();

    protected abstract Pose startingPose();

    protected abstract void runAutonomous();

    protected final Alliance currentAlliance() {
        return alliance;
    }

    @Override
    public final void runOpMode() throws InterruptedException {
        if (CompConfig.panelsEnabled()) {
            Panels.INSTANCE.enable();
        } else {
            Panels.INSTANCE.disable();
        }

        alliance = lastPositionStorage.getCurrentAlliance();
        follower = Constants.createFollower(hardwareMap);
        follower.setPose(startingPose());
        follower.update();

        intakeSubsystem = new intake();
        intakeSubsystem.init(hardwareMap);
        shooterSubsystem = new shooter();
        shooterSubsystem.init(hardwareMap, intakeSubsystem, alliance);
        shooterSubsystem.setBypassEnabled(true);
        updateFlywheelTarget();
        taskManager.init();

        try {
            waitForStart();
            if (isStopRequested()) return;

            taskManager.schedule(FLYWHEEL_TASK, () -> shooterSubsystem.setFlywheelVelocity(
                    targetRpm, shooter.shooterMotor.raw().getVelocity()), Units.Time.ms(20));
            if (CompConfig.loopTimeTelemetryEnabled()) loopTimer.reset();
            try {
                runAutonomous();
            } catch (AutoStopped ignored) {
                // A path or shot was interrupted by the OpMode stopping.
            }
        } finally {
            taskManager.stopAll();
            shooterSubsystem.stop();
            intakeSubsystem.stop();
        }
    }

    /** Drive a path without starting either scoring action. */
    protected final void followPath(Path path) {
        runUntil(follower::atParametricEnd, () -> follower.follow(path), null);
    }

    /** Keep the intake on from the start of the path until it ends or the op mode stops. */
    protected final void followIntakePath(Path path) {
        try {
            runUntil(follower::atParametricEnd, () -> {
                intakeSubsystem.forward();
                follower.follow(path);
            }, null);
        } finally {
            intakeSubsystem.off();
        }
    }

    /**
     * Start intake for the last {@code intakeLastSeconds} of the estimated path time.
     * The scheduled start is cancelled if the path finishes before that time. If the
     * requested intake time covers the estimate, intake runs for the whole path.
     */
    protected final void followIntakePath(Path path, double estimatedPathSeconds,
                                          double intakeLastSeconds) {
        requirePositive(estimatedPathSeconds, "estimatedPathSeconds");
        requireNonNegative(intakeLastSeconds, "intakeLastSeconds");

        if (intakeLastSeconds >= estimatedPathSeconds) {
            followIntakePath(path);
            return;
        }
        if (intakeLastSeconds == 0.0) {
            followPath(path);
            return;
        }

        double intakeDelaySeconds = estimatedPathSeconds - intakeLastSeconds;
        AtomicBoolean startIntake = new AtomicBoolean();
        try {
            runUntil(follower::atParametricEnd, () -> {
                intakeSubsystem.off();
                follower.follow(path);
                taskManager.runOnceDelayed(START_INTAKE_TASK, () -> startIntake.set(true),
                        Units.Time.s(intakeDelaySeconds));
            }, () -> {
                if (startIntake.getAndSet(false)) intakeSubsystem.forward();
            });
        } finally {
            taskManager.cancel(START_INTAKE_TASK, false);
            intakeSubsystem.off();
        }
    }

    /** Drive to the path endpoint, then shoot there for the requested duration. */
    protected final void followShootPath(Path path, double shotDurationSeconds) {
        requirePositive(shotDurationSeconds, "shotDurationSeconds");
        followPath(path);
        shootAtCurrentPosition(shotDurationSeconds);
    }

    /** Shoot at the current pose without driving a path first. */
    protected final void shootAtCurrentPosition(double shotDurationSeconds) {
        requirePositive(shotDurationSeconds, "shotDurationSeconds");
        runUntil(this::flywheelAtShootingSpeed);

        AtomicBoolean shotTimeElapsed = new AtomicBoolean();
        try {
            runUntil(shotTimeElapsed::get, () -> {
                shooterSubsystem.shoot();
                taskManager.runOnceDelayed(STOP_SHOOTING_TASK, () -> shotTimeElapsed.set(true),
                        Units.Time.s(shotDurationSeconds));
            }, null);
        } finally {
            taskManager.cancel(STOP_SHOOTING_TASK, false);
            shooterSubsystem.stopShooting();
            intakeSubsystem.off();
        }
    }

    private void updateFlywheelTarget() {
        if (shooterSubsystem.isBypassEnabled()) {
            targetRpm = shooterSubsystem.getTargetRpm();
            return;
        }

        Pose currentPose = follower.pose();
        shooter.HiveDistance nearestHive = shooter.nearestHive(
                currentPose.x(), currentPose.y(), alliance);
        targetRpm = shooterSubsystem.calculateFlywheelVelocity(nearestHive.distance);
        shooterSubsystem.setTargetRpm(targetRpm);
    }

    private boolean flywheelAtShootingSpeed() {
        double currentRpm = shooterSubsystem.getCurrentRpm();
        double desiredRpm = shooterSubsystem.getTargetRpm();
        return Double.isFinite(currentRpm) && Double.isFinite(desiredRpm)
                && Math.abs(currentRpm - desiredRpm) <= SHOOT_SPEED_TOLERANCE_RPM;
    }

    private void runUntil(BooleanSupplier finished) {
        runUntil(finished, null, null);
    }

    private void runUntil(BooleanSupplier finished, Runnable onStart, Runnable beforeUpdate) {
        boolean started = false;
        while (true) {
            if (!opModeIsActive()) throw new AutoStopped();
            if (!started) {
                if (onStart != null) onStart.run();
                started = true;
            }
            if (finished.getAsBoolean()) return;
            if (beforeUpdate != null) beforeUpdate.run();
            updateFollowerAndTelemetry();
        }
    }

    private static final class AutoStopped extends RuntimeException {
    }

    private void updateFollowerAndTelemetry() {
        boolean showLoopTime = CompConfig.loopTimeTelemetryEnabled();
        double loopStartMs = showLoopTime ? loopTimer.milliseconds() : 0.0;
        follower.update();
        updateFlywheelTarget();
        if (showLoopTime) {
            telemetry.addData("Loop ms", loopTimer.milliseconds() - loopStartMs);
        }
        if (CompConfig.telemetryEnabled()) {
            telemetry.addData("x", follower.pose().x());
            telemetry.addData("y", follower.pose().y());
            telemetry.addData("heading", follower.pose().heading());
            if (follower.currentPath() != null) {
                telemetry.addData("Path distance remaining", follower.distanceToEndpoint());
                telemetry.addData("Path index", follower.pathIndex());
            }
        }
        if (showLoopTime || CompConfig.telemetryEnabled()) {
            telemetry.update();
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
    }
}
