package org.firstinspires.ftc.teamcode;

import com.bylazar.telemetry.JoinedTelemetry;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.control.lastPositionStorage;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.intake;
import org.firstinspires.ftc.teamcode.subsystems.shooter;

/**keep this file clean and make sure to add methods clearly related to one subsystem to this subsystem**/
public final class Robot {
    private HardwareMap hardwareMap;
    private Follower follower;
    private Telemetry telemetryManager;
    private Alliance alliance;
    private final shooter shooterSubsystem = new shooter();
    private final intake intakeSubsystem = new intake();
    private final FlowerIntake flowerIntakeSubsystem = new FlowerIntake();

    /** Keep references to shared robot services and initialize known subsystems. */
    public void initRobot(HardwareMap hardwareMap, Follower follower, Alliance alliance,
                          Telemetry driverStationTelemetry, boolean panelsEnabled) {
        this.hardwareMap = hardwareMap;
        this.follower = follower;
        this.alliance = alliance;

        if (panelsEnabled) {
            TelemetryManager panelsTelemetry = PanelsTelemetry.INSTANCE.getTelemetry();
            telemetryManager = new JoinedTelemetry(
                    panelsTelemetry.getWrapper(), driverStationTelemetry);
        } else {
            telemetryManager = driverStationTelemetry;
        }

        intakeSubsystem.init(hardwareMap);
        shooterSubsystem.init(hardwareMap, intakeSubsystem, alliance);
        flowerIntakeSubsystem.init(hardwareMap);
    }

    /** Telemetry destination shared by robot and subsystem diagnostics. */
    public Telemetry getTelemetryManager() {
        return telemetryManager;
    }

    /** Perform one-time actions when the OpMode starts. */
    public void startRobot() {
        // TODO: Reset per-run subsystem state when the robot design is finalized.
    }

    /** Update mechanisms once per OpMode loop; drive localization is updated by AbstractTeleOp. */
    public void updateRobot() {
        if (follower != null && alliance != null) {
            Pose pose = follower.pose();
            shooterSubsystem.update(pose.x(), pose.y());
        }
    }

    /** Access the shooter subsystem for OpMode-specific controls. */
    public shooter getShooter() {
        return shooterSubsystem;
    }

    /** Access the intake subsystem for OpMode-specific controls. */
    public intake getIntake() {
        return intakeSubsystem;
    }

    /** Access the flower intake subsystem for OpMode-specific controls. */
    public FlowerIntake getFlowerIntake() {
        return flowerIntakeSubsystem;
    }

    /** Stop mechanism outputs and remember the final pose for the next OpMode. */
    public void stopRobot() {
        shooterSubsystem.stop();
        intakeSubsystem.stop();
        if (follower != null && alliance != null) {
            lastPositionStorage.storeData(follower.pose(), alliance);
        }
    }
}
