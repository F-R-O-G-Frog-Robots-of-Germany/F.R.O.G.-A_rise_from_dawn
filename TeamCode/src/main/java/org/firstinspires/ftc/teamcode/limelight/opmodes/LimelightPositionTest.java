package org.firstinspires.ftc.teamcode.limelight.opmodes;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.LimelightCalibration;
import org.firstinspires.ftc.teamcode.limelight.LimelightGeometry;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
import org.firstinspires.ftc.teamcode.limelight.Pollen;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/** Runtime camera calibration. Copy measured values to LimelightGeometry to persist them. */
@Disabled
@TeleOp(name = "Limelight position test", group = "Limelight")
public final class LimelightPositionTest extends LinearOpMode {
    private final boolean[] previous = new boolean[14];
    private boolean pressed(int index, boolean down) {
        boolean edge = down && !previous[index];
        previous[index] = down;
        return edge;
    }

    @Override public void runOpMode() throws InterruptedException {
        Follower follower = null;
        Limelight camera = new Limelight(hardwareMap);
        PollenMap map = new PollenMap(camera);
        try {
            for (LynxModule hub : hardwareMap.getAll(LynxModule.class)) {
                hub.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
            }
            follower = Constants.create_follower(hardwareMap);
            follower.setPose(new Pose(72, 72, 0));
            PedroButineDrive drive = new PedroButineDrive(follower);
            camera.init();
            map.init();
            telemetry.setMsTransmissionInterval(100);
            telemetry.addLine("Bench start pose 72,72,0: place robot there or correct the pose before use.");
            telemetry.update();
            waitForStart();
            VisionPose reference = null;
            LimelightCalibration pinhole = new LimelightCalibration();
            LimelightCalibration quadratic = new LimelightCalibration();
            pinhole.usePinholeRange = true;
            quadratic.usePinholeRange = false;
            boolean details = true;
            long statusNanos = 0;
            while (opModeIsActive()) {
                follower.manual(-gamepad1.left_stick_y * 0.4, -gamepad1.left_stick_x * 0.4,
                        -gamepad1.right_stick_x * 0.4);
                follower.update();
                VisionPose here = drive.pose();
                camera.update(here);
                if (System.nanoTime() - statusNanos >= 250_000_000L) {
                    camera.update_full(here);
                    statusNanos = System.nanoTime();
                }
                map.update();
                if (pressed(0, gamepad1.a)) reference = here;
                if (pressed(1, gamepad1.b)) reference = null;
                if (pressed(2, gamepad1.x)) details = !details;
                if (pressed(3, gamepad1.y)) map.clear();
                if (pressed(4, gamepad1.back)) camera.set_pipeline(camera.pipeline() == Limelight.DETECTOR_PIPELINE
                        ? Limelight.APRILTAG_PIPELINE : Limelight.DETECTOR_PIPELINE);
                if (pressed(5, gamepad1.start)) camera.calibration.usePinholeRange = !camera.calibration.usePinholeRange;
                if (pressed(6, gamepad1.left_trigger > 0.5)) camera.calibration.cameraHeightIn =
                        Math.max(1, camera.calibration.cameraHeightIn - 0.5);
                if (pressed(7, gamepad1.right_trigger > 0.5)) camera.calibration.cameraHeightIn =
                        Math.min(60, camera.calibration.cameraHeightIn + 0.5);
                if (pressed(8, gamepad1.left_bumper)) camera.calibration.cameraPitchDeg =
                        Math.max(0, camera.calibration.cameraPitchDeg - 1);
                if (pressed(9, gamepad1.right_bumper)) camera.calibration.cameraPitchDeg =
                        Math.min(89, camera.calibration.cameraPitchDeg + 1);
                if (pressed(10, gamepad1.dpad_up)) {
                    VisionPose center = reference == null ? new VisionPose(72, 72, 0) : reference;
                    map.inject(new VisionPose(center.x + 24, center.y, 0), 0.9, 100);
                    map.inject(new VisionPose(center.x - 24, center.y, 0), 0.9, 100);
                    map.inject(new VisionPose(center.x, center.y + 24, 0), 0.9, 100);
                }
                if (pressed(11, gamepad1.dpad_down)) {
                    map.inject(new VisionPose(72, 72, 0), 0.9, 100);
                    map.inject(new VisionPose(36, 36, 0), 0.9, 100);
                    map.inject(new VisionPose(108, 108, 0), 0.9, 100);
                }
                camera.telemetry(telemetry);
                telemetry.addData("Odometry", "%.1f, %.1f @ %.1f deg", here.x, here.y, Math.toDegrees(here.heading));
                VisionPose fix = camera.get_pose();
                if (fix == null) telemetry.addData("AprilTag pose", "none; BACK switches pipelines");
                else {
                    telemetry.addData("AprilTag pose", "%.1f, %.1f @ %.1f deg", fix.x, fix.y, Math.toDegrees(fix.heading));
                    telemetry.addData("Tag minus odometry", "dx %.1f / dy %.1f / heading %.1f deg",
                            fix.x - here.x, fix.y - here.y, Math.toDegrees(VisionPose.wrap_pi(fix.heading - here.heading)));
                }
                telemetry.addData("Reference", reference == null ? "A to set" : reference.distance_to(here) + " in moved");
                telemetry.addData("Range model", camera.calibration.usePinholeRange ? "pinhole" : "quadratic placeholder");
                telemetry.addData("Height / pitch", "%.1f in / %.1f deg", camera.calibration.cameraHeightIn,
                        camera.calibration.cameraPitchDeg);
                telemetry.addData("Range at LL ty", camera.forward_distance(camera.get_ty()));
                pinhole.cameraHeightIn = camera.calibration.cameraHeightIn;
                pinhole.cameraPitchDeg = camera.calibration.cameraPitchDeg;
                telemetry.addData("Compare ranges", "pinhole %.1f / quadratic %.1f in",
                        pinhole.forward_distance(camera.get_ty()), quadratic.forward_distance(camera.get_ty()));
                for (double ty : new double[]{-20, -15, -10, -5, 0}) {
                    telemetry.addData("f(" + ty + ") in", camera.forward_distance(ty));
                }
                telemetry.addData("Map tracks", map.tracks().size());
                if (details) {
                    telemetry.addData("Camera offsets", "forward %.1f / left %.1f / yaw %.1f",
                            LimelightGeometry.CAMERA_FORWARD_OFFSET_IN, LimelightGeometry.CAMERA_LEFT_OFFSET_IN,
                            LimelightGeometry.CAMERA_YAW_DEG);
                    telemetry.addData("Camera FOV", "H %.1f / V %.1f", LimelightGeometry.HORIZONTAL_FOV_DEG,
                            LimelightGeometry.VERTICAL_FOV_DEG);
                    int i = 0;
                    VisionPose capture = camera.pollen_frame_pose();
                    telemetry.addData("Frame capture pose", "%.1f, %.1f @ %.1f deg", capture.x, capture.y,
                            Math.toDegrees(capture.heading));
                    for (Pollen pollen : camera.get_pollen()) {
                        if (i == 5) break;
                        telemetry.addData("Detection " + i++, "%.1f,%.1f confidence %.2f area %.1f",
                                pollen.position.x, pollen.position.y, pollen.confidence, pollen.area);
                    }
                    for (PollenMap.Track track : map.tracks()) {
                        if (i >= 10) break;
                        telemetry.addData("Track #" + track.id, "%.1f,%.1f confidence %.2f sightings %d",
                                track.position.x, track.position.y, track.confidence, track.sightings);
                        i++;
                    }
                }
                telemetry.addLine("A ref | B clear ref | X details | Y clear map | BACK pipeline | START model");
                telemetry.addLine("Triggers height -/+ | bumpers pitch -/+ | dpad UD inject");
                telemetry.update();
                idle();
            }
        } finally {
            try { camera.stop(); }
            finally { if (follower != null) {
                try { follower.stop(); } finally { follower.drivetrain.stop(); }
            } }
        }
    }
}
