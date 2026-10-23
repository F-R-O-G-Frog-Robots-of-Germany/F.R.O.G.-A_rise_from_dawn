package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Independent mouth geometry and whole-route capacity/timing checks; no SDK or physical robot. */
public final class ButineIntakeChecks {
    private static int checks;
    private static VisionPose pose(double x, double y, double h) { return new VisionPose(x, y, h); }
    private static Pollen pollen(int id, double x, double y) { return new Pollen(id, pose(x, y, 0), 1, 1, 0); }
    private static void require(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }
    private static void near(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-7,
                message + ": expected " + expected + ", got " + actual);
    }
    private static Butine.Params settings() {
        Butine.Params p = new Butine.Params();
        p.field = new ButineField(144, 18, 18, 1);
        p.remainingS = 100;
        p.scoreOverheadS = 0;
        p.lookaheadRuns = 1;
        return p;
    }

    public static void main(String[] args) {
        analytic_front_contacts();
        independent_random_contact_oracle();
        incidental_pickups_and_capacity();
        route_events_and_continuity();
        depot_heading_and_wall_reachability();
        timing_and_deadlines();
        intake_overshoot();
        legacy_and_configuration();
        System.out.println("Butine intake checks passed: " + checks);
    }

    private static void analytic_front_contacts() {
        VisionPose a = pose(20, 20, 0), b = pose(40, 20, 0);
        near(.48, ButineIntake.contact_fraction(a, b, pose(40, 27, 0), 0, 18, 9, 1.4), "front mouth contacts before center");
        require(Double.isNaN(ButineIntake.contact_fraction(a, b, pose(20, 20, 0), 0, 18, 9, 1.4)),
                "body center is not an intake contact");
        require(Double.isNaN(ButineIntake.contact_fraction(a, b, pose(20, 28, 0), 0, 18, 9, 1.4)),
                "side/back body radius is not an intake");
        require(Double.isNaN(ButineIntake.contact_fraction(a, b, pose(40, 27, 0), 0, 4, 9, 1.4)),
                "narrow width excludes lateral pollen");
        near(.23, ButineIntake.contact_fraction(a, pose(20, 40, 0), pose(29, 35, 0), 0, 18, 9, 1.4),
                "constant-heading holonomic strafe mouth contact");
        near(.48, ButineIntake.contact_fraction(a, pose(20, 40, 0), pose(27, 40, 0), Math.PI / 2, 18, 9, 1.4),
                "rotated front geometry");
        near((11 - Math.sqrt(1.4 * 1.4 - 1)) / 20,
                ButineIntake.contact_fraction(a, b, pose(40, 30, 0), 0, 18, 9, 1.4), "round endcap contact");
        near(.55, ButineIntake.contact_fraction(a, b, pose(40, 30.4, 0), 0, 18, 9, 1.4), "endcap tangency");
        near(0, ButineIntake.contact_fraction(a, a, pose(29, 20, 0), 0, 18, 9, 1.4), "stationary front contact");
    }

    private static double independent_distance(VisionPose a, VisionPose b, VisionPose pollen,
                                               double heading, double width, double offset, double fraction) {
        double cx = a.x + (b.x - a.x) * fraction + offset * Math.cos(heading);
        double cy = a.y + (b.y - a.y) * fraction + offset * Math.sin(heading);
        double ax = cx + width / 2 * Math.sin(heading), ay = cy - width / 2 * Math.cos(heading);
        double bx = cx - width / 2 * Math.sin(heading), by = cy + width / 2 * Math.cos(heading);
        double projection = ((pollen.x - ax) * (bx - ax) + (pollen.y - ay) * (by - ay)) / (width * width);
        projection = Math.max(0, Math.min(1, projection));
        return Math.hypot(pollen.x - (ax + projection * (bx - ax)), pollen.y - (ay + projection * (by - ay)));
    }

    private static void independent_random_contact_oracle() {
        Random random = new Random(4118);
        for (int test = 0; test < 400; test++) {
            VisionPose a = pose(20, 20, 0), b = pose(random.nextDouble() * 60, random.nextDouble() * 60, 0);
            VisionPose pollen = pose(random.nextDouble() * 60, random.nextDouble() * 60, 0);
            double heading = random.nextDouble() * 2 * Math.PI, width = 2 + random.nextDouble() * 16;
            double contact = ButineIntake.contact_fraction(a, b, pollen, heading, width, 9, 1.4);
            boolean sampledHit = false;
            for (int step = 0; step <= 1000; step++) {
                if (independent_distance(a, b, pollen, heading, width, 9, step / 1000.0) <= 1.4) {
                    sampledHit = true;
                    if (!Double.isNaN(contact)) require(contact <= step / 1000.0 + 1e-7, "analytic entry precedes sampled entry");
                    break;
                }
            }
            if (Double.isNaN(contact)) require(!sampledHit, "no analytic contact cannot hide an independently sampled hit");
            else {
                require(contact >= 0 && contact <= 1, "contact fraction is bounded");
                require(independent_distance(a, b, pollen, heading, width, 9, contact) <= 1.4 + 1e-7,
                        "analytic entry touches finite front segment");
                if (contact > 1e-5) require(independent_distance(a, b, pollen, heading, width, 9, contact - 1e-5) >= 1.4 - 1e-7,
                        "entry is the first contact, not an interior crossing");
            }
        }
    }

    private static void incidental_pickups_and_capacity() {
        Butine.Params p = settings();
        p.candidateCap = 1;
        VisionPose start = pose(30, 25, Math.PI / 2), depot = pose(30, 20, Math.PI / 2);
        List<Pollen> pair = Arrays.asList(pollen(1, 25, 50), pollen(2, 35, 50));
        Butine.Plan plan = Butine.solve(pair, start, depot, p);
        require(plan.fittingRuns == 1 && plan.runs[0].count == 2, "one optimized anchor sweeps neighboring pollen beyond candidate cap");
        require(plan.runs[0].route.pickup_segment(0) == plan.runs[0].route.pickup_segment(1), "pair is collected on the same moving segment");
        p.intakeWidthIn = 2;
        plan = Butine.solve(pair, start, depot, p);
        require(plan.fittingRuns == 1 && plan.runs[0].count == 2, "narrow mouth can collect via additional anchors");
        require(plan.runs[0].route.pickup_segment(0) != plan.runs[0].route.pickup_segment(1), "width changes actual swept route");
        p = settings(); p.candidateCap = 1;
        ArrayList<Pollen> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(pollen(i, 30, 50));
        plan = Butine.solve(five, start, depot, p);
        require(plan.fittingRuns == 0, "simultaneous fifth contact rejects the outbound pass rather than silently truncating IDs");
    }

    private static void route_events_and_continuity() {
        Butine.Params p = settings();
        VisionPose start = pose(30, 25, Math.PI / 2), depot = pose(30, 20, Math.PI / 2);
        List<Pollen> points = Arrays.asList(pollen(1, 25, 50), pollen(2, 35, 50), pollen(3, 80, 80));
        Butine.Run run = Butine.solve(points, start, depot, p).runs[0];
        ButineIntake.Route route = run.route;
        require(run.count == 3 && route.pickup_count() == 3, "all swept contacts are run members");
        near(start.x, route.waypoint(0).x, "path retains actual start");
        near(depot.y, route.waypoint(route.waypoint_count() - 1).y, "path returns to depot");
        near(0, route.segment_start_speed_in_s(0), "run departs from rest");
        near(0, route.segment_end_speed_in_s(route.segment_count() - 1), "run arrives at rest");
        double sum = 0;
        boolean returning = false;
        for (int i = 0; i < route.segment_count(); i++) {
            VisionPose a = route.waypoint(i), b = route.waypoint(i + 1);
            require(p.field.segment_clear(a, b), "every actual center segment satisfies field collision geometry");
            require(Double.isFinite(route.segment_time_s(i)) && route.segment_time_s(i) >= 0, "segment time is finite");
            if (route.intake_active(i)) {
                require(!returning, "intake never reopens on return");
                near(start.heading, a.heading, "collection heading stays fixed");
                near(start.heading, b.heading, "collection endpoint heading stays fixed");
            } else returning = true;
            if (i + 1 < route.segment_count()) near(route.segment_end_speed_in_s(i), route.segment_start_speed_in_s(i + 1),
                    "velocity magnitude carries across internal waypoints");
            sum += route.segment_time_s(i);
        }
        near(sum, run.timeS, "deadline cost is the actual continuous route sum");
        for (int i = 0; i < run.count; i++) {
            int segment = route.pickup_segment(i);
            require(route.intake_active(segment), "every pickup is on an active intake segment");
            near(route.pickup_fraction(i), ButineIntake.contact_fraction(route.waypoint(segment), route.waypoint(segment + 1),
                    run.poses[i], route.collectionHeading, route.intakeWidthIn, route.intakeForwardOffsetIn, route.pollenRadiusIn),
                    "stored event matches front geometry");
            boolean waypointAtPollen = false;
            for (int point = 0; point < route.waypoint_count(); point++)
                if (route.waypoint(point).distance_to(run.poses[i]) < 1e-7) waypointAtPollen = true;
            require(!waypointAtPollen, "observed pollen is not used as a required robot-center waypoint");
        }
    }

    private static void depot_heading_and_wall_reachability() {
        Butine.Params p = settings();
        VisionPose depot = pose(40, 30, Math.PI / 2);
        Butine.Plan plan = Butine.solve(Collections.singletonList(pollen(1, 40, 14)), depot, depot, p);
        require(plan.fittingRuns == 1, "depot may select another fixed collection heading to reach wall-side pollen");
        ButineIntake.Route route = plan.runs[0].route;
        near(depot.heading, route.waypoint(0).heading, "actual depot start heading is retained");
        require(!route.intake_active(0) && route.waypoint(0).distance_to(route.waypoint(1)) < 1e-9,
                "depot prealignment is explicit and intake off");
        require(route.segment_time_s(0) > 0, "depot rotation has a time cost");
        plan = Butine.solve(Collections.singletonList(pollen(1, 40, 14)), pose(40, 31, Math.PI / 2), depot, p);
        require(plan.fittingRuns == 0, "a live start away from depot does not invent a stationary intake sweep rotation");
    }

    private static void timing_and_deadlines() {
        Butine.Params p = settings();
        p.kinematics.omegaMaxRadS = 0;
        ButineIntake.Route home = ButineIntake.return_route(pose(20, 20, 0), pose(100, 20, 0), p);
        near(Butine.straight_time(80, p.kinematics), home.timeS, "single continuous segment matches independent rest-to-rest baseline");
        require(!home.intake_active(0) && home.pickup_count() == 0, "recovery never acquires pollen");
        p = settings();
        VisionPose start = pose(30, 25, Math.PI / 2), depot = pose(30, 20, Math.PI / 2);
        List<Pollen> points = Arrays.asList(pollen(1, 25, 50), pollen(2, 35, 50));
        Butine.Run run = Butine.solve(points, start, depot, p).runs[0];
        p.remainingS = run.timeS / 2;
        require(Butine.solve(points, start, depot, p).fittingRuns == 0, "continuous sweep time participates in deadline fitting");
    }

    private static void legacy_and_configuration() {
        Butine.Params p = settings(); p.field = ButineField.unrestricted();
        Butine.Run legacy = Butine.solve(Collections.singletonList(pollen(1, 2, 0)), pose(0, 0, 0), p).runs[0];
        require(legacy.route == null, "unrestricted mathematical calibration retains its point-pickup route semantics");
        Butine.Params original = settings(), copied = new Butine.Params(original);
        original.intakeWidthIn = 2;
        original.intakeOvershootIn = 3;
        near(18, copied.intakeWidthIn, "parameter copies snapshot the one direct intake width setting");
        near(1, copied.intakeOvershootIn, "parameter copies snapshot the direct one-inch overshoot setting");
        copied.intakeWidthIn = 30;
        boolean rejected = false;
        try { Butine.solve(Collections.singletonList(pollen(1, 40, 40)), pose(30, 30, 0), copied); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "an intake outside the configured robot clearance envelope is not silently accepted");
        for (double bad : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Butine.Params invalid = settings(); invalid.intakeOvershootIn = bad;
            rejected = false;
            try { Butine.solve(Collections.singletonList(pollen(1, 40, 40)), pose(30, 30, 0), invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "invalid overshoot is rejected: " + bad);
        }
    }

    private static int final_active_segment(ButineIntake.Route route) {
        for (int segment = route.segment_count() - 1; segment >= 0; segment--)
            if (route.intake_active(segment)) return segment;
        throw new AssertionError("No active collection segment");
    }

    private static VisionPose contact_pose(ButineIntake.Route route, int pickup) {
        int segment = route.pickup_segment(pickup);
        VisionPose a = route.waypoint(segment), b = route.waypoint(segment + 1);
        double fraction = route.pickup_fraction(pickup);
        return pose(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction, route.collectionHeading);
    }

    private static void intake_overshoot() {
        Butine.Params p = settings();
        VisionPose start = pose(30, 25, Math.PI / 2), depot = pose(30, 20, Math.PI / 2);
        List<Pollen> one = Collections.singletonList(pollen(1, 30, 50));
        Butine.Run run = Butine.solve(one, start, depot, p).runs[0];
        ButineIntake.Route route = run.route;
        VisionPose touch = contact_pose(route, 0), endpoint = route.waypoint(final_active_segment(route) + 1);
        near(39.6, touch.y, "first front-surface contact remains an event before the endpoint");
        near(40.6, endpoint.y, "robot drives one inch beyond front-surface contact");
        near(1, touch.distance_to(endpoint), "one-inch default overshoot is stored in actual center geometry");
        near(1, route.intakeOvershootIn, "route publishes immutable overshoot metadata");
        require(route.pickup_fraction(0) < 1, "intake stays active beyond the pickup event");
        near(.4, independent_distance(start, endpoint, run.poses[0], Math.PI / 2, 18, 9, 1),
                "mouth penetrates the pollen disk by one inch rather than merely touching it");
        double overshootTime = run.timeS;
        p.intakeOvershootIn = 0;
        Butine.Run touching = Butine.solve(one, start, depot, p).runs[0];
        near(1, touching.route.pickup_fraction(0), "explicit zero overshoot preserves the touch endpoint");
        require(overshootTime > touching.timeS, "overshoot travel participates in the actual deadline cost");

        p = settings();
        start = pose(25, 25, 0); depot = pose(20, 25, 0);
        run = Butine.solve(Collections.singletonList(pollen(1, 70, 55)), start, depot, p).runs[0];
        route = run.route;
        int segment = final_active_segment(route);
        VisionPose a = route.waypoint(segment), b = route.waypoint(segment + 1);
        touch = contact_pose(route, run.count - 1);
        double dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy);
        near(1, touch.distance_to(b), "diagonal approach also advances exactly one inch");
        near(0, (b.x - touch.x) * dy - (b.y - touch.y) * dx, "overshoot retains the same incoming angle");
        require(((b.x - touch.x) * dx + (b.y - touch.y) * dy) / length > .99,
                "overshoot advances forward on the incoming ray");

        start = pose(25, 30, 0); depot = pose(20, 30, 0);
        p = settings(); p.minConfidence = .5; p.candidateCap = 1;
        List<Pollen> chain = Arrays.asList(pollen(1, 50, 30),
                new Pollen(2, pose(50, 40.4, 0), 0, 1, 0),
                new Pollen(3, pose(51, 40.3, 0), 0, 1, 0));
        run = Butine.solve(chain, start, depot, p).runs[0];
        route = run.route;
        require(run.count == 3, "overshoot retains newly encountered incidental pollen IDs");
        endpoint = route.waypoint(final_active_segment(route) + 1);
        touch = contact_pose(route, run.count - 1);
        near(1, touch.distance_to(endpoint), "closure provides a full inch beyond the newly incidentally touched pollen");
        require(endpoint.x > 42, "new incidental contact extends the initial overshoot endpoint again");

        p.field = new ButineField(144, 18, 18, 1,
                new ButineField.Box(41.5 + p.field.radiusIn, 65, 25, 35));
        List<Pollen> blocked = chain.subList(0, 2);
        p.intakeOvershootIn = 0;
        require(Butine.solve(blocked, start, depot, p).fittingRuns == 1, "touch-only route is clear before the nearby obstacle");
        p.intakeOvershootIn = 1;
        require(Butine.solve(blocked, start, depot, p).fittingRuns == 0, "blocked straight overshoot rejects the candidate");

        p = settings(); p.minConfidence = .5; p.candidateCap = 1;
        List<Pollen> fifth = Arrays.asList(pollen(1, 50, 30), pollen(2, 50, 30), pollen(3, 50, 30),
                new Pollen(4, pose(50, 40.4, 0), 0, 1, 0),
                new Pollen(5, pose(51, 40.3, 0), 0, 1, 0));
        p.intakeOvershootIn = 0;
        require(Butine.solve(fifth, start, depot, p).runs[0].count == 4, "four contacts fit before extending the pass");
        p.intakeOvershootIn = 1;
        require(Butine.solve(fifth, start, depot, p).fittingRuns == 0, "incidental fifth contact during overshoot rejects the whole pass");
    }
}
