package org.firstinspires.ftc.teamcode.opmodes.teleop;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.bylazar.panels.Panels;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.PeriodicRegistry;
import org.firstinspires.ftc.teamcode.core.control.TaskManager;
import org.firstinspires.ftc.teamcode.core.control.LoopTiming;
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
 * {@link #clear_bulk_cache()} is called first; the base loop does this at the top
 * of every iteration, and subclasses must do the same in inner sensor-wait loops.</p>
 *
 * <p>Scheduling tasks after {@link #waitForStart()} allocates task-manager state
 * (including an {@code Entry}, task lambdas, and potentially a worker thread), so
 * tasks must be scheduled from {@link #on_init()}.</p>
 */
public abstract class AbstractTeleOp extends LinearOpMode {
    private static final Pose DEFAULT_STARTING_POSE = new Pose(9.0, 9.0, toRadians(90.0));

    protected double driveDirection = 1.0;
    protected double driveSpeed = 1.0;
    protected double vetoSpeed = 1.0;
    protected Alliance alliance;
    protected RobotState robotState;
    protected Follower follower;
    protected Pose currentPose;
    protected final InputManager inputManager = new InputManager();
    private final LoopTiming loopTiming = new LoopTiming();
    private Telemetry telemetryManager;
    private boolean usingFallbackStartingPose;

    private List<LynxModule> allHubs;

    @Override
    public final void runOpMode() throws InterruptedException {
        try {
            if (CompConfig.panels_enabled()) {
                Panels.INSTANCE.enable();
            } else {
                Panels.INSTANCE.disable();
            }
            PeriodicRegistry.clear();

            allHubs = hardwareMap.getAll(LynxModule.class);
            for (int i = 0; i < allHubs.size(); i++) {
                allHubs.get(i).setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
            }

            telemetryManager = telemetry;
            follower = Constants.create_follower(hardwareMap);
            set_limelight_video_enabled(CompConfig.video_enabled());
            initialize_starting_pose();
            robotState = RobotState.INIT;
            on_init();

            if (CompConfig.telemetry_enabled()) {
                get_telemetry_manager().addLine("Init");
                get_telemetry_manager().update();
            }

            get_telemetry_manager().setMsTransmissionInterval(100);
            while (opModeInInit()) {
                on_init_loop();
                idle();
            }
            waitForStart();
            if (isStopRequested()) {
                return;
            }

            for (int i = 0; i < allHubs.size(); i++) {
                allHubs.get(i).setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
            }

            robotState = RobotState.TELEOP;
            on_start();

            while (opModeIsActive()) {
                boolean showLoopTime = CompConfig.loop_time_telemetry_enabled();
                loopTiming.begin();
                clear_bulk_cache();

                inputManager.update(gamepad1, gamepad2);
                before_drive_update();
                follower.update();
                currentPose = follower.pose();
                PeriodicRegistry.run_all();
                op_mode_loop();

                loopTiming.end_work();
                if (showLoopTime) {
                    get_telemetry_manager().addData("Work ms", loopTiming.work_ms());
                    get_telemetry_manager().addData("Cycle ms", loopTiming.cycle_ms());
                    get_telemetry_manager().addData("Hz", loopTiming.hz());
                }
                if (CompConfig.telemetry_enabled()) {
                    get_telemetry_manager().addData("x [in]", currentPose.x());
                    get_telemetry_manager().addData("y [in]", currentPose.y());
                    get_telemetry_manager().addData("heading [°]", toDegrees(currentPose.heading()));
                }
                if (showLoopTime || CompConfig.telemetry_enabled()) {
                    get_telemetry_manager().update();
                }
            }
        } finally {
            if (follower != null) {
                follower.stop();
                follower.drivetrain.stop();
            }
            try {
                op_mode_stop();
            } finally {
                TaskManager.stop_all();
                PeriodicRegistry.clear();
            }
        }
    }
    /** Clear every hub's MANUAL bulk cache; required before each group of sensor reads. */
    protected final void clear_bulk_cache() {
        for (int i = 0; i < allHubs.size(); i++) {
            allHubs.get(i).clearBulkCache();
        }
    }

    /** Replace the default Driver Station telemetry sink with a joined sink if desired. */
    protected final void set_telemetry_manager(Telemetry telemetryManager) {
        this.telemetryManager = telemetryManager != null ? telemetryManager : telemetry;
    }

    protected final Telemetry get_telemetry_manager() {
        return telemetryManager != null ? telemetryManager : telemetry;
    }

    /** Future Limelight integration point; no Limelight object is created here yet. */
    protected void set_limelight_video_enabled(boolean enabled) {
    }

    protected final void reset_pose(double x, double y) {
        Pose START_POSE = new Pose(x, y, toRadians(90.0));
        follower.setPose(PoseMirroring.mirror_if_blue(START_POSE, alliance));
        currentPose = follower.pose();
    }

    /** Change driver orientation; only an INIT fallback pose follows the alliance reflection. */
    protected final void set_alliance(Alliance alliance) {
        this.alliance = alliance;
        driveDirection = alliance == Alliance.BLUE ? -1.0 : 1.0;
        if (robotState == RobotState.INIT && usingFallbackStartingPose) {
            follower.setPose(PoseMirroring.mirror_if_blue(DEFAULT_STARTING_POSE, alliance));
            follower.localizer.update();
            currentPose = follower.pose();
        }
    }

    private void initialize_starting_pose() {
        usingFallbackStartingPose = !LastPositionStorage.valid_data_available();
        if (!usingFallbackStartingPose) {
            follower.setPose(LastPositionStorage.get_last_position());
            alliance = LastPositionStorage.get_current_alliance();
        } else {
            alliance = LastPositionStorage.get_current_alliance();
            follower.setPose(PoseMirroring.mirror_if_blue(DEFAULT_STARTING_POSE, alliance));
            if (CompConfig.telemetry_enabled()) {
                get_telemetry_manager().addLine("Starting pose not available");
                get_telemetry_manager().update();
            }
        }

        if (alliance == Alliance.BLUE) {
            driveDirection = -1.0;
        }
        follower.localizer.update();
        currentPose = follower.pose();
    }

    protected abstract void on_init();

    protected void on_init_loop() { }

    /** Supply this iteration's manual command before Pedro applies it in update(). */
    protected void before_drive_update() { }

    protected abstract void op_mode_stop();

    protected abstract void on_start();

    protected abstract void op_mode_loop();
}
