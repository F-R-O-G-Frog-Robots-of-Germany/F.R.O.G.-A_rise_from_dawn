import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.teamcode.limelight.HiveLimelightObservation;
import org.firstinspires.ftc.teamcode.limelight.HivePoseEstimator;
import java.util.List;

/** Injects one SDK-shaped exposure through the production adapter and frame estimator. */
public final class HiveFrameAudit {
    private static int checks;

    public static void main(String[] args) {
        double[] identity = {1,0,0, 0,1,0, 0,0,1};
        HivePoseEstimator.TagModel firstModel = model(30, new double[]{2,3,4});
        HivePoseEstimator.TagModel secondModel = model(31, new double[]{-4,3,4});
        HivePoseEstimator estimator = new HivePoseEstimator(
                new HivePoseEstimator.TagModel[]{firstModel, secondModel,
                        model(32, new double[]{2,3,4})},
                new HivePoseEstimator.CameraMount(identity, new double[]{1,2,10}, false));

        // With optical identity, tag Z maps to camera forward. Camera is at (11,7,10),
        // hinge at (60,0,40). Subtract each tag-to-hinge vector in camera coordinates.
        LLResult source = new LLResult();
        LLResultTypes.FiducialResult first = fiducial(30, 5, -33, 45);
        source.fiducials.add(first);
        source.fiducials.add(fiducial(31, 11, -33, 45));
        source.fiducials.add(fiducial(32, 5, -33, 75)); // Incorrect range; outlier.
        source.fiducials.add(null);
        source.fiducials.add(new LLResultTypes.FiducialResult()); // No full pose.
        source.fiducials.add(fiducial(99, 5, -33, 45)); // Unknown geometry.
        List<HivePoseEstimator.Observation> observations =
                HiveLimelightObservation.from_result(source, 1234, true);
        require(observations.size() == 4, "adapter skips malformed poses but retains unknown IDs");
        for (HivePoseEstimator.Observation observation : observations) {
            require(observation.captureTimeMs == 1234 && observation.timingAssumed,
                    "frame exposure metadata copied to every observation");
        }
        // Later camera transport changes cannot alter the copied estimator inputs.
        first.id = 45;
        first.targetPoseCameraSpace = null;
        source.fiducials.clear();
        HivePoseEstimator.FrameResult result = estimator.estimate_frame(
                observations, new double[]{0,0,-1}, false);
        require(result != null, "adapter-to-frame consensus exists");
        require(result.tagCount == 2 && result.usableTagCount == 3 && result.observationCount == 4,
                "unknown geometry and outlier counts preserved");
        int[] ids = result.tag_ids();
        require(ids.length == 2 && ids[0] == 30 && ids[1] == 31, "correct inlier IDs");
        close(result.x, 10, "robot X");
        close(result.y, 5, "robot Y");
        close(result.z, 0, "robot height");
        close(result.heading, 0, "robot heading");
        close(result.positionSpreadIn, 0, "exact tags agree");
        require(result.captureTimeMs == 1234 && result.usesAssumedValues,
                "estimated timing provenance reaches fused result");

        source.fiducials.add(fiducial(30, 5, -33, 45));
        source.fiducials.add(fiducial(30, 5, -33, 45));
        require(estimator.estimate_frame(HiveLimelightObservation.from_result(source, 1235),
                new double[]{0,0,-1}, false) == null, "duplicate camera detections cannot vote twice");
        source.valid = false;
        require(HiveLimelightObservation.from_result(source, 1236).isEmpty(),
                "invalid camera frame supplies no observations");
        System.out.println("Hive frame adapter audit checks passed: " + checks);
    }

    private static HivePoseEstimator.TagModel model(int id, double[] pivotTag) {
        return new HivePoseEstimator.TagModel(id, new double[]{60,0,40}, new double[]{1,0,0},
                pivotTag, new double[]{0,0,1}, false, "synthetic adapter integration");
    }

    private static LLResultTypes.FiducialResult fiducial(int id, double x, double y, double z) {
        LLResultTypes.FiducialResult result = new LLResultTypes.FiducialResult();
        result.id = id;
        result.targetPoseCameraSpace = new Pose3D(
                new Position(DistanceUnit.METER, x * 0.0254, y * 0.0254, z * 0.0254, 0),
                new YawPitchRollAngles(AngleUnit.DEGREES, 0, 0, 0, 0));
        return result;
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-9, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
