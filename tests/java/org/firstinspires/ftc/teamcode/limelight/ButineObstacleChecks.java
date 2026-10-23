package org.firstinspires.ftc.teamcode.limelight;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Velocity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Clearance geometry, routed deadlines and real runner semantics with a simulated drive. */
public final class ButineObstacleChecks {
    private static int checks;
    private static final VisionPose START = pose(20, 50);
    private static final VisionPose PICKUP = pose(80, 50);

    public static void main(String[] args) throws Exception {
        footprint_and_official_underpass();
        boundaries_detours_and_unreachable();
        routed_solver_and_deadline();
        transit_collection_and_live_origin();
        recovery_return_and_worker_snapshot();
        real_pedro_live_origin_guard();
        possession_limit_in_solver_and_runner();
        System.out.println("Butine obstacle checks passed: " + checks);
    }

    private static VisionPose pose(double x, double y) { return new VisionPose(x, y, 0); }

    private static void possession_limit_in_solver_and_runner() {
        require(Butine.POSSESSION_LIMIT == 4 && new Butine.Params().capacity == 4,
                "production default possession limit is four");
        VisionPose depot = pose(72, 20);
        List<Pollen> points = new ArrayList<>();
        for (int i = 0; i < 8; i++) points.add(pollen(i + 1, pose(72, 25 + 3 * i)));
        for (int requested : new int[]{0, 20}) {
            Butine.Params settings = params(ButineField.biobuzz());
            settings.capacity = requested; settings.lookaheadRuns = 3;
            Butine.Plan plan = Butine.solve(points, depot, depot, settings);
            require(!plan.uncapacitated && plan.expectedCollected == 8,
                    "physical field converts unbounded/oversized requests to four-pollen loads");
            for (Butine.Run run : plan.runs) require(run.count <= 4, "no planned physical-field load exceeds four");
            PollenMap map = new PollenMap();
            for (Pollen point : points) map.inject(point.position);
            Drive drive = new Drive(); drive.pose = depot;
            ButineRunner runner = new ButineRunner(map, drive);
            require(runner.run_butine(depot, settings, 1), "runtime accepts bounded physical-field request");
            require(runner.last_plan().runs[0].count <= 4, "actual runner also enforces the possession limit");
            runner.stop();
        }
    }
    private static ButineField test_field() {
        return new ButineField(100, 18, 18, 1, new ButineField.Box(45, 55, 30, 70));
    }
    private static Butine.Params params(ButineField field) {
        Butine.Params p = new Butine.Params();
        p.sweepIntake = false;
        p.field = field; p.capacity = 1; p.lookaheadRuns = 1;
        p.iterationCap = 0; p.localSearchPasses = 0; p.scoreOverheadS = 0;
        p.remainingS = 100;
        return p;
    }
    private static Pollen pollen(int id, VisionPose position) { return new Pollen(id, position, 1, 1); }
    private static void require(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }
    private static void near(double expected, double actual, String message) {
        require(Math.abs(expected - actual) <= 1e-7 * Math.max(1, Math.abs(expected)),
                message + ": expected " + expected + ", got " + actual);
    }

    private static void footprint_and_official_underpass() {
        ButineField field = ButineField.biobuzz();
        near(Math.sqrt(162) + 1, field.radiusIn, "robot half diagonal plus one-inch clearance");
        require(field.is_clear(pose(72, 72)), "middle of the field is open below the Hives");
        require(field.segment_clear(pose(72, 20), pose(72, 124)),
                "an 18-inch-high robot can drive straight between the low frame supports");
        require(!field.is_clear(pose(50, 72)) && !field.is_clear(pose(94, 72)),
                "both low frame sides remain obstacles");
        for (VisionPose flower : new VisionPose[]{pose(48, 140), pose(140, 96), pose(96, 4), pose(4, 48)})
            require(!field.is_clear(flower), "each full flower assembly is blocked");
        require(!field.is_clear(pose(9, 20)), "center nine inches from wall has no rotating-corner clearance");
        ButineField bigger = new ButineField(144, 20, 18, 1);
        near(Math.hypot(10, 9) + 1, bigger.radiusIn, "dimensions drive the envelope");
        ButineField reserved = new ButineField(100, 18, 18, 1, 1, .5,
                new ButineField.Box(45, 55, 30, 70));
        near(field.radiusIn + 1.5, reserved.radiusIn, "field and tracking reserves remain explicit");
        require(!reserved.is_clear(pose(field.radiusIn + .5, 20)), "reserves also increase wall inset");
        require(!reserved.is_clear(pose(45 - field.radiusIn - .5, 50)),
                "reserves increase obstacle exclusion too");
        require(ButineField.biobuzz(18, 18, 18, 1, 0, 0).segment_clear(pose(72, 20), pose(72, 124)),
                "configured nominal height retains underpass");
        require(!ButineField.biobuzz(18, 18, 25, 1, 0, 0).is_clear(pose(72, 72)),
                "taller robot cannot reuse the 19-inch support slice");
        require(!ButineField.biobuzz(18, 18, 18, 1, 1, 0).is_clear(pose(72, 72)),
                "vertical field reserve beyond verified slice retains full center exclusion");
        ButineField.Box[] boxes = {new ButineField.Box(45, 55, 30, 70)};
        ButineField immutable = new ButineField(100, 18, 18, 1, boxes);
        boxes[0] = new ButineField.Box(15, 25, 15, 25);
        require(!immutable.is_clear(pose(50, 50)) && immutable.is_clear(pose(20, 20)),
                "caller cannot replace the field's obstacle snapshot");
    }

    private static void boundaries_detours_and_unreachable() {
        ButineField field = test_field();
        double r = field.radiusIn;
        require(!field.is_clear(pose(45 - r, 50)), "obstacle boundary contact is forbidden");
        require(!field.segment_clear(START, PICKUP), "safe endpoints can have a blocked connecting line");
        require(!field.segment_clear(pose(20, 30 - r), pose(80, 30 - r)), "tangent edge contact is rejected");
        require(field.segment_clear(START, START), "clear coincident pose is allowed");
        require(!field.segment_clear(pose(50, 50), pose(50, 50)), "coincident point inside obstacle is rejected");
        require(!field.is_clear(pose(Double.NaN, 30)), "invalid numeric pose cannot be clear");
        ButineField.Route route = field.route(START, PICKUP);
        require(route != null && route.point_count() >= 3, "blocked direct route gains transit corners");
        require(route.distanceIn > START.distance_to(PICKUP), "detour has a longer geometric length");
        independent_clearance(route, START, r, 45, 55, 30, 70);
        near(0, route.point(route.point_count() - 1).distance_to(PICKUP), "route reaches requested endpoint");
        ButineField wall = new ButineField(100, 18, 18, 1, new ButineField.Box(45, 55, 0, 100));
        require(wall.route(START, PICKUP) == null, "field-spanning wall has no false passage");
        require(field.route(pose(5, 50), PICKUP) == null, "invalid origin has no route");
        require(field.route(START, pose(50, 50)) == null, "blocked destination has no route");
        ButineField overlap = new ButineField(144, 2, 2, 1,
                new ButineField.Box(50, 70, 45, 75), new ButineField.Box(60, 80, 65, 90));
        VisionPose from = pose(30, 70), to = pose(110, 70);
        ButineField.Route around = overlap.route(from, to);
        require(around != null, "overlapping boxes do not hide the outer route");
        independent_clearance(around, from, overlap.radiusIn, 50, 70, 45, 75);
        independent_clearance(around, from, overlap.radiusIn, 60, 80, 65, 90);
    }

    private static void routed_solver_and_deadline() {
        ButineField field = test_field();
        Butine.Params p = params(field);
        p.kinematics.pickupDwellS = .2;
        List<Pollen> items = new ArrayList<>();
        items.add(pollen(1, PICKUP));
        Butine.Plan plan = Butine.solve(items, START, START, p);
        require(plan.field == field && plan.expectedCollected == 1, "plan retains the immutable geometry snapshot");
        List<ButineRunner.Leg> legs = ButineRunner.build_run_legs(plan.runs[0], START, START, p.kinematics, field);
        double emittedTime = 0;
        for (ButineRunner.Leg leg : legs) {
            require(field.segment_clear(leg.from, leg.target), "each emitted segment is clear");
            emittedTime += leg.predictedS;
        }
        near(plan.runs[0].timeS, emittedTime, "solver cost matches emitted detour turns and dwell");
        double direct = Butine.run_time(START, START, new VisionPose[]{PICKUP}, 1, p.kinematics);
        require(emittedTime > direct, "deadline accounts for detour cost");
        p.remainingS = (direct + emittedTime) / 2;
        require(Butine.solve(items, START, START, p).fittingRuns == 0,
                "a direct-line budget cannot accept the longer safe route");
        p = params(field); p.candidateCap = 1;
        items.add(0, pollen(2, pose(50, 50)));
        Butine.Plan filtered = Butine.solve(items, START, START, p);
        require(filtered.candidatesConsidered == 1 && filtered.runs[0].ids[0] == 1,
                "blocked nearby pollen does not consume candidate cap");
        require(Butine.solve(items, pose(50, 50), START, p).runCount == 0,
                "invalid start yields an empty plan");
        require(Butine.solve(items, START, pose(50, 50), p).runCount == 0,
                "invalid return depot yields an empty plan");
    }

    private static final class Drive implements ButineRunner.Drive {
        VisionPose pose = START;
        ButineRunner.Leg leg;
        boolean busy;
        int dispatched, stopped;
        public VisionPose pose() { return pose; }
        public boolean is_busy() { return busy; }
        public void follow(ButineRunner.Leg next, double speed) {
            require(next.field.segment_clear(pose, next.target), "fake drive never receives an unsafe live segment");
            leg = next; dispatched++; busy = true;
        }
        public void stop() { busy = false; stopped++; }
        public void set_pose(VisionPose value) { pose = value; }
    }

    private static void transit_collection_and_live_origin() {
        ButineField field = test_field();
        PollenMap map = new PollenMap(); map.inject(PICKUP);
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(START, params(field), 1), "routed collection starts");
        int transitArrivals = 0;
        for (int guard = 0; runner.is_busy() && guard < 100; guard++) {
            runner.update();
            if (!drive.busy) continue;
            runner.update();
            int beforeCollected = runner.stats().collected;
            int beforeTracks = map.snapshot().size();
            boolean transit = drive.leg.kind == ButineRunner.Leg.Kind.TRANSIT;
            drive.pose = drive.leg.target; drive.busy = false; runner.update();
            if (transit) {
                transitArrivals++;
                require(beforeCollected == runner.stats().collected && beforeTracks == map.snapshot().size(),
                        "transit neither collects nor removes tracks");
                require(runner.state() != ButineRunner.State.COMPLETE, "return transit does not finish the run");
            }
        }
        require(transitArrivals >= 4 && runner.state() == ButineRunner.State.COMPLETE,
                "pickup and return detours reach final depot");
        require(runner.stats().collected == 1 && map.snapshot().isEmpty(), "only the real pickup counts");
        near(0, drive.pose.distance_to(START), "run returns to requested depot");

        map = new PollenMap(); map.inject(PICKUP); drive = new Drive(); runner = new ButineRunner(map, drive);
        require(runner.run_butine(START, params(field), 1), "second route starts");
        drive.pose = pose(50, 50); runner.update();
        require(runner.state() == ButineRunner.State.ABORTED && drive.dispatched == 0 && drive.stopped > 0,
                "forbidden live origin stops before first dispatch");
    }

    private static void recovery_return_and_worker_snapshot() throws Exception {
        ButineField field = test_field();
        PollenMap map = new PollenMap(); map.inject(PICKUP);
        Drive drive = new Drive(); ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(START, params(field), 1), "recovery scenario starts");
        boolean failedPickup = false;
        for (int guard = 0; runner.is_busy() && guard < 150; guard++) {
            runner.update();
            if (!drive.busy) continue;
            runner.update();
            if (!failedPickup && drive.leg.kind == ButineRunner.Leg.Kind.PICKUP) {
                drive.busy = false;
                failedPickup = true;
            } else { drive.pose = drive.leg.target; drive.busy = false; }
            runner.update();
        }
        require(failedPickup && runner.state() == ButineRunner.State.COMPLETE,
                "failed pickup returns around obstacle through routed recovery");
        require(runner.stats().collected == 0 && map.snapshot().size() == 1,
                "failed pickup stays visible and is not claimed");

        ButineWorker worker = new ButineWorker(); worker.preferredCore = -1;
        require(worker.start(), "worker starts for geometry snapshot check");
        try {
            Butine.Params original = params(field);
            long id = worker.request_plan(Collections.singletonList(pollen(1, PICKUP)), START, START, original, 0);
            original.field = ButineField.unrestricted();
            long deadline = System.nanoTime() + 5_000_000_000L;
            ButineWorker.Result result;
            while ((result = worker.latest_result()) == null && System.nanoTime() < deadline) Thread.sleep(1);
            require(result != null && result.requestId == id && result.error == null && result.plan.field == field,
                    "worker captures geometry before caller replaces its setting");
            require(result.plan.runs[0].timeS > Butine.run_time(START, START,
                    new VisionPose[]{PICKUP}, 1, original.kinematics), "worker retains routed cost after caller edits");
        } finally { require(worker.stop(), "worker stops after geometry check"); }
    }

    private static void real_pedro_live_origin_guard() {
        ButineField field = test_field();
        VisionPose next = field.route(START, PICKUP).point(0);
        require(field.segment_clear(START, next) && !field.segment_clear(PICKUP, next),
                "nominal line is clear but displaced actual origin would cross the obstacle");
        Localizer localizer = new Localizer() {
            Pose current = new Pose(PICKUP.x, PICKUP.y, 0);
            public void setPose(Pose value) { current = value; }
            public MotionState state() { return MotionState.ofVelocity(current, Velocity.zero()); }
            public void update() { }
            public void reset() { current = Pose.zero(); }
        };
        final int[] stops = {0};
        Drivetrain motors = new Drivetrain() {
            public void drive(DrivePowers powers, boolean manual) { }
            public double maxScaling(DrivePowers a, DrivePowers b) { return 1; }
            public void stop() { stops[0]++; }
            public void stop(boolean brake) { stop(); }
            public Map<String, Object> debug() { return Collections.emptyMap(); }
            public double interpolateVelocity(double forward, double strafe, double heading) { return 72; }
        };
        Follower follower = new Follower(localizer, motors, new Foresight(new ForesightConfig(c -> {
            c.naturalForwardDeceleration.set(46.0);
            c.naturalStrafeDeceleration.set(79.0);
        })));
        PedroButineDrive adapter = new PedroButineDrive(follower);
        ButineRunner.Leg leg = new ButineRunner.Leg(START, next, -1, 1, 0,
                ButineRunner.Leg.Kind.TRANSIT, field);
        boolean rejected = false;
        try { adapter.follow(leg, 1); }
        catch (IllegalStateException blocked) { rejected = true; }
        require(rejected && stops[0] > 0 && !follower.following(),
                "real Pedro adapter stops motors and rejects unsafe actual-origin path");
    }

    /** Independent clearance oracle using endpoints, box corners and segment/edge intersections. */
    private static void independent_clearance(ButineField.Route route, VisionPose from, double radius,
                                               double minX, double maxX, double minY, double maxY) {
        VisionPose previous = from;
        for (int i = 0; i < route.point_count(); i++) {
            VisionPose next = route.point(i);
            double distance = Math.min(point_box(previous, minX, maxX, minY, maxY),
                    point_box(next, minX, maxX, minY, maxY));
            VisionPose[] corners = {pose(minX, minY), pose(maxX, minY), pose(maxX, maxY), pose(minX, maxY)};
            for (int c = 0; c < 4; c++) {
                distance = Math.min(distance, point_segment(corners[c], previous, next));
                if (intersects(previous, next, corners[c], corners[(c + 1) % 4])) distance = 0;
            }
            require(distance + 1e-7 >= radius, "independent segment-to-body-box clearance is at least the robot radius");
            previous = next;
        }
    }
    private static double point_box(VisionPose p, double x0, double x1, double y0, double y1) {
        return Math.hypot(Math.max(0, Math.max(x0 - p.x, p.x - x1)), Math.max(0, Math.max(y0 - p.y, p.y - y1)));
    }
    private static double point_segment(VisionPose p, VisionPose a, VisionPose b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared));
        return Math.hypot(p.x - a.x - t * dx, p.y - a.y - t * dy);
    }
    private static double cross(VisionPose a, VisionPose b, VisionPose c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
    private static boolean intersects(VisionPose a, VisionPose b, VisionPose c, VisionPose d) {
        return cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0;
    }
}
