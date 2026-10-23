package org.firstinspires.ftc.teamcode.limelight.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.SnapshotTrainer;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/** Camera-only bench tool; snapshot controls can also be added to the driver's TeleOp. */
@Disabled
@TeleOp(name = "Limelight NN trainer", group = "Limelight")
public final class LimelightNeuralNetworkTrainer extends LinearOpMode {
    @Override public void runOpMode() throws InterruptedException {
        Limelight camera = new Limelight(hardwareMap);
        SnapshotTrainer trainer = new SnapshotTrainer(camera);
        try {
            camera.init();
            telemetry.addLine("Gamepad 2: dpad left snapshot; dpad right toggle every 2 seconds");
            telemetry.update();
            waitForStart();
            gamepad2.resetEdgeDetection();
            gamepad2.copy(gamepad2);
            long statusNanos = 0;
            while (opModeIsActive()) {
                camera.update(VisionPose.ZERO);
                if (System.nanoTime() - statusNanos >= 250_000_000L) {
                    camera.update_full(VisionPose.ZERO);
                    statusNanos = System.nanoTime();
                }
                if (gamepad2.dpadLeftWasPressed()) trainer.take_snapshot();
                if (gamepad2.dpadRightWasPressed()) trainer.toggle_auto_capture();
                trainer.update();
                camera.telemetry(telemetry);
                trainer.telemetry(telemetry);
                telemetry.update();
                idle();
            }
        } finally {
            trainer.set_auto_capture(false);
            camera.stop();
        }
    }
}
