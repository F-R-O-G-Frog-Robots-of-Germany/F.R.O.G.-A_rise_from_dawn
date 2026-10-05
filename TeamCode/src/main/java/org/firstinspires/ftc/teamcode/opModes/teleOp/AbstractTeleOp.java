package org.firstinspires.ftc.teamcode.opModes.teleOp;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.follower.Follower;
import com.pedropathing.follower.ManualDrive;
import com.pedropathing.math.Pose;
import com.bylazar.panels.Panels;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.lastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.periodicRegistry;
import org.firstinspires.ftc.teamcode.core.control.taskManager;
import org.firstinspires.ftc.teamcode.core.inputs.InputManager;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.pedro.PoseMirroring;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.RobotState;

import java.util.List;

/**
 * Shared teleop lifecycle and drive loop.
 *
 * <p>Hardware discovery, follower construction, and all other allocations belong
 * before {@link #waitForStart()}. MANUAL bulk-cache reads go stale unless
 * {@link #clearBulkCache()} is called first; the base loop does this at the top
 * of every iteration, and subclasses must do the same in inner sensor-wait loops.</p>
 *
 * <p>Scheduling tasks after {@link #waitForStart()} allocates task-manager state
 * (including an {@code Entry}, task lambdas, and potentially a worker thread), so
 * tasks must be scheduled from {@link #onInit()}.</p>
 */
public abstract class AbstractTeleOp extends LinearOpMode {
    private static final Pose DEFAULT_STARTING_POSE = new Pose(9.0, 9.0, toRadians(90.0));

    protected double driveDirection = 1.0;
    protected double driveSpeed = 1.0;
    protected double vetoSpeed = 1.0;
    protected Alliance alliance;
    protected RobotState robotState;
    protected Follower follower;
    protected final InputManager inputManager = new InputManager();
    private final ElapsedTime loopTimer = new ElapsedTime();
    private Telemetry telemetryManager;

    private List<LynxModule> allHubs;

    @Override
    public final void runOpMode() throws InterruptedException {
        if (CompConfig.panelsEnabled()) {
            Panels.INSTANCE.enable();
        } else {
            Panels.INSTANCE.disable();
        }
        taskManager.init();
        periodicRegistry.clear();

        try {
            allHubs = hardwareMap.getAll(LynxModule.class);
            for (int i = 0; i < allHubs.size(); i++) {
                allHubs.get(i).setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
            }

            telemetryManager = telemetry;
            follower = Constants.createFollower(hardwareMap);
            setLimelightVideoEnabled(CompConfig.videoEnabled());
            initializeStartingPose();
            robotState = RobotState.INIT;
            onInit();

            if (CompConfig.telemetryEnabled()) {
                getTelemetryManager().addLine("Init");
                getTelemetryManager().update();
            }

            waitForStart();
            if (isStopRequested()) {
                return;
            }

            for (int i = 0; i < allHubs.size(); i++) {
                allHubs.get(i).setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
            }

            robotState = RobotState.TELEOP;
            onStart();

            if (CompConfig.loopTimeTelemetryEnabled()) loopTimer.reset();
            while (opModeIsActive()) {
                boolean showLoopTime = CompConfig.loopTimeTelemetryEnabled();
                double loopStartMs = showLoopTime ? loopTimer.milliseconds() : 0.0;
                clearBulkCache();

                inputManager.update(gamepad1, gamepad2);
                follower.update();
                periodicRegistry.runAll();
                opModeLoop();

                double loopTimeMs = showLoopTime ? loopTimer.milliseconds() - loopStartMs : 0.0;
                if (showLoopTime) {
                    getTelemetryManager().addData("Loop ms", loopTimeMs);
                }
                if (CompConfig.telemetryEnabled()) {
                    getTelemetryManager().addData("Hz", loopTimeMs > 0.0 ? 1000.0 / loopTimeMs : 0.0);
                    getTelemetryManager().addData("x [in]", follower.pose().x());
                    getTelemetryManager().addData("y [in]", follower.pose().y());
                    getTelemetryManager().addData("heading [°]", toDegrees(follower.pose().heading()));
                }
                if (showLoopTime || CompConfig.telemetryEnabled()) {
                    getTelemetryManager().update();
                }
            }
        } finally {
            try {
                opModeStop();
            } finally {
                taskManager.stopAll();
                periodicRegistry.clear();
            }
        }
    }
    /** Clear every hub's MANUAL bulk cache; required before each group of sensor reads. */
    protected final void clearBulkCache() {
        for (int i = 0; i < allHubs.size(); i++) {
            allHubs.get(i).clearBulkCache();
        }
    }

    /** Replace the default Driver Station telemetry sink with a joined sink if desired. */
    protected final void setTelemetryManager(Telemetry telemetryManager) {
        this.telemetryManager = telemetryManager != null ? telemetryManager : telemetry;
    }

    protected final Telemetry getTelemetryManager() {
        return telemetryManager != null ? telemetryManager : telemetry;
    }

    /** Future Limelight integration point; no Limelight object is created here yet. */
    protected void setLimelightVideoEnabled(boolean enabled) {
    }

    protected final void resetPose(double x, double y) {
        Pose startPose = new Pose(x, y, toRadians(90.0));
        follower.setPose(PoseMirroring.mirror_if_blue(startPose, alliance));
        follower.update();
    }

    private void initializeStartingPose() {
        if (lastPositionStorage.validDataAvailable()) {
            follower.setPose(lastPositionStorage.getLastPosition());
            alliance = lastPositionStorage.getCurrentAlliance();
        } else {
            alliance = Alliance.BLUE;
            follower.setPose(DEFAULT_STARTING_POSE);
            if (CompConfig.telemetryEnabled()) {
                getTelemetryManager().addLine("Starting pose not available");
                getTelemetryManager().update();
            }
        }

        if (alliance == Alliance.BLUE) {
            driveDirection = -1.0;
        }
        follower.update();
    }

    protected abstract void onInit();

    protected abstract void opModeStop();

    protected abstract void onStart();

    protected abstract void opModeLoop();
}
