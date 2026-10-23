package org.firstinspires.ftc.teamcode.opmodes.teleop;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.bylazar.panels.Panels;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import org.firstinspires.ftc.teamcode.core.control.CompConfig;
import org.firstinspires.ftc.teamcode.core.control.CompetitionWorkers;
import org.firstinspires.ftc.teamcode.core.control.ControlThreadAffinity;
import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.control.LoopTiming;
import org.firstinspires.ftc.teamcode.core.control.OpModeCleanup;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.pedro.PoseMirroring;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.RobotState;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

import java.util.Collections;
import java.util.List;

/**
 * Shared teleop lifecycle and drive loop.
 *
 * <p>Hardware discovery, follower construction, and all other allocations belong
 * before {@link #waitForStart()}. MANUAL bulk-cache reads go stale unless
 * {@link #clear_bulk_cache()} is called first; the base loop does this at the top
 * of every iteration, and subclasses must do the same in inner sensor-wait loops.</p>
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
    protected CompetitionWorkers workers;
    private ControlThreadAffinity controlAffinity;
    private final LoopTiming loopTiming = new LoopTiming();
    private Telemetry telemetryManager;
    private boolean usingFallbackStartingPose;

    private List<LynxModule> allHubs = Collections.emptyList();

    @Override
    public final void runOpMode() throws InterruptedException {
        Throwable failure = null;
        try {
            controlAffinity = create_control_affinity();
            initialize_hardware();
            on_init();
            workers = create_workers();
            configure_workers(workers);
            workers.start();
            controlAffinity.bind(control_core());

            if (CompConfig.telemetry_enabled()) {
                get_telemetry_manager().addLine("Init");
                get_telemetry_manager().update();
            }

            get_telemetry_manager().setMsTransmissionInterval(100);
            while (opModeInInit()) {
                follower.localizer.update();
                update_workers();
                currentPose = follower.pose();
                on_init_loop();
                if (CompConfig.init_telemetry_enabled()) {
                    get_telemetry_manager().addData("Control affinity", controlAffinity.detail());
                    workers.telemetry(get_telemetry_manager());
                    get_telemetry_manager().update();
                }
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

                update_controls();
                before_drive_update();
                follower.update();
                update_workers();
                currentPose = follower.pose();
                after_drive_update();
                op_mode_loop();

                loopTiming.end_work();
                if (showLoopTime) {
                    get_telemetry_manager().addData("Work ms", loopTiming.work_ms());
                    get_telemetry_manager().addData("Cycle ms", loopTiming.cycle_ms());
                    get_telemetry_manager().addData("Hz", loopTiming.hz());
                }
                if (CompConfig.telemetry_enabled() && currentPose != null) {
                    get_telemetry_manager().addData("x [in]", currentPose.x());
                    get_telemetry_manager().addData("y [in]", currentPose.y());
                    get_telemetry_manager().addData("heading [°]", toDegrees(currentPose.heading()));
                }
                if (showLoopTime || CompConfig.telemetry_enabled()) {
                    get_telemetry_manager().update();
                }
            }
        } catch (RuntimeException | Error stopped) {
            failure = stopped;
            throw stopped;
        } finally {
            OpModeCleanup.run(failure,
                    () -> { if (workers != null) workers.request_stop(); },
                    () -> { if (follower != null) follower.stop(); },
                    () -> { if (follower != null) follower.drivetrain.stop(); },
                    this::op_mode_stop,
                    () -> { if (workers != null && !workers.stop())
                        RobotLog.ww("Competition", "Worker still finishing after bounded shutdown"); },
                    () -> { if (controlAffinity != null && !controlAffinity.restore())
                        RobotLog.ww("Competition", controlAffinity.detail()); });
        }
    }

    protected void initialize_hardware() {
        if (CompConfig.panels_enabled()) Panels.INSTANCE.enable();
        else Panels.INSTANCE.disable();
        allHubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : allHubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        telemetryManager = telemetry;
        follower = Constants.create_follower(hardwareMap);
        set_limelight_video_enabled(CompConfig.video_enabled());
        initialize_starting_pose();
        robotState = RobotState.INIT;
    }

    protected boolean vision_enabled() { return CompConfig.VISION_ENABLED; }
    protected boolean planning_enabled() { return false; }
    protected int control_core() { return CompConfig.CONTROL_CORE; }
    protected ControlThreadAffinity create_control_affinity() { return new ControlThreadAffinity(); }
    protected CompetitionWorkers create_workers() {
        return new CompetitionWorkers(hardwareMap, follower, vision_enabled(), planning_enabled());
    }
    /** Camera/map calibration and worker core selection belong here, before their owners start. */
    protected void configure_workers(CompetitionWorkers workers) { }

    protected final void update_workers() { if (workers != null) workers.update(); }
    protected final boolean assistance_owns_drive() { return workers != null && workers.owns_drive(); }
    protected final void cancel_assistance() { if (workers != null) workers.cancel_drive(); }
    protected final boolean request_collection(VisionPose depot, Butine.Params params, double speedScale) {
        return workers != null && workers.request_collection(depot, params, speedScale);
    }
    protected final boolean request_relocalization() {
        return workers != null && workers.request_relocalization();
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

    /** Optional display hook; background detection does not require a camera video stream. */
    protected void set_limelight_video_enabled(boolean enabled) {
    }

    protected final void reset_pose(double x, double y) {
        cancel_assistance();
        Pose START_POSE = new Pose(x, y, toRadians(90.0));
        follower.setPose(PoseMirroring.mirror_if_blue(START_POSE, alliance));
        if (workers != null) workers.pose_changed();
        currentPose = follower.pose();
    }

    /** Change driver orientation; only an INIT fallback pose follows the alliance reflection. */
    protected final void set_alliance(Alliance alliance) {
        if (this.alliance != alliance) cancel_assistance();
        this.alliance = alliance;
        driveDirection = alliance == Alliance.BLUE ? -1.0 : 1.0;
        if (robotState == RobotState.INIT && usingFallbackStartingPose) {
            follower.setPose(PoseMirroring.mirror_if_blue(DEFAULT_STARTING_POSE, alliance));
            if (workers != null) workers.pose_changed();
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

    protected void update_controls() { }

    /** Supply this iteration's manual command before Pedro applies it in update(). */
    protected void before_drive_update() { }

    /** Check the fresh localization sample before the rest of this iteration proceeds. */
    protected void after_drive_update() { }

    protected abstract void op_mode_stop();

    protected abstract void on_start();

    protected abstract void op_mode_loop();
}
