package org.firstinspires.ftc.teamcode.core.control;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** INIT-only target selection: X toggles the model; bumpers edit the remembered fixed RPM. */
public final class ShooterSelection {
    private boolean previousX, previousLeft, previousRight;

    public void update(Gamepad gamepad, Shooter shooter, Telemetry telemetry) {
        if (gamepad.x && !previousX) shooter.toggle_bypass();
        if (shooter.is_bypass_enabled()) {
            if (gamepad.left_bumper && !previousLeft) shooter.adjust_target_rpm(-100);
            if (gamepad.right_bumper && !previousRight) shooter.adjust_target_rpm(100);
        }
        previousX = gamepad.x;
        previousLeft = gamepad.left_bumper;
        previousRight = gamepad.right_bumper;
        if (!CompConfig.init_telemetry_enabled()) return;
        telemetry.addData("Shooter target mode", shooter.is_bypass_enabled() ? "FIXED RPM" : "DISTANCE MODEL");
        telemetry.addData("Fixed RPM", shooter.get_fixed_rpm());
        telemetry.addLine("Gamepad 2: X = target mode; bumpers = fixed RPM -/+100");
    }
}

