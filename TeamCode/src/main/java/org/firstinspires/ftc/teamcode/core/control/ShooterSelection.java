package org.firstinspires.ftc.teamcode.core.control;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** INIT-only target selection: X toggles both models; bumpers edit RPM; D-pad edits hood. */
public final class ShooterSelection {
    private boolean previousX, previousLeft, previousRight;
    private boolean previousUp, previousDown;

    public void update(Gamepad gamepad, Shooter shooter, Telemetry telemetry) {
        if (gamepad.x && !previousX) shooter.toggle_bypass();
        if (shooter.is_bypass_enabled()) {
            if (gamepad.left_bumper && !previousLeft) shooter.adjust_target_rpm(-100);
            if (gamepad.right_bumper && !previousRight) shooter.adjust_target_rpm(100);
            if (gamepad.dpad_up && !previousUp) shooter.adjust_hood_angle(1);
            if (gamepad.dpad_down && !previousDown) shooter.adjust_hood_angle(-1);
        }
        previousX = gamepad.x;
        previousLeft = gamepad.left_bumper;
        previousRight = gamepad.right_bumper;
        previousUp = gamepad.dpad_up;
        previousDown = gamepad.dpad_down;
        if (!CompConfig.init_telemetry_enabled()) return;
        telemetry.addData("Shooter target mode", shooter.is_bypass_enabled() ? "FIXED RPM + HOOD" : "DISTANCE MODELS");
        telemetry.addData("Fixed RPM", shooter.get_fixed_rpm());
        telemetry.addData("Fixed hood [deg]", shooter.get_fixed_hood_angle());
        telemetry.addLine("Gamepad 2: X = target mode; bumpers = RPM -/+100; D-pad up/down = hood +/-1 deg");
    }
}

