package org.firstinspires.ftc.teamcode.limelight;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.LLStatus;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Direct Java mirror of the ma douce camera subsystem and its Java bridge. */
public final class Limelight {
    public static final int DETECTOR_PIPELINE = 0;
    public static final int APRILTAG_PIPELINE = 1;
    public static final int MAX_DETECTIONS = 128;
    public static final double MAX_LATENCY_MS = 500.0;
    private static final int POSE_HISTORY = 64;
    private static final long POSE_SAMPLE_INTERVAL_MS = 10;

    public enum Status { NOT_INITIALISED, OK, INIT_FAILED }

    public static final class Result {
        public final boolean valid;
        public final boolean botposeValid;
        public final double tx;
        public final double ty;
        public final double ta;
        /** Raw Limelight field-centre coordinates, meters and degrees. */
        public final double x;
        public final double y;
        public final double yaw;
        public final double captureLatency;
        public final double targetingLatency;
        public final double parseLatency;
        public final double temp;
        public final double cpu;
        public final double fps;
        public final int pipelineIndex;
        public final double frameTimestamp;
        public final long stalenessMs;
        private final VisionPose pose;

        private Result(boolean valid, double tx, double ty, double ta,
                       VisionPose pose, double x, double y, double yaw,
                       double captureLatency, double targetingLatency, double parseLatency,
                       double temp, double cpu, double fps, int pipelineIndex,
                       double frameTimestamp, long stalenessMs) {
            this.valid = valid;
            this.tx = tx;
            this.ty = ty;
            this.ta = ta;
            this.pose = pose;
            this.botposeValid = pose != null;
            this.x = x;
            this.y = y;
            this.yaw = yaw;
            this.captureLatency = captureLatency;
            this.targetingLatency = targetingLatency;
            this.parseLatency = parseLatency;
            this.temp = temp;
            this.cpu = cpu;
            this.fps = fps;
            this.pipelineIndex = pipelineIndex;
            this.frameTimestamp = frameTimestamp;
            this.stalenessMs = stalenessMs;
        }
    }

    public final LimelightCalibration calibration = new LimelightCalibration();
    /** A disconnected camera's last cached image cannot clear the map. */
    public long maxFrameAgeMs = 250;
    /** JSON detector confidence is a fraction. Set to 0.01 only for verified percentage firmware. */
    public double confidenceScale = 1.0;
    private HardwareMap hardwareMap;
    private final String hardwareName;
    private Limelight3A device;
    private Status status = Status.NOT_INITIALISED;
    private String error = "";
    private int selectedPipeline = DETECTOR_PIPELINE;
    private Result lastResult = empty_result();
    private long lastResultReadMs;
    private long lastResultInitialAgeMs;
    private long publishedFrameMs;
    private long publishedFrameAgeMs;
    private LLResult lastReadResult;
    private double temperature;
    private double cpu;
    private double fps;
    private List<Pollen> currentFrame = Collections.emptyList();
    private long frameSequence;
    private double lastFrameTimestamp;
    private long lastHubTimestamp = Long.MIN_VALUE;
    private LLResult lastAnalysedResult;
    private VisionPose currentPose = VisionPose.ZERO;
    private VisionPose capturePose;
    private final long[] poseStamps = new long[POSE_HISTORY];
    private final VisionPose[] poseHistory = new VisionPose[POSE_HISTORY];
    private int poseCount;
    private int poseNext;
    private long poseAnchorMs;

    public Limelight() { this(null, "limelight"); }
    public Limelight(HardwareMap hardwareMap) { this(hardwareMap, "limelight"); }

    public Limelight(HardwareMap hardwareMap, String hardwareName) {
        if (hardwareName == null || hardwareName.isEmpty()) {
            throw new IllegalArgumentException("Limelight hardware name is required");
        }
        this.hardwareMap = hardwareMap;
        this.hardwareName = hardwareName;
    }

    /** Competition-friendly init: failure stays visible in status and the robot log. */
    public boolean init() { return init(hardwareMap); }

    public boolean init(HardwareMap hardwareMap) {
        try {
            hard_init(hardwareMap);
            return true;
        } catch (RuntimeException exception) {
            stop();
            status = Status.INIT_FAILED;
            error = exception.toString();
            RobotLog.ee("Limelight", exception, "Could not initialize camera %s", hardwareName);
            return false;
        }
    }

    /** Test-friendly init that preserves the hardware failure as an exception. */
    public void hard_init(HardwareMap hardwareMap) {
        stop();
        if (hardwareMap == null) throw new IllegalArgumentException("Hardware map is required");
        this.hardwareMap = hardwareMap;
        try {
            device = hardwareMap.get(Limelight3A.class, hardwareName);
            if (!device.pipelineSwitch(DETECTOR_PIPELINE)) {
                throw new IllegalStateException("Could not select detector pipeline");
            }
            selectedPipeline = DETECTOR_PIPELINE;
            device.setPollRateHz(100);
            device.start();
            status = Status.OK;
            error = "";
        } catch (RuntimeException exception) {
            stop();
            status = Status.INIT_FAILED;
            error = exception.toString();
            throw exception;
        }
    }

    /** Call every robot loop to feed the capture-time pose history. */
    public void update(VisionPose pose) {
        long now = VisionPose.now_ms();
        if (pose != null && pose.is_finite()) {
            currentPose = pose;
            push_pose(now, pose);
        }
        if (device == null) {
            lastResult = empty_result();
            return;
        }
        try {
            LLResult result = device.getLatestResult();
            if (result == null) {
                lastResult = empty_result();
                return;
            }
            long age = Math.max(0, result.getStaleness());
            int actualPipeline = result.getPipelineIndex();
            double resultTimestamp = result.getTimestamp();
            boolean newResult = lastReadResult == null || actualPipeline != lastResult.pipelineIndex
                    || resultTimestamp != lastResult.frameTimestamp;
            if (!(Double.isFinite(resultTimestamp) && resultTimestamp > 0)) {
                newResult = result != lastReadResult;
            }
            if (newResult) {
                lastResultReadMs = now;
                lastResultInitialAgeMs = age;
            }
            lastReadResult = result;
            boolean fresh = age <= Math.max(0, maxFrameAgeMs)
                    && lastResultInitialAgeMs <= Math.max(0, maxFrameAgeMs)
                    && now - lastResultReadMs <= Math.max(0, maxFrameAgeMs) - lastResultInitialAgeMs;
            boolean correctPipeline = actualPipeline == selectedPipeline;
            boolean valid = fresh && correctPipeline && result.isValid()
                    && Double.isFinite(result.getTx()) && Double.isFinite(result.getTy())
                    && Double.isFinite(result.getTa()) && result.getTa() >= 0;
            VisionPose tagPose = null;
            double rawX = 0, rawY = 0, rawYaw = 0;
            List<LLResultTypes.FiducialResult> tags = result.getFiducialResults();
            boolean tagsPresent = result.getBotposeTagCount() > 0 || (tags != null && !tags.isEmpty());
            if (valid && selectedPipeline == APRILTAG_PIPELINE && tagsPresent) {
                Pose3D botpose = result.getBotpose();
                if (botpose != null) {
                    Position position = botpose.getPosition().toUnit(DistanceUnit.METER);
                    rawX = position.x;
                    rawY = position.y;
                    rawYaw = botpose.getOrientation().getYaw(AngleUnit.DEGREES);
                    VisionPose converted = new VisionPose(DistanceUnit.INCH.fromMeters(rawX)
                            + LimelightGeometry.FIELD_CENTRE_IN,
                            DistanceUnit.INCH.fromMeters(rawY) + LimelightGeometry.FIELD_CENTRE_IN,
                            Math.toRadians(rawYaw));
                    if (converted.is_finite()) tagPose = converted;
                }
            }
            double captureLatency = result.getCaptureLatency();
            double targetingLatency = result.getTargetingLatency();
            double parseLatency = result.getParseLatency();
            lastResult = new Result(valid, valid ? result.getTx() : 0,
                    valid ? result.getTy() : 0, valid ? result.getTa() : 0,
                    tagPose, rawX, rawY, rawYaw, captureLatency, targetingLatency,
                    parseLatency, temperature, cpu, fps, actualPipeline, result.getTimestamp(), age);
            if (!fresh || !correctPipeline || selectedPipeline != DETECTOR_PIPELINE) return;
            // Invalid localization must not sweep a wedge at an invented robot pose.
            if (pose == null || !pose.is_finite()) return;
            double timestamp = result.getTimestamp();
            if (Double.isFinite(timestamp) && timestamp > 0 && lastFrameTimestamp > 0) {
                if (timestamp == lastFrameTimestamp) return;
                if (timestamp < lastFrameTimestamp
                        && (result == lastAnalysedResult
                        || result.getControlHubTimeStamp() <= lastHubTimestamp)) return;
                // A fresh Control Hub receipt with lower camera uptime starts a new camera epoch.
            }
            if ((!Double.isFinite(timestamp) || timestamp <= 0)
                    && (result == lastAnalysedResult
                    || result.getControlHubTimeStamp() == lastHubTimestamp)) return;
            double latency = captureLatency + targetingLatency + parseLatency + age;
            // A corrupt latency supplies no usable capture pose or negative evidence.
            if (!Double.isFinite(latency) || captureLatency < 0 || targetingLatency < 0
                    || parseLatency < 0 || latency > MAX_LATENCY_MS
                    || !Double.isFinite(confidenceScale) || confidenceScale <= 0
                    || !valid_calibration()) return;
            VisionPose framePose = pose_at(Math.max(0, now - (long) latency));
            ArrayList<Pollen> detections = new ArrayList<>();
            List<LLResultTypes.DetectorResult> detectorResults = result.getDetectorResults();
            int count = Math.min(detectorResults == null ? 0 : detectorResults.size(), MAX_DETECTIONS);
            for (int i = 0; i < count; i++) {
                LLResultTypes.DetectorResult detection = detectorResults.get(i);
                if (detection == null || !"pollen".equalsIgnoreCase(detection.getClassName())) continue;
                // The JSON specification uses 0..1; SDK 12's getter passes that value through.
                double confidence = detection.getConfidence() * confidenceScale;
                double area = detection.getTargetArea();
                if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1
                        || !Double.isFinite(area) || area < 0) continue;
                VisionPose position = calibration.project(detection.getTargetXDegrees(),
                        detection.getTargetYDegrees(), framePose);
                if (position != null) detections.add(new Pollen(-1, position, confidence, area, now));
            }
            currentFrame = Collections.unmodifiableList(detections);
            capturePose = framePose;
            publishedFrameMs = now;
            publishedFrameAgeMs = age;
            lastFrameTimestamp = Double.isFinite(timestamp) && timestamp > 0 ? timestamp : 0;
            lastHubTimestamp = result.getControlHubTimeStamp();
            lastAnalysedResult = result;
            frameSequence++;
        } catch (RuntimeException exception) {
            lastResult = empty_result();
            if (!exception.toString().equals(error)) {
                RobotLog.ee("Limelight", exception, "Camera result read failed");
            }
            error = exception.toString();
        }
    }

    /** Status requests are slower than cached frames; call this at a lower rate. */
    public void update_full(VisionPose pose) {
        if (device != null) {
            try {
                LLStatus cameraStatus = device.getStatus();
                if (cameraStatus != null) {
                    temperature = cameraStatus.getTemp();
                    cpu = cameraStatus.getCpu();
                    fps = cameraStatus.getFps();
                }
            } catch (RuntimeException exception) {
                error = exception.toString();
            }
        }
        update(pose);
    }

    public void stop() {
        if (device != null) {
            try { device.stop(); }
            catch (RuntimeException exception) { error = exception.toString(); }
        }
        device = null;
        status = Status.NOT_INITIALISED;
        reset_frame();
        temperature = cpu = fps = 0;
        lastResult = empty_result();
    }

    /** The camera switches asynchronously; only matching result indices are consumed. */
    public boolean set_pipeline(int index) {
        if (index < 0 || index > 9 || device == null) return false;
        if (index == selectedPipeline) return true;
        try {
            if (!device.pipelineSwitch(index)) return false;
            selectedPipeline = index;
            reset_frame();
            lastResult = empty_result();
            return true;
        } catch (RuntimeException exception) {
            error = exception.toString();
            return false;
        }
    }

    public boolean capture_snapshot(String name) {
        if (device == null || name == null || name.isEmpty()) return false;
        try { return device.captureSnapshot(name); }
        catch (RuntimeException exception) { error = exception.toString(); return false; }
    }

    public Status status() { return status; }
    public Status get_status() { return status(); }
    public String get_error() { return error; }
    public boolean is_connected() { return device != null && status == Status.OK && device.isConnected(); }
    public int pipeline() { return selectedPipeline; }
    public boolean has_target() { return result_is_fresh() && lastResult.valid; }
    public double get_tx() { return has_target() ? lastResult.tx : 0; }
    public double get_ty() { return has_target() ? lastResult.ty : 0; }
    public double get_ta() { return has_target() ? lastResult.ta : 0; }
    public Result get_result() { return lastResult; }
    public List<Pollen> get_pollen() { return currentFrame; }
    public void clear_pollen() { currentFrame = Collections.emptyList(); capturePose = null; }
    public long pollen_frame_sequence() { return frameSequence; }
    public boolean has_pollen_frame() {
        return capturePose != null && selectedPipeline == DETECTOR_PIPELINE
                && lastResult.pipelineIndex == DETECTOR_PIPELINE
                && result_is_fresh()
                && publishedFrameAgeMs <= Math.max(0, maxFrameAgeMs)
                && VisionPose.now_ms() - publishedFrameMs
                <= Math.max(0, maxFrameAgeMs) - publishedFrameAgeMs;
    }
    public VisionPose pollen_frame_pose() { return capturePose != null ? capturePose : currentPose; }
    public VisionPose get_pose() { return result_is_fresh() ? lastResult.pose : null; }
    public VisionPose getPose() { return get_pose(); }
    public LimelightCalibration calibration() { return calibration; }
    public double forward_distance(double tyDeg) { return calibration.forward_distance(tyDeg); }

    public void telemetry(Telemetry telemetry) {
        if (telemetry == null) return;
        telemetry.addData("Limelight status", "%s, connected=%s", status, is_connected());
        telemetry.addData("Limelight pipeline", "requested=%d, received=%d", selectedPipeline,
                lastResult.pipelineIndex);
        telemetry.addData("Limelight target", "valid=%s tx=%.2f ty=%.2f area=%.2f",
                lastResult.valid, lastResult.tx, lastResult.ty, lastResult.ta);
        telemetry.addData("Limelight latency ms", "capture=%.1f target=%.1f parse=%.1f age=%d",
                lastResult.captureLatency, lastResult.targetingLatency,
                lastResult.parseLatency, lastResult.stalenessMs);
        telemetry.addData("Limelight health", "temp=%.1f CPU=%.1f FPS=%.1f", temperature, cpu, fps);
        telemetry.addData("Limelight pollen", "%d, frame=%d", currentFrame.size(), frameSequence);
        if (!error.isEmpty()) telemetry.addData("Limelight error", error);
    }

    private void reset_frame() {
        currentFrame = Collections.emptyList();
        lastFrameTimestamp = 0;
        lastHubTimestamp = Long.MIN_VALUE;
        lastAnalysedResult = null;
        lastReadResult = null;
        capturePose = null;
        poseCount = poseNext = 0;
        // Sequence is monotonic across init, stop and pipeline switches.
    }

    private Result empty_result() {
        return new Result(false, 0, 0, 0, null, 0, 0, 0,
                0, 0, 0, temperature, cpu, fps, -1, 0, 0);
    }

    private void push_pose(long stamp, VisionPose pose) {
        if (poseCount > 0 && stamp >= poseAnchorMs
                && stamp - poseAnchorMs < POSE_SAMPLE_INTERVAL_MS) {
            int latest = (poseNext + POSE_HISTORY - 1) % POSE_HISTORY;
            poseStamps[latest] = stamp;
            poseHistory[latest] = pose;
            return;
        }
        poseAnchorMs = stamp;
        poseStamps[poseNext] = stamp;
        poseHistory[poseNext] = pose;
        poseNext = (poseNext + 1) % POSE_HISTORY;
        poseCount = Math.min(POSE_HISTORY, poseCount + 1);
    }

    private boolean valid_calibration() {
        return !calibration.usePinholeRange || (Double.isFinite(calibration.cameraHeightIn)
                && calibration.cameraHeightIn > 0 && Double.isFinite(calibration.cameraPitchDeg)
                && calibration.cameraPitchDeg >= 0 && calibration.cameraPitchDeg < 90);
    }

    private boolean result_is_fresh() {
        return lastResult.pipelineIndex >= 0 && lastResult.stalenessMs <= Math.max(0, maxFrameAgeMs)
                && lastResultInitialAgeMs <= Math.max(0, maxFrameAgeMs)
                && VisionPose.now_ms() - lastResultReadMs
                <= Math.max(0, maxFrameAgeMs) - lastResultInitialAgeMs;
    }

    private VisionPose pose_at(long stamp) {
        if (poseCount == 0) return currentPose;
        int newer = -1;
        int older = -1;
        for (int i = 0; i < poseCount; i++) {
            int index = (poseNext + POSE_HISTORY - 1 - i) % POSE_HISTORY;
            if (poseStamps[index] > stamp) newer = index;
            else { older = index; break; }
        }
        if (older < 0) return newer >= 0 ? poseHistory[newer] : currentPose;
        if (newer < 0 || poseStamps[newer] == poseStamps[older]) return poseHistory[older];
        double t = (double) (stamp - poseStamps[older]) / (poseStamps[newer] - poseStamps[older]);
        VisionPose from = poseHistory[older];
        VisionPose to = poseHistory[newer];
        return new VisionPose(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t,
                from.heading + VisionPose.wrap_pi(to.heading - from.heading) * t);
    }
}
