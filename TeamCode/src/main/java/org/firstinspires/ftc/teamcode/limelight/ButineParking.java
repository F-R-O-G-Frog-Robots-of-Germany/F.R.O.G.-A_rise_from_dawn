package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;

/** Pure partial-overlap parking geometry and intake-off routing from a live field pose. */
public final class ButineParking {
    public static final double DEFAULT_OVERLAP_IN = 1.0;
    private static final double EPSILON = 1e-9;
    private static final double NOMINAL_FIELD_SIZE_IN = 144.0;
    private static final double LOADING_DEPTH_IN = 11.0;
    private static final double LOADING_WIDTH_IN = 23.0;

    private ButineParking() { }

    /**
     * Immutable actual-field rectangle, including its boundary tape. Bounds may be surveyed.
     * leftWall selects the outside approach and final heading; it does not mirror live poses.
     */
    public static final class Zone {
        public final double minX, maxX, minY, maxY;
        public final boolean leftWall;

        public Zone(double minX, double maxX, double minY, double maxY, boolean leftWall) {
            if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                    || !Double.isFinite(minY) || !Double.isFinite(maxY)
                    || minX >= maxX || minY >= maxY)
                throw new IllegalArgumentException("Finite positive loading-zone rectangle required");
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.leftWall = leftWall;
        }
    }

    /**
     * Nominal Red A5 zone in the repo's 144-inch audience-oriented frame.
     * Manual 9.3 gives approximately 23 by 11 inches; setup 8.3 locates tape at tile seams.
     * These centered nominal bounds are model defaults, not surveyed official coordinates.
     */
    public static Zone red_zone() {
        double centerY = 3.0 * NOMINAL_FIELD_SIZE_IN / 4.0;
        return new Zone(0, LOADING_DEPTH_IN, centerY - LOADING_WIDTH_IN / 2.0,
                centerY + LOADING_WIDTH_IN / 2.0, true);
    }

    /** Blue F2 is the 180-degree field rotation of Red A5, not a corner zone. */
    public static Zone blue_zone() {
        Zone red = red_zone();
        return new Zone(NOMINAL_FIELD_SIZE_IN - red.maxX, NOMINAL_FIELD_SIZE_IN - red.minX,
                NOMINAL_FIELD_SIZE_IN - red.maxY, NOMINAL_FIELD_SIZE_IN - red.minY, false);
    }

    public static VisionPose default_target(Zone zone, ButineField field) {
        return default_target(zone, field, DEFAULT_OVERLAP_IN);
    }

    /**
     * Axis-aligned outside target using the configured body length and requested overlap.
     * Returns null if this default is blocked or the conservative wall envelope cannot park.
     * A caller may supply a different validated pose for alliance-partner coordination.
     */
    public static VisionPose default_target(Zone zone, ButineField field, double overlapIn) {
        if (!usable(zone, field, overlapIn)) return null;
        double x = zone.leftWall ? zone.maxX + field.robotLengthIn / 2.0 - overlapIn
                : zone.minX - field.robotLengthIn / 2.0 + overlapIn;
        double y = zone.minY / 2.0 + zone.maxY / 2.0;
        VisionPose target = new VisionPose(x, y, zone.leftWall ? 0 : Math.PI);
        return is_parked(target, zone, field, overlapIn) ? target : null;
    }

    /**
     * Positive overlap of the centered, oriented physical body with the loading-zone volume.
     * All separating axes must overlap by the requested reserve, in inches. With zero reserve,
     * exact tangency is rejected. Collision/wall clearance remains independently mandatory.
     * This assumes the configured rectangle is occupied body, not a concave bounding box.
     */
    public static boolean is_parked(VisionPose pose, Zone zone, ButineField field, double overlapIn) {
        if (!usable(zone, field, overlapIn) || pose == null || !pose.is_finite()
                || !field.is_clear(pose)) return false;
        double c = Math.cos(pose.heading), s = Math.sin(pose.heading);
        return axis_overlaps(pose, zone, field, overlapIn, 1, 0, c, s)
                && axis_overlaps(pose, zone, field, overlapIn, 0, 1, c, s)
                && axis_overlaps(pose, zone, field, overlapIn, c, s, c, s)
                && axis_overlaps(pose, zone, field, overlapIn, -s, c, c, s);
    }

    private static boolean usable(Zone zone, ButineField field, double overlapIn) {
        return zone != null && field != null && !field.is_unrestricted()
                && Double.isFinite(overlapIn) && overlapIn >= 0;
    }

    private static boolean axis_overlaps(VisionPose pose, Zone zone, ButineField field,
                                         double overlapIn, double ax, double ay, double c, double s) {
        double robotCenter = pose.x * ax + pose.y * ay;
        double robotRadius = field.robotLengthIn / 2.0 * Math.abs(ax * c + ay * s)
                + field.robotWidthIn / 2.0 * Math.abs(-ax * s + ay * c);
        double zoneCenter = (zone.minX / 2.0 + zone.maxX / 2.0) * ax
                + (zone.minY / 2.0 + zone.maxY / 2.0) * ay;
        double zoneRadius = (zone.maxX - zone.minX) / 2.0 * Math.abs(ax)
                + (zone.maxY - zone.minY) / 2.0 * Math.abs(ay);
        double overlap = Math.min(robotCenter + robotRadius, zoneCenter + zoneRadius)
                - Math.max(robotCenter - robotRadius, zoneCenter - zoneRadius);
        return Double.isFinite(overlap)
                && (overlapIn == 0 ? overlap > EPSILON : overlap + EPSILON >= overlapIn);
    }

    /** Fixed-heading recovery from an already-invalid nominal pose, not normal free-floor travel. */
    public static final class Egress {
        public final VisionPose from, target;
        public final double timeS;

        private Egress(VisionPose from, VisionPose target, double timeS) {
            this.from = from;
            this.target = target;
            this.timeS = timeS;
        }
    }

    /** Recovery, if needed, followed by a strict normal route. Execute the two phases separately. */
    public static final class Approach {
        public final Egress egress;
        public final ButineIntake.Route route;
        public final double timeS;

        private Approach(Egress egress, ButineIntake.Route route) {
            this.egress = egress;
            this.route = route;
            timeS = (egress == null ? 0 : egress.timeS) + route.timeS;
        }
    }

    /**
     * Allows a checked, non-rotating exit from an authored pose outside the conservative envelope.
     * Existing nominal body/clearance overlap may only decrease; no new overlap is allowed.
     * This is recovery from an already-invalid model state, not a physical collision guarantee.
     * After braking at egress.target, the caller must recompute route() from its new live pose.
     */
    public static Approach approach(VisionPose from, VisionPose target, Butine.Params params) {
        if (from == null || target == null || !from.is_finite() || !target.is_finite()
                || params == null || params.field == null || params.kinematics == null
                || !params.field.is_clear(target)) return null;
        ButineField field = params.field;
        if (field.is_clear(from)) {
            ButineIntake.Route normal = route(from, target, params);
            return normal == null ? null : new Approach(null, normal);
        }
        if (field.is_unrestricted() || from.x < 0 || from.y < 0
                || from.x > field.fieldSizeIn || from.y > field.fieldSizeIn) return null;
        try {
            Butine.leg_time(from.heading, from, from, params.kinematics);
            double inset = field.radiusIn + ButineField.ROUTING_OFFSET_IN;
            double far = field.fieldSizeIn - inset;
            if (inset > far) return null;
            ArrayList<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
            add_coordinate(xs, Math.max(inset, Math.min(far, from.x)));
            add_coordinate(ys, Math.max(inset, Math.min(far, from.y)));
            for (int i = 0; i < field.box_count(); i++) {
                ButineField.Box box = field.exclusion_box(i);
                if (from.x < box.minX - EPSILON || from.x > box.maxX + EPSILON
                        || from.y < box.minY - EPSILON || from.y > box.maxY + EPSILON) continue;
                add_coordinate(xs, Math.max(inset, Math.min(far,
                        box.minX - ButineField.ROUTING_OFFSET_IN)));
                add_coordinate(xs, Math.max(inset, Math.min(far,
                        box.maxX + ButineField.ROUTING_OFFSET_IN)));
                add_coordinate(ys, Math.max(inset, Math.min(far,
                        box.minY - ButineField.ROUTING_OFFSET_IN)));
                add_coordinate(ys, Math.max(inset, Math.min(far,
                        box.maxY + ButineField.ROUTING_OFFSET_IN)));
            }
            Approach best = null;
            for (double x : xs) for (double y : ys) {
                VisionPose exit = new VisionPose(x, y, from.heading);
                if (!egress_clear(from, exit, field)) continue;
                ButineIntake.Route normal = route(exit, target, params);
                if (normal == null) continue;
                double seconds = Butine.straight_time(from.distance_to(exit), params.kinematics);
                Approach candidate = new Approach(new Egress(from, exit, seconds), normal);
                if (Double.isFinite(candidate.timeS)
                        && (best == null || candidate.timeS < best.timeS - EPSILON)) best = candidate;
            }
            return best;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static void add_coordinate(ArrayList<Double> values, double value) {
        for (double existing : values) if (Math.abs(existing - value) <= EPSILON) return;
        values.add(value);
    }

    /** Revalidate the complete constant-heading recovery segment immediately before dispatch. */
    public static boolean egress_clear(VisionPose from, VisionPose to, ButineField field) {
        if (from == null || to == null || field == null || field.is_unrestricted()
                || !from.is_finite() || !to.is_finite() || !field.is_clear(to)
                || Math.abs(VisionPose.wrap_pi(to.heading - from.heading)) > EPSILON
                || from.x < 0 || from.x > field.fieldSizeIn || from.y < 0 || from.y > field.fieldSizeIn)
            return false;
        // A normal start never receives a recovery waiver from the usual all-heading router.
        if (field.is_clear(from)) return field.segment_clear(from, to);
        double c = Math.cos(from.heading), s = Math.sin(from.heading);
        double halfX = field.robotLengthIn / 2 * Math.abs(c) + field.robotWidthIn / 2 * Math.abs(s);
        double halfY = field.robotLengthIn / 2 * Math.abs(s) + field.robotWidthIn / 2 * Math.abs(c);
        double margin = field.bodyClearanceIn + field.fieldReserveIn + field.trackingReserveIn;
        // With constant heading every wall deficit is affine, so endpoints bound the whole move.
        if (!deficit_not_worse(halfX + margin - from.x, halfX + margin - to.x)
                || !deficit_not_worse(from.x + halfX + margin - field.fieldSizeIn,
                        to.x + halfX + margin - field.fieldSizeIn)
                || !deficit_not_worse(halfY + margin - from.y, halfY + margin - to.y)
                || !deficit_not_worse(from.y + halfY + margin - field.fieldSizeIn,
                        to.y + halfY + margin - field.fieldSizeIn)) return false;
        for (int i = 0; i < field.box_count(); i++) {
            ButineField.Box box = field.physical_box(i);
            if (!obstacle_recovery_clear(from, to, field, box, margin, c, s)) return false;
        }
        return true;
    }

    private static boolean deficit_not_worse(double initial, double end) {
        return Double.isFinite(initial) && Double.isFinite(end) && end <= Math.max(0, initial) + EPSILON;
    }

    private static boolean obstacle_recovery_clear(VisionPose from, VisionPose to, ButineField field,
                                                     ButineField.Box box, double margin, double c, double s) {
        double[][] axes = {{1, 0}, {0, 1}, {c, s}, {-s, c}};
        double bx = box.minX / 2 + box.maxX / 2, by = box.minY / 2 + box.maxY / 2;
        double hx = (box.maxX - box.minX) / 2 + margin;
        double hy = (box.maxY - box.minY) / 2 + margin;
        boolean initiallySeparated = false;
        for (double[] axis : axes) {
            double rr = robot_radius(field, c, s, axis[0], axis[1]);
            double br = hx * Math.abs(axis[0]) + hy * Math.abs(axis[1]);
            double p = (from.x - bx) * axis[0] + (from.y - by) * axis[1];
            if (projection_deficit(p, rr, br) < -EPSILON) initiallySeparated = true;
        }
        if (initiallySeparated) {
            // Swept OBB = rectangle + translation segment. Its SAT axes add the segment normal.
            for (double[] axis : axes)
                if (swept_separates(from, to, field, bx, by, hx, hy, c, s, axis[0], axis[1])) return true;
            double dx = to.x - from.x, dy = to.y - from.y, length = Math.hypot(dx, dy);
            return length > EPSILON && swept_separates(from, to, field, bx, by, hx, hy,
                    c, s, -dy / length, dx / length);
        }
        // The overlap along each SAT axis is maximal at the smallest absolute center offset.
        // Checking that maximum prevents even a brief increase between the endpoints.
        for (double[] axis : axes) {
            double rr = robot_radius(field, c, s, axis[0], axis[1]);
            double br = hx * Math.abs(axis[0]) + hy * Math.abs(axis[1]);
            double p0 = (from.x - bx) * axis[0] + (from.y - by) * axis[1];
            double p1 = (to.x - bx) * axis[0] + (to.y - by) * axis[1];
            double closest = p0 * p1 <= 0 ? 0 : Math.min(Math.abs(p0), Math.abs(p1));
            if (projection_deficit(closest, rr, br) > Math.max(0, projection_deficit(p0, rr, br)) + EPSILON)
                return false;
        }
        return true;
    }

    private static double robot_radius(ButineField field, double c, double s, double ax, double ay) {
        return field.robotLengthIn / 2 * Math.abs(ax * c + ay * s)
                + field.robotWidthIn / 2 * Math.abs(-ax * s + ay * c);
    }

    private static double projection_deficit(double center, double radius, double otherRadius) {
        // Unlike intersection length, this detects deeper motion even when one interval contains the other.
        return radius + otherRadius - Math.abs(center);
    }

    private static boolean swept_separates(VisionPose from, VisionPose to, ButineField field,
                                             double bx, double by, double hx, double hy,
                                             double c, double s, double ax, double ay) {
        double rr = robot_radius(field, c, s, ax, ay);
        double br = hx * Math.abs(ax) + hy * Math.abs(ay);
        double p0 = (from.x - bx) * ax + (from.y - by) * ay;
        double p1 = (to.x - bx) * ax + (to.y - by) * ay;
        return Math.min(p0, p1) - rr > br + EPSILON || Math.max(p0, p1) + rr < -br - EPSILON;
    }

    /**
     * Safe intake-off path from the supplied live pose to a caller-validated parking target.
     * Route.timeS uses Butine's continuous travel/turn model; scoring overhead is excluded.
     * Invalid or unreachable geometry returns null, never a direct unchecked shortcut.
     */
    public static ButineIntake.Route route(VisionPose from, VisionPose target, Butine.Params params) {
        if (from == null || target == null || !from.is_finite() || !target.is_finite()
                || params == null || params.field == null || params.kinematics == null
                || !params.field.is_clear(from) || !params.field.is_clear(target)) return null;
        try {
            // Reuse the planner's public validation without charging an extra travel leg.
            Butine.leg_time(from.heading, from, from, params.kinematics);
            double angle = Math.abs(VisionPose.wrap_pi(VisionPose.wrap_pi(target.heading)
                    - VisionPose.wrap_pi(from.heading)));
            if (angle > params.kinematics.turnDeadbandRad
                    && params.kinematics.omegaMaxRadS <= EPSILON) return null;
            ButineIntake.Route route = ButineIntake.return_route(from, target, params);
            return route != null && Double.isFinite(route.timeS) && route.timeS >= 0 ? route : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
