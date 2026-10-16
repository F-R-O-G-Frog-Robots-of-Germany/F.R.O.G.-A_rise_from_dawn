package org.firstinspires.ftc.teamcode.limelight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Input-equivalence and independently calculated geometry regression audit. */
public final class VisionAuditChecks {
    private static int checks, failures;

    public static void main(String[] args) throws Exception {
        sequence_order();
        pinhole_geometry();
        fast_pose_history();
        bounded_map_work();
        invalid_input_matrix();
        frustum_boundaries();
        calibrated_frustum();
        System.out.println("Vision audit assertions: " + checks + ", failures: " + failures);
        if (failures > 0) throw new AssertionError("Vision audit failures: " + failures);
    }

    private static void sequence_order() {
        PollenMap map = new PollenMap();
        VisionPose robot = new VisionPose(20, 20, 0);
        map.update(Collections.singletonList(new Pollen(-1, new VisionPose(40, 20, 0), 1, 1)), robot, 3);
        int id = map.snapshot().get(0).id;
        map.update(Collections.emptyList(), robot, 2);
        require(map.snapshot().size() == 1 && map.snapshot().get(0).id == id,
                "Sequence 2 cannot erase newer sequence 3");
        map.update(Collections.emptyList(), robot, 3);
        require(map.snapshot().size() == 1, "Repeated sequence 3 preserves the latest map");
        map.update(Collections.emptyList(), robot, 4);
        require(map.snapshot().isEmpty(), "New empty sequence 4 clears its wedge");
    }

    private static void pinhole_geometry() {
        LimelightCalibration calibration = new LimelightCalibration();
        calibration.usePinholeRange = true;
        calibration.cameraHeightIn = 10;
        double[] pitches = {10, 30, 60};
        double[] txs = {-25, -10, 0, 10, 25};
        double[] tys = {-15, 0, 15};
        double[] headings = {0, Math.PI / 2, Math.PI, -Math.PI / 2};
        for (double pitch : pitches) for (double tx : txs) for (double ty : tys) {
            calibration.cameraPitchDeg = pitch;
            double p = Math.toRadians(pitch), a = Math.toRadians(tx), b = Math.toRadians(ty);
            // Independent 3D ray [right=tan(tx), up=tan(ty), opticalForward=1].
            double floorDown = Math.sin(p) - Math.tan(b) * Math.cos(p);
            double cameraForward = Math.cos(p) + Math.tan(b) * Math.sin(p);
            double expectedForward = 10 * cameraForward / floorDown;
            double expectedRight = 10 * Math.tan(a) / floorDown;
            for (double heading : headings) {
                VisionPose actual = calibration.project(tx, ty, new VisionPose(72, 72, heading));
                if (floorDown <= 0 || expectedForward <= 0 || expectedForward > 120) {
                    require(actual == null, "Pinhole non-floor ray is rejected");
                } else {
                    close(actual == null ? Double.NaN : actual.x,
                            72 + expectedForward * Math.cos(heading) + expectedRight * Math.sin(heading),
                            "Pitched off-axis projection x: pitch=" + pitch + " tx=" + tx + " ty=" + ty);
                    close(actual == null ? Double.NaN : actual.y,
                            72 + expectedForward * Math.sin(heading) - expectedRight * Math.cos(heading),
                            "Pitched off-axis projection y: pitch=" + pitch + " tx=" + tx + " ty=" + ty);
                }
            }
        }
    }

    private static void fast_pose_history() throws Exception {
        Limelight camera = new Limelight();
        Method push = Limelight.class.getDeclaredMethod("push_pose", long.class, VisionPose.class);
        Method at = Limelight.class.getDeclaredMethod("pose_at", long.class);
        push.setAccessible(true); at.setAccessible(true);
        for (long t = 0; t <= 600; t++) push.invoke(camera, t, new VisionPose(t / 10.0, 0, 0));
        VisionPose captured = (VisionPose) at.invoke(camera, 100L);
        close(captured.x, 10, "500 ms capture pose survives a 1 ms application loop");
        captured = (VisionPose) at.invoke(camera, 595L);
        close(captured.x, 59.5, "Recent capture pose still interpolates accurately");
    }

    private static void bounded_map_work() {
        PollenMap map = new PollenMap();
        List<Pollen> rejected = new ArrayList<>();
        for (int i = 0; i < 10000; i++) rejected.add(null);
        map.update(rejected, VisionPose.ZERO, 1);
        require(map.stats().rejected <= PollenMap.HARD_MAX_DETECTIONS,
                "Rejected input also obeys the hard per-frame work cap");
        map.config.maxTracks = 64;
        for (int i = 0; i < 64; i++) map.inject(new VisionPose(1 + i, 100, 0));
        map.config.maxTracks = 2;
        map.update(Collections.emptyList(), VisionPose.ZERO, 2);
        require(map.snapshot().size() <= 2, "Lowered capacity takes effect on the next frame even outside view");
    }

    private static void invalid_input_matrix() throws Exception {
        double[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double value : invalid) {
            for (int axis = 0; axis < 3; axis++) {
                VisionPose pose = new VisionPose(axis == 0 ? value : 20,
                        axis == 1 ? value : 20, axis == 2 ? value : 0);
                PollenMap map = new PollenMap();
                int id = map.inject(new VisionPose(40, 20, 0));
                map.update(Collections.emptyList(), pose, 1);
                require(map.snapshot().size() == 1 && map.snapshot().get(0).id == id,
                        "Invalid capture coordinate preserves existing tracks");
                require(map.inject(pose) == -1, "Invalid injected coordinate is rejected");
            }
        }
        String[] configNames = {"matchDistanceIn", "nearIn", "farIn", "hfovDeg",
                "frustumMarginDeg", "frustumMarginIn", "minConfidence", "fieldWidthIn", "fieldMarginIn"};
        for (String name : configNames) for (double value : invalid) {
            PollenMap map = new PollenMap();
            map.inject(new VisionPose(40, 20, 0));
            Field field = PollenMap.Config.class.getField(name);
            field.setDouble(map.config, value);
            map.update(Collections.emptyList(), new VisionPose(20, 20, 0), 1);
            require(map.snapshot().size() == 1, "Invalid map " + name + " cannot supply negative evidence");
        }
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 20, 0));
        map.update(null, VisionPose.ZERO, 1);
        map.update(Collections.emptyList(), null, 1);
        map.update(Collections.emptyList(), VisionPose.ZERO, -1);
        require(map.snapshot().size() == 1, "Null frame, null pose and negative sequence are inert");
        for (double confidence : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 1.01}) {
            require(map.inject(new VisionPose(30, 30, 0), confidence, 1) == -1,
                    "Invalid injected confidence is rejected");
        }
    }

    private static void frustum_boundaries() {
        PollenMap map = new PollenMap();
        VisionPose origin = new VisionPose(72, 72, 0);
        require(!map.in_frustum(origin, new VisionPose(82.99, 72, 0)), "Inside near safety margin is invisible");
        require(map.in_frustum(origin, new VisionPose(83, 72, 0)), "Near safe boundary is visible");
        require(map.in_frustum(origin, new VisionPose(129, 72, 0)), "Far safe boundary is visible");
        require(!map.in_frustum(origin, new VisionPose(129.01, 72, 0)), "Beyond far safe boundary is invisible");
        double edge = Math.toRadians(LimelightGeometry.HORIZONTAL_FOV_DEG / 2 - 4);
        require(map.in_frustum(origin, new VisionPose(72 + 30 * Math.cos(edge - 1e-8),
                72 + 30 * Math.sin(edge - 1e-8), 0)), "Inside angular margin is visible");
        require(!map.in_frustum(origin, new VisionPose(72 + 30 * Math.cos(edge + 1e-8),
                72 + 30 * Math.sin(edge + 1e-8), 0)), "Outside angular margin is invisible");
    }

    private static void calibrated_frustum() {
        Limelight camera = new Limelight();
        camera.calibration.usePinholeRange = true;
        camera.calibration.cameraHeightIn = 10;
        camera.calibration.cameraPitchDeg = 0;
        PollenMap map = new PollenMap(camera);
        VisionPose robot = new VisionPose(72, 72, 0);
        int unseen = map.inject(new VisionPose(87, 72, 0));
        map.inject(new VisionPose(102, 72, 0));
        map.update(Collections.emptyList(), robot, 1);
        require(map.snapshot().size() == 1 && map.snapshot().get(0).id == unseen,
                "Level camera VFOV cannot clear15inch floor target but sees30inch floor target");
        camera.calibration.cameraPitchDeg = 30;
        map.update(Collections.emptyList(), robot, 2);
        require(map.snapshot().isEmpty(), "Downward pitch brings that near-floor target into view");
    }

    private static void close(double actual, double expected, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= 1e-8, message);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { failures++; if (failures <= 12) System.out.println("FAIL: " + message); }
    }
}
