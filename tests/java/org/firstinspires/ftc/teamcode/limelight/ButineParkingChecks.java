package org.firstinspires.ftc.teamcode.limelight;

/** Hardware-free parking rules, rotated-footprint counterexamples and safe live-origin routes. */
public final class ButineParkingChecks {
    private static int checks;

    public static void main(String[] args) {
        nominal_zones_and_partial_defaults();
        physical_overlap_and_rotated_false_positives();
        configured_body_and_bad_defaults();
        live_origin_detours_and_rotation();
        recovery_from_authored_nominal_endpoints();
        recovery_never_deepens_or_adds_overlap();
        System.out.println("Butine parking checks passed: " + checks);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1e-7, message + ": " + actual);
    }

    private static VisionPose pose(double x, double y, double heading) {
        return new VisionPose(x, y, heading);
    }

    private static void nominal_zones_and_partial_defaults() {
        ButineField field = ButineField.biobuzz();
        ButineParking.Zone red = ButineParking.red_zone(), blue = ButineParking.blue_zone();
        near(11, red.maxX - red.minX, "zone depth includes tape");
        near(23, red.maxY - red.minY, "nominal width is 23, not a complete tile");
        near(96.5, red.minY, "Red A5 is not a field corner");
        near(144 - red.maxX, blue.minX, "Blue mirrors Red X bounds");
        near(144 - red.maxY, blue.minY, "Blue mirrors Red Y bounds");
        VisionPose target = ButineParking.default_target(red, field);
        require(target != null, "nominal Red body can park while retaining all-heading clearance");
        near(19, target.x, "default leaves exactly one inch of physical body overlap");
        near(108, target.y, "default uses center of Red A5");
        near(0, target.heading, "Red default is axis aligned");
        require(target.x > red.maxX, "center and majority of body may remain outside zone");
        require(ButineParking.is_parked(target, red, field, 1), "partial body overlap scores");
        require(!ButineParking.is_parked(target, red, field, 1.01), "operational overlap reserve is enforced");
        VisionPose other = ButineParking.default_target(blue, field);
        require(other != null, "nominal Blue default is valid");
        near(144 - target.x, other.x, "default Blue center mirrors Red");
        near(144 - target.y, other.y, "default Blue Y mirrors Red");
        near(Math.PI, other.heading, "default Blue heading rotates 180 degrees");
        require(ButineParking.is_parked(other, blue, field, 1), "Blue physical overlap passes");
        require(!ButineParking.is_parked(other, red, field, 0), "opposing loading zone does not score");
        require(!ButineParking.is_parked(pose(9, 108, 0), red, field, 1),
                "geometric overlap cannot bypass existing wall clearance");
    }

    private static void physical_overlap_and_rotated_false_positives() {
        ButineField field = new ButineField(144, 18, 18, 1);
        ButineParking.Zone red = ButineParking.red_zone();
        require(!ButineParking.is_parked(pose(20, 108, 0), red, field, 0), "exact tangency is rejected");
        require(ButineParking.is_parked(pose(19.9, 108, 0), red, field, 0), "small positive overlap is recognized");
        require(!ButineParking.is_parked(pose(19.9, 108, 0), red, field, 1), "small overlap is insufficient for reserve");
        // A 45-degree square's AABB spans (87.27..112.73)^2, but this small zone is
        // beyond its occupied top-right diamond edge. AABB/circle tests would accept it.
        ButineParking.Zone corner = new ButineParking.Zone(110, 112, 110, 112, true);
        VisionPose rotated = pose(100, 100, Math.PI / 4);
        require(!ButineParking.is_parked(rotated, corner, field, 0), "actual OBB rejects AABB corner false positive");
        ButineParking.Zone reached = new ButineParking.Zone(110, 112, 99, 101, true);
        require(ButineParking.is_parked(rotated, reached, field, 1), "rotated physical corner can provide positive overlap");
        require(!ButineParking.is_parked(pose(Double.NaN, 108, 0), red, field, 1), "invalid localization is not parking");
        require(!ButineParking.is_parked(rotated, reached, field, Double.NaN), "invalid overlap cannot pass");
    }

    private static void configured_body_and_bad_defaults() {
        ButineParking.Zone red = ButineParking.red_zone();
        ButineField longer = new ButineField(144, 20, 18, 1);
        VisionPose longerTarget = ButineParking.default_target(red, longer, 1);
        require(longerTarget != null, "configured longer body can park");
        near(20, longerTarget.x, "default is calculated from body length");
        require(ButineParking.is_parked(longerTarget, red, longer, 1), "longer body overlap is validated");
        ButineField blocked = new ButineField(144, 18, 18, 1,
                new ButineField.Box(15, 21, 104, 112));
        require(ButineParking.default_target(red, blocked) == null, "blocked default does not invent an unchecked alternative");
        ButineField excessiveReserve = new ButineField(144, 18, 18, 8);
        require(ButineParking.default_target(red, excessiveReserve) == null, "impossible clearance/overlap combination fails");
        require(ButineParking.default_target(red, ButineField.unrestricted()) == null,
                "free-plane model cannot establish a physical parking footprint");
        require(ButineParking.default_target(red, longer, -1) == null, "negative reserve rejected");
        boolean rejected = false;
        try { new ButineParking.Zone(2, 1, 3, 4, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "invalid surveyed bounds rejected");
    }

    private static void live_origin_detours_and_rotation() {
        Butine.Params params = new Butine.Params();
        params.field = ButineField.biobuzz();
        VisionPose target = ButineParking.default_target(ButineParking.red_zone(), params.field);
        VisionPose here = pose(72, 30, 0.7);
        ButineIntake.Route route = ButineParking.route(here, target, params);
        require(route != null && route.timeS > 0, "live pose has a timed parking route");
        require(route.waypoint_count() > 2, "frame-blocked straight line uses a detour");
        near(here.x, route.waypoint(0).x, "parking begins at live X, not a depot");
        near(here.y, route.waypoint(0).y, "parking begins at live Y");
        near(target.x, route.waypoint(route.waypoint_count() - 1).x, "parking reaches selected X");
        near(target.y, route.waypoint(route.waypoint_count() - 1).y, "parking reaches selected Y");
        for (int i = 0; i < route.segment_count(); i++) {
            require(!route.intake_active(i), "parking never activates intake");
            require(params.field.segment_clear(route.waypoint(i), route.waypoint(i + 1)), "each detour leg is collision checked");
        }
        ButineIntake.Route turn = ButineParking.route(pose(target.x, target.y, Math.PI), target, params);
        require(turn != null, "already overlapping pose can rotate safely within all-heading envelope");
        near(Math.PI / params.kinematics.omegaMaxRadS + params.kinematics.turnSettleS,
                turn.timeS, "zero-distance final rotation consumes predicted time");
        params.field = new ButineField(144, 18, 18, 1, new ButineField.Box(60, 80, 0, 144));
        require(ButineParking.route(pose(110, 30, 0), target, params) == null, "unreachable parking never shortcuts spanning wall");
        require(ButineParking.route(pose(5, 30, 0), target, params) == null, "invalid live origin never dispatches");
        params.field = ButineField.biobuzz();
        params.kinematics.omegaMaxRadS = 0;
        require(ButineParking.route(pose(target.x, target.y, Math.PI), target, params) == null,
                "zero turn capability cannot claim a required rotation");
        params.kinematics.cruiseSpeedInS = Double.NaN;
        require(ButineParking.route(here, target, params) == null, "invalid kinematics cannot produce a route");
    }

    private static void recovery_from_authored_nominal_endpoints() {
        Butine.Params params = new Butine.Params();
        VisionPose[] red = {pose(59.25, 9, Math.PI / 2), pose(59.25, 135, -Math.PI / 2),
                pose(8, 47, Math.PI), pose(8, 8, 0)};
        for (VisionPose authored : red) for (boolean blue : new boolean[]{false, true}) {
            VisionPose from = blue ? pose(144 - authored.x, 144 - authored.y, authored.heading + Math.PI) : authored;
            VisionPose target = ButineParking.default_target(blue ? ButineParking.blue_zone() : ButineParking.red_zone(),
                    params.field);
            require(ButineParking.route(from, target, params) == null,
                    "normal free-floor routing remains strict at an authored near-wall pose");
            ButineParking.Approach approach = ButineParking.approach(from, target, params);
            require(approach != null && approach.egress != null, "authored endpoint has an explicit checked recovery");
            require(approach.egress.from == from, "recovery records its actual supplied origin");
            near(0, VisionPose.wrap_pi(approach.egress.target.heading - from.heading),
                    "recovery never rotates outside the all-heading region");
            require(params.field.is_clear(approach.egress.target), "recovery ends in normal all-heading space");
            require(ButineParking.egress_clear(from, approach.egress.target, params.field),
                    "entire fixed-heading recovery is revalidatable before dispatch");
            near(approach.egress.target.x, approach.route.waypoint(0).x, "normal route begins after recovery X");
            near(approach.egress.target.y, approach.route.waypoint(0).y, "normal route begins after recovery Y");
            near(approach.egress.timeS + approach.route.timeS, approach.timeS,
                    "deadline estimate includes recovery braking and normal travel");
            for (int i = 0; i < approach.route.segment_count(); i++) {
                require(!approach.route.intake_active(i), "post-recovery parking keeps intake off");
                require(params.field.segment_clear(approach.route.waypoint(i), approach.route.waypoint(i + 1)),
                        "every post-recovery segment uses the original conservative field");
            }
        }
        VisionPose safe = pose(72, 30, 0);
        ButineParking.Approach normal = ButineParking.approach(safe,
                ButineParking.default_target(ButineParking.red_zone(), params.field), params);
        require(normal != null && normal.egress == null, "normal clear starts add no recovery phase");
        near(normal.route.timeS, normal.timeS, "normal approach has no invented recovery cost");
    }

    private static void recovery_never_deepens_or_adds_overlap() {
        ButineField field = new ButineField(144, 18, 18, 1);
        require(ButineParking.egress_clear(pose(59.25, 9, Math.PI / 2), pose(59.25, 15, Math.PI / 2), field),
                "wall-touching body can translate inward with fixed heading");
        require(!ButineParking.egress_clear(pose(59.25, 9, Math.PI / 2), pose(59.25, 15, 0), field),
                "recovery cannot rotate before reaching conservative space");
        require(!ButineParking.egress_clear(pose(59.25, 9, Math.PI / 2), pose(1, 15, Math.PI / 2), field),
                "recovery cannot create another wall deficit");
        ButineField newObstacle = new ButineField(144, 18, 18, 1, new ButineField.Box(55, 65, 22, 25));
        require(!ButineParking.egress_clear(pose(59.25, 9, 0), pose(59.25, 40, 0), newObstacle),
                "new obstacle overlap in the middle of the segment is rejected");
        ButineField deep = new ButineField(144, 18, 18, 1, new ButineField.Box(0, 25, 0, 25));
        require(!ButineParking.egress_clear(pose(8, 8, 0), pose(45, 45, 0), deep),
                "contained initial overlap cannot deepen by crossing the obstacle center");
        Butine.Params trapped = new Butine.Params();
        trapped.field = deep;
        VisionPose target = pose(19, 108, 0);
        require(ButineParking.approach(pose(8, 8, 0), target, trapped) == null,
                "trapped nominal start never receives a direct escape shortcut");
        require(ButineParking.approach(pose(-1, 8, 0), target, trapped) == null,
                "localization center outside the field is not an authored recovery");
        Butine.Params invalid = new Butine.Params();
        invalid.kinematics.cruiseSpeedInS = Double.NaN;
        require(ButineParking.approach(pose(59.25, 9, Math.PI / 2), target, invalid) == null,
                "recovery rejects invalid timing rather than inventing a finite estimate");
    }
}
