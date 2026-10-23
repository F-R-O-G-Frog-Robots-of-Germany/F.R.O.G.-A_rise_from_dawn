package org.firstinspires.ftc.teamcode.core.control;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** INIT-only target selection: X toggles both models; bumpers edit RPM; D-pad edits hood. */
public final class ShooterSelection {
    public void update(Gamepad gamepad, Shooter shooter, Telemetry telemetry) {
        boolean left = gamepad.leftBumperWasPressed();
        boolean right = gamepad.rightBumperWasPressed();
        boolean up = gamepad.dpadUpWasPressed();
        boolean down = gamepad.dpadDownWasPressed();
        if (gamepad.xWasPressed()) shooter.toggle_bypass();
        if (shooter.is_bypass_enabled()) {
            if (left) shooter.adjust_target_rpm(-100);
            if (right) shooter.adjust_target_rpm(100);
            if (up) shooter.adjust_hood_angle(1);
            if (down) shooter.adjust_hood_angle(-1);
        }
        if (!CompConfig.init_telemetry_enabled()) return;
        telemetry.addData("Shooter target mode", shooter.is_bypass_enabled() ? "FIXED RPM + HOOD" : "DISTANCE MODELS");
        telemetry.addData("Fixed RPM", shooter.get_fixed_rpm());
        telemetry.addData("Fixed hood [deg]", shooter.get_fixed_hood_angle());
        telemetry.addLine("Gamepad 2: X = target mode; bumpers = RPM -/+100; D-pad up/down = hood +/-1 deg");
    }
}

