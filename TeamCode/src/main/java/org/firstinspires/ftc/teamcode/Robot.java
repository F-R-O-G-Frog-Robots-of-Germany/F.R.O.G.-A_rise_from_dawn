package org.firstinspires.ftc.teamcode;

import com.bylazar.telemetry.JoinedTelemetry;
import com.bylazar.telemetry.PanelsTelemetry;
import com.bylazar.telemetry.TelemetryManager;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.core.control.LastPositionStorage;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/**keep this file clean and make sure to add methods clearly related to one subsystem to this subsystem**/
public final class Robot {
    private HardwareMap hardwareMap;
    private Follower follower;
    private Telemetry telemetryManager;
    private Alliance alliance;
    private final Shooter shooterSubsystem = new Shooter();
    private final Intake intakeSubsystem = new Intake();
    private final FlowerIntake flowerIntakeSubsystem = new FlowerIntake();

    /** Keep references to shared robot services and initialize known subsystems. */
    public void init_robot(HardwareMap hardwareMap, Follower follower, Alliance alliance,
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
    public Telemetry get_telemetry_manager() {
        return telemetryManager;
    }

    /** Keep shooter targeting and the stored OpMode handoff on the selected alliance. */
    public void set_alliance(Alliance alliance) {
        this.alliance = alliance;
        shooterSubsystem.set_alliance(alliance);
    }

    /** Perform one-time actions when the OpMode starts. */
    public void start_robot() {
        // Per-run modes and outputs are initialized before START by each subsystem.
    }

    /** Update mechanisms once per OpMode loop; drive localization is updated by AbstractTeleOp. */
    public void update_robot() {
        if (follower != null && alliance != null) {
            Pose POSE = follower.pose();
            shooterSubsystem.update(POSE.x(), POSE.y());
        }
    }

    public void update_robot(Pose pose) {
        shooterSubsystem.update(pose.x(), pose.y());
    }

    /** Access the shooter subsystem for OpMode-specific controls. */
    public Shooter get_shooter() {
        return shooterSubsystem;
    }

    /** Access the intake subsystem for OpMode-specific controls. */
    public Intake get_intake() {
        return intakeSubsystem;
    }

    /** Access the flower intake subsystem for OpMode-specific controls. */
    public FlowerIntake get_flower_intake() {
        return flowerIntakeSubsystem;
    }

    /** Stop mechanism outputs and remember the final pose for the next OpMode. */
    public void stop_robot() {
        if (follower != null) {
            follower.stop();
            follower.drivetrain.stop();
        }
        shooterSubsystem.stop();
        intakeSubsystem.stop();
        flowerIntakeSubsystem.up();
        if (follower != null && alliance != null) {
            follower.localizer.update();
            LastPositionStorage.store_data(follower.pose(), alliance);
        }
    }
}
