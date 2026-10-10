package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A new detector frame replaces its visible wedge and preserves everything outside it. */
public final class PollenMap {
    public static final int HARD_MAX_TRACKS = 64;
    public static final int HARD_MAX_DETECTIONS = 32;

    public static final class Config {
        public double matchDistanceIn = LimelightGeometry.TRACK_MATCH_DISTANCE_IN;
        public double nearIn = LimelightGeometry.NEAREST_FORWARD_IN;
        public double farIn = LimelightGeometry.FAR_FORWARD_IN;
        public double hfovDeg = LimelightGeometry.HORIZONTAL_FOV_DEG;
        public double frustumMarginDeg = 4.0;
        public double frustumMarginIn = 3.0;
        public double minConfidence = 0.25;
        public double fieldWidthIn = LimelightGeometry.FIELD_WIDTH_IN;
        public double fieldMarginIn = 6.0;
        public int maxTracks = 32;
    }

    public static final class Track {
        public final int id;
        public final VisionPose position;
        public final double confidence;
        public final double area;
        public final long firstSeenMs;
        public final long lastSeenMs;
        public final int sightings;

        public Track(int id, VisionPose position, double confidence, double area,
                     long firstSeenMs, long lastSeenMs, int sightings) {
            this.id = id;
            this.position = position;
            this.confidence = confidence;
            this.area = area;
            this.firstSeenMs = firstSeenMs;
            this.lastSeenMs = lastSeenMs;
            this.sightings = sightings;
        }
    }

    public static final class Stats {
        public final long framesProcessed;
        public final long framesSkipped;
        public final int created;
        public final int replaced;
        public final int rejected;

        private Stats(long framesProcessed, long framesSkipped, int created,
                      int replaced, int rejected) {
            this.framesProcessed = framesProcessed;
            this.framesSkipped = framesSkipped;
            this.created = created;
            this.replaced = replaced;
            this.rejected = rejected;
        }
    }

    public final Config config;
    private final Limelight camera;
    private LimelightCalibration activeCalibration;
    private final ArrayList<Track> tracks = new ArrayList<>(HARD_MAX_TRACKS);
    private long lastFrameSequence;
    private boolean frameSequenceValid;
    private int nextId;
    private long framesProcessed;
    private long framesSkipped;
    private int created;
    private int replaced;
    private int rejected;

    public PollenMap() { this(null, new Config()); }

    public PollenMap(Limelight camera) { this(camera, new Config()); }

    public PollenMap(Config config) { this(null, config); }

    public PollenMap(Limelight camera, Config config) {
        if (config == null) throw new IllegalArgumentException("Map config is required");
        this.camera = camera;
        this.activeCalibration = camera == null ? null : camera.calibration;
        this.config = config;
    }

    public void init() {
        clear();
        lastFrameSequence = 0;
        frameSequenceValid = false;
        nextId = 0;
        framesProcessed = framesSkipped = 0;
        created = replaced = rejected = 0;
    }

    public void clear() { tracks.clear(); }

    public void update() { update(camera); }

    public void update(Limelight camera) {
        if (camera != null && camera.has_pollen_frame()) {
            activeCalibration = camera.calibration;
            update(camera.get_pollen(), camera.pollen_frame_pose(), camera.pollen_frame_sequence());
        }
    }

    /** A missing frame, invalid pose, or repeated sequence never supplies negative evidence. */
    public void update(List<Pollen> frame, VisionPose capturePose, long sequence) {
        if (sequence <= 0 || frame == null || capturePose == null || !capturePose.is_finite()
                || !valid_config()) return;
        if (frameSequenceValid && sequence <= lastFrameSequence) {
            framesSkipped++;
            return;
        }
        frameSequenceValid = true;
        lastFrameSequence = sequence;
        framesProcessed++;
        long stamp = VisionPose.now_ms();
        while (tracks.size() > capacity()) drop_stalest();
        ArrayList<Pollen> detections = new ArrayList<>(HARD_MAX_DETECTIONS);
        int inspected = 0;
        for (Pollen pollen : frame) {
            if (inspected++ >= HARD_MAX_DETECTIONS) break;
            if (pollen == null || !plausible(pollen.position)
                    || !Double.isFinite(pollen.confidence) || pollen.confidence < config.minConfidence
                    || pollen.confidence > 1 || !Double.isFinite(pollen.area) || pollen.area < 0) {
                rejected++;
                continue;
            }
            detections.add(pollen);
        }

        ArrayList<Track> cleared = new ArrayList<>(tracks.size());
        for (Iterator<Track> iterator = tracks.iterator(); iterator.hasNext();) {
            Track track = iterator.next();
            if (in_frustum(capturePose, track.position)) {
                cleared.add(track);
                iterator.remove();
            }
        }
        replaced += cleared.size();
        double gateSquared = config.matchDistanceIn * config.matchDistanceIn;
        for (Pollen detection : detections) {
            for (Iterator<Track> iterator = tracks.iterator(); iterator.hasNext();) {
                Track track = iterator.next();
                if (detection.position.distance_squared(track.position) <= gateSquared) {
                    cleared.add(track);
                    iterator.remove();
                }
            }
            int best = -1;
            double bestDistance = gateSquared;
            for (int i = 0; i < cleared.size(); i++) {
                Track track = cleared.get(i);
                if (track == null) continue;
                double distance = detection.position.distance_squared(track.position);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            int id;
            long firstSeen = stamp;
            int sightings = 0;
            if (best >= 0) {
                Track previous = cleared.set(best, null);
                id = previous.id;
                firstSeen = previous.firstSeenMs;
                sightings = previous.sightings;
            } else {
                id = nextId++;
                created++;
            }
            while (tracks.size() >= capacity()) drop_stalest();
            tracks.add(new Track(id, detection.position, detection.confidence, detection.area,
                    firstSeen, stamp, Math.min(0xffff, sightings + 1)));
        }
    }

    public boolean remove(int id) {
        for (Iterator<Track> iterator = tracks.iterator(); iterator.hasNext();) {
            if (iterator.next().id == id) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public int mark_collected(double x, double y, double radiusIn) {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(radiusIn) || radiusIn < 0) return 0;
        int count = 0;
        VisionPose centre = new VisionPose(x, y, 0);
        for (Iterator<Track> iterator = tracks.iterator(); iterator.hasNext();) {
            if (iterator.next().position.distance_squared(centre) <= radiusIn * radiusIn) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    public List<Track> tracks() {
        return Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    public Stats stats() {
        return new Stats(framesProcessed, framesSkipped, created, replaced, rejected);
    }

    public List<Pollen> snapshot() {
        ArrayList<Pollen> snapshot = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            snapshot.add(new Pollen(track.id, track.position, track.confidence, track.area,
                    track.lastSeenMs));
        }
        return snapshot;
    }

    public int inject(VisionPose position) { return inject(position, 1.0, 1.0); }

    public int inject(VisionPose position, double confidence, double area) {
        if (!valid_config() || !plausible(position) || !Double.isFinite(confidence)
                || confidence < 0 || confidence > 1 || !Double.isFinite(area) || area < 0) {
            rejected++;
            return -1;
        }
        while (tracks.size() >= capacity()) drop_stalest();
        int id = nextId++;
        long stamp = VisionPose.now_ms();
        tracks.add(new Track(id, position, confidence, area, stamp, stamp, 1));
        created++;
        return id;
    }

    public boolean in_frustum(VisionPose robot, VisionPose target) {
        if (robot == null || target == null || !robot.is_finite() || !target.is_finite()
                || !valid_config()) return false;
        double cos = Math.cos(robot.heading);
        double sin = Math.sin(robot.heading);
        double cameraX = robot.x + LimelightGeometry.CAMERA_FORWARD_OFFSET_IN * cos
                - LimelightGeometry.CAMERA_LEFT_OFFSET_IN * sin;
        double cameraY = robot.y + LimelightGeometry.CAMERA_FORWARD_OFFSET_IN * sin
                + LimelightGeometry.CAMERA_LEFT_OFFSET_IN * cos;
        double dx = target.x - cameraX;
        double dy = target.y - cameraY;
        double range = Math.hypot(dx, dy);
        if (range < config.nearIn + config.frustumMarginIn
                || range > config.farIn - config.frustumMarginIn) return false;
        double halfFov = Math.toRadians(config.hfovDeg / 2 - config.frustumMarginDeg);
        double bearing = VisionPose.wrap_pi(Math.atan2(dy, dx) - robot.heading
                - Math.toRadians(LimelightGeometry.CAMERA_YAW_DEG));
        if (activeCalibration != null && activeCalibration.usePinholeRange) {
            double height = activeCalibration.cameraHeightIn;
            double pitch = Math.toRadians(activeCalibration.cameraPitchDeg);
            if (!Double.isFinite(height) || height <= 0 || !Double.isFinite(pitch)) return false;
            double forward = range * Math.cos(bearing);
            double right = -range * Math.sin(bearing);
            double opticalForward = forward * Math.cos(pitch) + height * Math.sin(pitch);
            double opticalUp = forward * Math.sin(pitch) - height * Math.cos(pitch);
            if (!(opticalForward > 0)) return false;
            double tx = Math.atan2(right, opticalForward);
            double ty = Math.atan2(opticalUp, opticalForward);
            return Math.abs(tx) <= halfFov
                    && Math.abs(ty) <= Math.toRadians(LimelightGeometry.VERTICAL_FOV_DEG / 2);
        }
        return Math.abs(bearing) <= halfFov;
    }

    private boolean valid_config() {
        return Double.isFinite(config.matchDistanceIn) && config.matchDistanceIn >= 0
                && Double.isFinite(config.nearIn) && config.nearIn >= 0
                && Double.isFinite(config.farIn) && config.farIn > config.nearIn
                && Double.isFinite(config.hfovDeg) && config.hfovDeg > 0 && config.hfovDeg < 180
                && Double.isFinite(config.frustumMarginDeg) && config.frustumMarginDeg >= 0
                && config.hfovDeg / 2 > config.frustumMarginDeg
                && Double.isFinite(config.frustumMarginIn) && config.frustumMarginIn >= 0
                && config.farIn - config.frustumMarginIn > config.nearIn + config.frustumMarginIn
                && Double.isFinite(config.minConfidence) && config.minConfidence >= 0
                && config.minConfidence <= 1 && Double.isFinite(config.fieldWidthIn)
                && config.fieldWidthIn > 0 && Double.isFinite(config.fieldMarginIn)
                && config.fieldMarginIn >= 0;
    }

    private boolean plausible(VisionPose position) {
        if (position == null || !position.is_finite()) return false;
        double lo = -config.fieldMarginIn;
        double hi = config.fieldWidthIn + config.fieldMarginIn;
        return position.x >= lo && position.x <= hi && position.y >= lo && position.y <= hi;
    }

    private int capacity() { return Math.max(1, Math.min(HARD_MAX_TRACKS, config.maxTracks)); }

    private void drop_stalest() {
        int oldest = 0;
        for (int i = 1; i < tracks.size(); i++) {
            if (tracks.get(i).lastSeenMs < tracks.get(oldest).lastSeenMs) oldest = i;
        }
        tracks.remove(oldest);
    }
}
