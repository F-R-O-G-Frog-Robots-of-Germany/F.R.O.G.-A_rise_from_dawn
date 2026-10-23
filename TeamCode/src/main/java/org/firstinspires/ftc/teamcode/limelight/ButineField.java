package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Immutable physical boxes and bounded, deterministic robot-center routing in field inches. */
public final class ButineField {
    public static final int MAX_BOXES = 32;
    /** Extra space at route corners; this is not a measured follower tracking bound. */
    public static final double ROUTING_OFFSET_IN = 0.5;
    private static final double EPSILON = 1e-9;

    public static final class Box {
        public final double minX, maxX, minY, maxY;

        public Box(double minX, double maxX, double minY, double maxY) {
            if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                    || !Double.isFinite(minY) || !Double.isFinite(maxY)
                    || minX > maxX || minY > maxY) {
                throw new IllegalArgumentException("Finite ordered box bounds required");
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        private boolean contains(double x, double y) {
            return x >= minX - EPSILON && x <= maxX + EPSILON
                    && y >= minY - EPSILON && y <= maxY + EPSILON;
        }
    }

    /** Points exclude the supplied origin and include the supplied destination, even at zero distance. */
    public static final class Route {
        private final VisionPose[] points;
        public final double distanceIn;

        private Route(VisionPose[] points, double distanceIn) {
            this.points = points.clone();
            this.distanceIn = distanceIn;
        }

        public int point_count() { return points.length; }
        public VisionPose point(int index) { return points[index]; }
    }

    public final double fieldSizeIn, robotLengthIn, robotWidthIn, bodyClearanceIn;
    public final double fieldReserveIn, trackingReserveIn, radiusIn;
    private final boolean unrestricted;
    private final Box[] physicalBoxes;
    private final Box[] exclusionBoxes;
    private final VisionPose[] corners;
    private final double[][] cornerDistances;

    private ButineField() {
        unrestricted = true;
        fieldSizeIn = robotLengthIn = robotWidthIn = bodyClearanceIn = 0;
        fieldReserveIn = trackingReserveIn = radiusIn = 0;
        physicalBoxes = exclusionBoxes = new Box[0];
        corners = new VisionPose[0];
        cornerDistances = new double[0][0];
    }

    /** The reference point must be at the rectangle center; include extended mechanisms in its dimensions. */
    public ButineField(double fieldSizeIn, double robotLengthIn, double robotWidthIn,
                       double bodyClearanceIn, Box... physicalBoxes) {
        this(fieldSizeIn, robotLengthIn, robotWidthIn, bodyClearanceIn, 0, 0, physicalBoxes);
    }

    public ButineField(double fieldSizeIn, double robotLengthIn, double robotWidthIn,
                       double bodyClearanceIn, double fieldReserveIn, double trackingReserveIn,
                       Box... physicalBoxes) {
        if (!Double.isFinite(fieldSizeIn) || fieldSizeIn <= 0
                || !Double.isFinite(robotLengthIn) || robotLengthIn <= 0
                || !Double.isFinite(robotWidthIn) || robotWidthIn <= 0
                || !Double.isFinite(bodyClearanceIn) || bodyClearanceIn < 0
                || !Double.isFinite(fieldReserveIn) || fieldReserveIn < 0
                || !Double.isFinite(trackingReserveIn) || trackingReserveIn < 0) {
            throw new IllegalArgumentException("Finite positive field/robot dimensions and nonnegative clearance required");
        }
        Objects.requireNonNull(physicalBoxes, "physicalBoxes");
        if (physicalBoxes.length > MAX_BOXES) throw new IllegalArgumentException("Too many obstacle boxes");
        double radius = Math.hypot(robotLengthIn / 2, robotWidthIn / 2)
                + bodyClearanceIn + fieldReserveIn + trackingReserveIn;
        if (!Double.isFinite(radius) || radius >= fieldSizeIn / 2) {
            throw new IllegalArgumentException("Robot envelope leaves no usable field domain");
        }
        this.fieldSizeIn = fieldSizeIn;
        this.robotLengthIn = robotLengthIn;
        this.robotWidthIn = robotWidthIn;
        this.bodyClearanceIn = bodyClearanceIn;
        this.fieldReserveIn = fieldReserveIn;
        this.trackingReserveIn = trackingReserveIn;
        radiusIn = radius;
        unrestricted = false;
        this.physicalBoxes = physicalBoxes.clone();
        exclusionBoxes = new Box[physicalBoxes.length];
        for (int i = 0; i < physicalBoxes.length; i++) {
            Box box = Objects.requireNonNull(physicalBoxes[i], "physical box");
            exclusionBoxes[i] = new Box(box.minX - radius, box.maxX + radius,
                    box.minY - radius, box.maxY + radius);
        }
        ArrayList<VisionPose> vertices = new ArrayList<>(4 * physicalBoxes.length);
        for (Box box : exclusionBoxes) {
            add_corner(vertices, box.minX - ROUTING_OFFSET_IN, box.minY - ROUTING_OFFSET_IN);
            add_corner(vertices, box.minX - ROUTING_OFFSET_IN, box.maxY + ROUTING_OFFSET_IN);
            add_corner(vertices, box.maxX + ROUTING_OFFSET_IN, box.minY - ROUTING_OFFSET_IN);
            add_corner(vertices, box.maxX + ROUTING_OFFSET_IN, box.maxY + ROUTING_OFFSET_IN);
        }
        corners = vertices.toArray(new VisionPose[0]);
        cornerDistances = new double[corners.length][corners.length];
        for (int a = 0; a < corners.length; a++) {
            for (int b = 0; b < a; b++) {
                double distance = segment_clear(corners[a], corners[b])
                        ? corners[a].distance_to(corners[b]) : Double.POSITIVE_INFINITY;
                cornerDistances[a][b] = cornerDistances[b][a] = distance;
            }
        }
    }

    private static final class Defaults {
        static final ButineField UNRESTRICTED = new ButineField();
        // Official STEP SHA256 05b35961c7df847741031f00fda73ddd068537f809e92a11b1cd59a94bcc8331.
        // Support boxes project above-floor FRAME geometry through Z=19 inches for an 18-inch
        // maximum-height robot plus one inch overhead allowance. The underpass remains open.
        // Allowed +/-30-degree HIVE motion has conservative underside Z>=22.401 inches.
        // See docs/Butine-Center-Underpass-Geometry-2026-10-18.json for CAD derivation and sources.
        static final ButineField BIOBUZZ = new ButineField(144, 18, 18, 1,
                new Box(47.2, 53.9, 52.5, 91.5), new Box(90.1, 96.8, 52.5, 91.5),
                new Box(45.2, 52.0, 136.4, 143.8), new Box(136.4, 143.8, 92.0, 98.8),
                new Box(92.0, 98.8, 0.2, 7.6), new Box(0.2, 7.6, 45.2, 52.0));
    }

    /** Explicit free-plane model for mathematical calibration and legacy desktop fixtures. */
    public static ButineField unrestricted() { return Defaults.UNRESTRICTED; }

    /** Nominal BIOBUZZ footprint for an 18 by 18-inch robot no taller than 18 inches. */
    public static ButineField biobuzz() { return Defaults.BIOBUZZ; }

    /** Taller envelopes retain a conservative whole-center obstacle until a new height slice is derived. */
    public static ButineField biobuzz(double robotLengthIn, double robotWidthIn, double robotHeightIn,
                                      double bodyClearanceIn, double fieldReserveIn, double trackingReserveIn) {
        if (!Double.isFinite(robotHeightIn) || robotHeightIn <= 0)
            throw new IllegalArgumentException("Finite positive robot height required");
        Box[] flowers = {
                new Box(45.2, 52.0, 136.4, 143.8), new Box(136.4, 143.8, 92.0, 98.8),
                new Box(92.0, 98.8, 0.2, 7.6), new Box(0.2, 7.6, 45.2, 52.0)};
        boolean underpass = robotHeightIn + bodyClearanceIn + fieldReserveIn <= 19;
        Box[] boxes = new Box[flowers.length + (underpass ? 2 : 1)];
        System.arraycopy(flowers, 0, boxes, 0, flowers.length);
        if (underpass) {
            boxes[4] = new Box(47.2, 53.9, 52.5, 91.5);
            boxes[5] = new Box(90.1, 96.8, 52.5, 91.5);
        } else boxes[4] = new Box(47.2, 96.8, 38.5, 105.5);
        return new ButineField(144, robotLengthIn, robotWidthIn, bodyClearanceIn,
                fieldReserveIn, trackingReserveIn, boxes);
    }

    public int box_count() { return physicalBoxes.length; }
    public Box physical_box(int index) { return physicalBoxes[index]; }
    public Box exclusion_box(int index) { return exclusionBoxes[index]; }
    public boolean is_unrestricted() { return unrestricted; }

    public boolean is_clear(VisionPose pose) {
        if (pose == null || !pose.is_finite()) return false;
        if (unrestricted) return true;
        double far = fieldSizeIn - radiusIn;
        if (pose.x < radiusIn || pose.x > far || pose.y < radiusIn || pose.y > far) return false;
        for (Box box : exclusionBoxes) if (box.contains(pose.x, pose.y)) return false;
        return true;
    }

    /** Closed obstacle boundaries are forbidden. Endpoint field checks suffice for the convex domain. */
    public boolean segment_clear(VisionPose from, VisionPose to) {
        if (!is_clear(from) || !is_clear(to)) return false;
        double dx = to.x - from.x, dy = to.y - from.y;
        if (!Double.isFinite(Math.hypot(dx, dy))) return false;
        for (Box box : exclusionBoxes) if (intersects(from, dx, dy, box)) return false;
        return true;
    }

    /** Returns null when endpoints are invalid or the bounded conservative graph has no path. */
    public Route route(VisionPose from, VisionPose to) {
        if (!is_clear(from) || !is_clear(to)) return null;
        if (segment_clear(from, to)) return new Route(new VisionPose[]{to}, from.distance_to(to));
        int size = corners.length + 2;
        double[] fromDistances = new double[corners.length];
        double[] toDistances = new double[corners.length];
        for (int c = 0; c < corners.length; c++) {
            fromDistances[c] = segment_clear(from, corners[c])
                    ? from.distance_to(corners[c]) : Double.POSITIVE_INFINITY;
            toDistances[c] = segment_clear(corners[c], to)
                    ? corners[c].distance_to(to) : Double.POSITIVE_INFINITY;
        }
        double[] distance = new double[size];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        distance[0] = 0;
        boolean[] visited = new boolean[size];
        int[] previous = new int[size];
        Arrays.fill(previous, -1);
        for (int step = 0; step < size; step++) {
            int best = -1;
            for (int n = 0; n < size; n++) {
                if (!visited[n] && Double.isFinite(distance[n])
                        && (best < 0 || distance[n] < distance[best])) best = n;
            }
            if (best < 0) return null;
            if (best == 1) break;
            visited[best] = true;
            for (int n = 1; n < size; n++) {
                if (visited[n] || n == best) continue;
                double edge;
                if (best == 0) edge = n == 1 ? Double.POSITIVE_INFINITY : fromDistances[n - 2];
                else edge = n == 1 ? toDistances[best - 2] : cornerDistances[best - 2][n - 2];
                double candidate = distance[best] + edge;
                if (candidate < distance[n] - EPSILON) {
                    distance[n] = candidate;
                    previous[n] = best;
                }
            }
        }
        if (previous[1] < 0 || !Double.isFinite(distance[1])) return null;
        int count = 0;
        for (int n = 1; n != 0; n = previous[n]) {
            if (n < 0 || ++count > size) return null;
        }
        VisionPose[] points = new VisionPose[count];
        int write = count;
        for (int n = 1; n != 0; n = previous[n]) points[--write] = n == 1 ? to : corners[n - 2];
        return new Route(points, distance[1]);
    }

    private void add_corner(ArrayList<VisionPose> vertices, double x, double y) {
        VisionPose corner = new VisionPose(x, y, 0);
        if (!is_clear(corner)) return;
        for (VisionPose other : vertices) if (other.distance_to(corner) <= EPSILON) return;
        vertices.add(corner);
    }

    private static boolean intersects(VisionPose from, double dx, double dy, Box box) {
        double enter = 0, exit = 1;
        if (dx == 0) {
            if (from.x < box.minX - EPSILON || from.x > box.maxX + EPSILON) return false;
        } else {
            double a = (box.minX - EPSILON - from.x) / dx;
            double b = (box.maxX + EPSILON - from.x) / dx;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (enter > exit) return false;
        }
        if (dy == 0) {
            if (from.y < box.minY - EPSILON || from.y > box.maxY + EPSILON) return false;
        } else {
            double a = (box.minY - EPSILON - from.y) / dy;
            double b = (box.maxY + EPSILON - from.y) / dy;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        return enter <= exit;
    }
}
