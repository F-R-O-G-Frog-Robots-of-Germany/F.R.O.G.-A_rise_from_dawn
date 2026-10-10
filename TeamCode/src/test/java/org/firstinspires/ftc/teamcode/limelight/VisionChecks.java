package org.firstinspires.ftc.teamcode.limelight;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Deterministic regression checks for real map, calibration and latency interpolation. */
public final class VisionChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        authoritative_frames();
        stable_ids_and_boundaries();
        invalid_data();
        bounded_capacity();
        calibration();
        capture_pose_history();
        System.out.println("Vision checks passed: " + checks);
    }

    private static void authoritative_frames() {
        PollenMap map = new PollenMap();
        VisionPose robot = new VisionPose(20, 20, 0);
        int visible = map.inject(new VisionPose(40, 20, 0));
        int outside = map.inject(new VisionPose(20, 60, 0));
        map.update(Collections.emptyList(), robot, 0);
        require(map.snapshot().size() == 2, "No analyzed frame must preserve injected tracks");
        map.update(Collections.emptyList(), robot, 1);
        require(!contains(map.snapshot(), visible), "Analyzed empty frame clears visible pollen");
        require(contains(map.snapshot(), outside), "Empty frame preserves pollen outside its wedge");
        int later = map.inject(new VisionPose(40, 20, 0));
        map.update(Collections.emptyList(), robot, 1);
        require(contains(map.snapshot(), later), "Repeated empty frame provides no negative evidence");
        require(map.stats().framesProcessed == 1 && map.stats().framesSkipped == 1,
                "Only new detector sequences count as processed frames");
        map.update(Collections.emptyList(), new VisionPose(20, 20, Math.PI / 2), 2);
        require(!contains(map.snapshot(), outside) && contains(map.snapshot(), later),
                "A changed capture heading clears the newly observed wedge only");
        PollenMap bound = new PollenMap(new Limelight());
        bound.inject(new VisionPose(40, 20, 0));
        bound.update();
        require(bound.snapshot().size() == 1, "An uninitialized camera cannot clear the map");
    }

    private static void stable_ids_and_boundaries() {
        PollenMap map = new PollenMap();
        VisionPose robot = new VisionPose(20, 20, 0);
        int id = map.inject(new VisionPose(40, 20, 0));
        map.update(Collections.singletonList(observation(41, 20)), robot, 1);
        require(map.snapshot().size() == 1 && map.snapshot().get(0).id == id,
                "A refreshed observation retains the nearest cleared id");
        require(map.tracks().get(0).sightings == 2, "Sightings survive id reuse");
        map.update(Arrays.asList(observation(45, 20), observation(50, 20)), robot, 2);
        List<Pollen> snapshot = map.snapshot();
        require(snapshot.size() == 2 && snapshot.get(0).id != snapshot.get(1).id,
                "Separate detections cannot receive the same cleared id");
        require(map.remove(snapshot.get(0).id) && !map.remove(snapshot.get(0).id),
                "Collection removes exactly its stable id");
        require(map.mark_collected(50, 20, 2) == 1, "Collection radius removes pollen below camera range");

        PollenMap edge = new PollenMap();
        int edgeId = edge.inject(new VisionPose(29, 20, 0));
        edge.update(Collections.singletonList(observation(30, 20)), robot, 1);
        require(edge.snapshot().size() == 1 && edge.snapshot().get(0).id == edgeId,
                "Observation near the shrunken frustum boundary supersedes an old track");
    }

    private static void invalid_data() {
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 20, 0));
        map.update(Collections.emptyList(), new VisionPose(Double.NaN, 20, 0), 1);
        require(map.snapshot().size() == 1, "Invalid capture pose must never sweep the map");
        map.config.hfovDeg = Double.NaN;
        map.update(Collections.emptyList(), new VisionPose(20, 20, 0), 1);
        require(map.snapshot().size() == 1, "Invalid frustum config must never sweep the map");
        map.config.hfovDeg = LimelightGeometry.HORIZONTAL_FOV_DEG;
        map.update(Arrays.asList(new Pollen(-1, new VisionPose(50, 20, 0), Double.NaN, 1),
                new Pollen(-1, new VisionPose(60, 20, 0), 0.1, 1),
                new Pollen(-1, new VisionPose(1000, 20, 0), 1, 1)),
                new VisionPose(20, 20, 0), 1);
        require(map.snapshot().isEmpty() && map.stats().rejected == 3,
                "Invalid, low-confidence and off-field observations cannot create tracks");
        require(map.inject(new VisionPose(30, Double.POSITIVE_INFINITY, 0)) == -1,
                "Synthetic injection validates coordinates too");
    }

    private static void bounded_capacity() {
        PollenMap map = new PollenMap();
        map.config.maxTracks = 2;
        int first = map.inject(new VisionPose(10, 10, 0));
        map.inject(new VisionPose(20, 10, 0));
        map.inject(new VisionPose(30, 10, 0));
        require(map.snapshot().size() == 2 && !contains(map.snapshot(), first),
                "Capacity evicts the oldest track");
        map.config.maxTracks = 10000;
        for (int i = 0; i < 200; i++) map.inject(new VisionPose(i % 140, 30, 0));
        require(map.snapshot().size() == PollenMap.HARD_MAX_TRACKS, "Hard track cap bounds work");
        map.init();
        require(map.snapshot().isEmpty() && map.stats().created == 0,
                "Initialization resets tracks and counters");
    }

    private static void calibration() {
        LimelightCalibration calibration = new LimelightCalibration();
        close(calibration.forward_distance(-LimelightGeometry.VERTICAL_FOV_DEG / 2), 8,
                "Placeholder bottom-frame range");
        close(calibration.forward_distance(LimelightGeometry.VERTICAL_FOV_DEG / 2), 60,
                "Placeholder top-frame range");
        require(Double.isNaN(calibration.forward_distance(Double.NaN)), "NaN ray is rejected");
        calibration.usePinholeRange = true;
        calibration.cameraHeightIn = 10;
        calibration.cameraPitchDeg = 30;
        close(calibration.forward_distance(0), 10 / Math.tan(Math.toRadians(30)),
                "Measured height and downward pitch provide flat-floor distance");
        require(Double.isNaN(calibration.forward_distance(30)), "Horizon ray has no forward floor point");
        VisionPose position = calibration.project(10, 0, new VisionPose(50, 50, 0));
        require(position != null && position.x > 50 && position.y < 50,
                "Positive camera tx projects to robot right");
        VisionPose turned = calibration.project(0, 0, new VisionPose(50, 50, Math.PI / 2));
        require(turned != null && Math.abs(turned.x - 50) < 1e-9 && turned.y > 50,
                "Projection follows the capture heading");
        calibration.cameraHeightIn = -1;
        require(calibration.project(0, 0, VisionPose.ZERO) == null, "Invalid mounting produces no pollen");
    }

    private static void capture_pose_history() throws Exception {
        Limelight camera = new Limelight();
        Method push = Limelight.class.getDeclaredMethod("push_pose", long.class, VisionPose.class);
        Method at = Limelight.class.getDeclaredMethod("pose_at", long.class);
        push.setAccessible(true);
        at.setAccessible(true);
        push.invoke(camera, 100L, new VisionPose(0, 0, Math.toRadians(179)));
        push.invoke(camera, 200L, new VisionPose(10, 20, Math.toRadians(-179)));
        VisionPose midpoint = (VisionPose) at.invoke(camera, 150L);
        close(midpoint.x, 5, "Capture-time x interpolation");
        close(midpoint.y, 10, "Capture-time y interpolation");
        close(Math.abs(VisionPose.wrap_pi(midpoint.heading)), Math.PI,
                "Heading interpolation takes the short arc across the wrap boundary");
        require(((VisionPose) at.invoke(camera, 0L)).x == 0,
                "Frame older than history uses the oldest available pose");
        camera.stop();
        require(camera.status() == Limelight.Status.NOT_INITIALISED && camera.get_pose() == null
                && !camera.has_pollen_frame(), "Stopping invalidates camera observations");
    }

    private static Pollen observation(double x, double y) {
        return new Pollen(-1, new VisionPose(x, y, 0), 1, 1, 1);
    }

    private static boolean contains(List<Pollen> frame, int id) {
        for (Pollen pollen : frame) if (pollen.id == id) return true;
        return false;
    }

    private static void close(double actual, double expected, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-9, message);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
