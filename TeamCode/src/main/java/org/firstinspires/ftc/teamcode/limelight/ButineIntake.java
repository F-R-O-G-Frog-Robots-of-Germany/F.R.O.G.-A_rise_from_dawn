package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Pure, constant-heading front-mouth contact geometry and continuous-run estimates. */
public final class ButineIntake {
    private static final double EPSILON = 1e-9;
    private static final double OVERSHOOT_EPSILON_IN = 1e-7;
    private ButineIntake() { }

    /** Immutable actual center path; pickup indices refer to its zero-based segments. */
    public static final class Route {
        private final VisionPose[] waypoints;
        private final boolean[] active;
        private final double[] times, speeds;
        private final int[] pickupSegments;
        private final double[] pickupFractions;
        public final double collectionHeading, intakeWidthIn, intakeForwardOffsetIn, intakeOvershootIn, pollenRadiusIn, timeS;

        private Route(List<VisionPose> points, boolean[] active, List<Contact> contacts,
                      Butine.Params params, double heading, VisionPose depot) {
            waypoints = points.toArray(new VisionPose[0]);
            this.active = active.clone();
            collectionHeading = heading;
            intakeWidthIn = params.intakeWidthIn;
            intakeForwardOffsetIn = params.intakeForwardOffsetIn;
            intakeOvershootIn = params.intakeOvershootIn;
            pollenRadiusIn = params.pollenRadiusIn;
            pickupSegments = new int[contacts.size()];
            pickupFractions = new double[contacts.size()];
            for (int i = 0; i < contacts.size(); i++) {
                pickupSegments[i] = contacts.get(i).segment;
                pickupFractions[i] = contacts.get(i).fraction;
            }
            Motion motion = motion(points, active, params.kinematics, heading, depot.heading);
            times = motion.times;
            speeds = motion.speeds;
            double total = 0;
            for (double time : times) total += time;
            if (!Double.isFinite(total)) throw new IllegalArgumentException("Continuous route time exceeds numeric range");
            timeS = total;
        }

        public int waypoint_count() { return waypoints.length; }
        public VisionPose waypoint(int index) { return waypoints[index]; }
        public int segment_count() { return active.length; }
        public boolean intake_active(int segment) { return active[segment]; }
        public double segment_time_s(int segment) { return times[segment]; }
        public double segment_start_speed_in_s(int segment) { return speeds[segment]; }
        public double segment_end_speed_in_s(int segment) { return speeds[segment + 1]; }
        public int pickup_segment(int pickup) { return pickupSegments[pickup]; }
        public double pickup_fraction(int pickup) { return pickupFractions[pickup]; }
        public int pickup_count() { return pickupSegments.length; }
    }

    static final class Candidate {
        final int[] ids;
        final VisionPose[] poses;
        final Route route;
        Candidate(Route route, List<Contact> contacts) {
            this.route = route;
            ids = new int[contacts.size()];
            poses = new VisionPose[contacts.size()];
            for (int i = 0; i < contacts.size(); i++) {
                ids[i] = contacts.get(i).pollen.id;
                poses[i] = contacts.get(i).pollen.position;
            }
        }
    }

    private static final class Contact {
        final Pollen pollen;
        final int segment;
        final double fraction;
        Contact(Pollen pollen, int segment, double fraction) {
            this.pollen = pollen; this.segment = segment; this.fraction = fraction;
        }
    }

    /** Earliest translation fraction where the pollen disk touches the finite FRONT mouth; NaN means no contact. */
    public static double contact_fraction(VisionPose from, VisionPose to, VisionPose pollen,
                                           double heading, double width, double offset, double radius) {
        if (from == null || to == null || pollen == null || !from.is_finite() || !to.is_finite()
                || !pollen.is_finite() || !Double.isFinite(heading)
                || !Double.isFinite(width) || width <= 0 || !Double.isFinite(offset) || offset < 0
                || !Double.isFinite(radius) || radius <= 0)
            throw new IllegalArgumentException("Finite poses, heading and positive mouth width/pollen radius required");
        double c = Math.cos(heading), s = Math.sin(heading);
        double px = pollen.x - from.x, py = pollen.y - from.y;
        double dx = to.x - from.x, dy = to.y - from.y;
        double u = px * c + py * s - offset, v = -px * s + py * c;
        double du = -(dx * c + dy * s), dv = -(-dx * s + dy * c);
        if (!Double.isFinite(u) || !Double.isFinite(v) || !Double.isFinite(du) || !Double.isFinite(dv))
            throw new IllegalArgumentException("Contact coordinates exceed numeric range");
        // A capsule around the lateral mouth segment is a rectangle plus its two circular end caps.
        double first = rectangle_entry(u, v, du, dv, radius, width / 2);
        first = earlier(first, circle_entry(u, v - width / 2, du, dv, radius));
        return earlier(first, circle_entry(u, v + width / 2, du, dv, radius));
    }

    private static double earlier(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.min(a, b);
    }

    private static double rectangle_entry(double x, double y, double dx, double dy, double rx, double ry) {
        double enter = 0, exit = 1;
        if (Math.abs(dx) < EPSILON) {
            if (Math.abs(x) > rx + EPSILON) return Double.NaN;
        } else {
            double a = (-rx - x) / dx, b = (rx - x) / dx;
            enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        if (Math.abs(dy) < EPSILON) {
            if (Math.abs(y) > ry + EPSILON) return Double.NaN;
        } else {
            double a = (-ry - y) / dy, b = (ry - y) / dy;
            enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        return enter <= exit + EPSILON ? Math.max(0, Math.min(1, enter)) : Double.NaN;
    }

    private static double circle_entry(double x, double y, double dx, double dy, double radius) {
        double c = x * x + y * y - radius * radius;
        if (c <= EPSILON) return 0;
        double a = dx * dx + dy * dy;
        if (a <= EPSILON * EPSILON) return Double.NaN;
        double b = x * dx + y * dy;
        double discriminant = b * b - a * c;
        if (discriminant < -EPSILON) return Double.NaN;
        double root = (-b - Math.sqrt(Math.max(0, discriminant))) / a;
        return root >= -EPSILON && root <= 1 + EPSILON ? Math.max(0, Math.min(1, root)) : Double.NaN;
    }

    static Candidate plan_run(List<Pollen> all, VisionPose start, VisionPose depot,
                               Butine.Params params, double budgetS) {
        Candidate best = plan_heading(all, start, depot, params, budgetS);
        if (start.distance_to(depot) > EPSILON) return best;
        for (double heading : new double[]{0, Math.PI / 2, Math.PI, -Math.PI / 2}) {
            double difference = Math.abs(Butine.wrap_pi(Butine.wrap_pi(heading) - Butine.wrap_pi(start.heading)));
            if (difference <= EPSILON || params.kinematics.omegaMaxRadS <= EPSILON) continue;
            double alignmentTime = difference > params.kinematics.turnDeadbandRad
                    ? difference / params.kinematics.omegaMaxRadS + params.kinematics.turnSettleS : 0;
            Candidate candidate = plan_heading(all, new VisionPose(start.x, start.y, heading), depot, params, budgetS - alignmentTime);
            if (candidate == null) continue;
            ArrayList<VisionPose> points = new ArrayList<>();
            points.add(start);
            for (int i = 0; i < candidate.route.waypoint_count(); i++) points.add(candidate.route.waypoint(i));
            boolean[] active = new boolean[candidate.route.segment_count() + 1];
            for (int i = 1; i < active.length; i++) active[i] = candidate.route.intake_active(i - 1);
            ArrayList<Contact> contacts = new ArrayList<>();
            for (int i = 0; i < candidate.ids.length; i++) contacts.add(new Contact(
                    new Pollen(candidate.ids[i], candidate.poses[i], 1, 1, 0),
                    candidate.route.pickup_segment(i) + 1, candidate.route.pickup_fraction(i)));
            candidate = new Candidate(new Route(points, active, contacts, params, heading, depot), contacts);
            if (candidate.route.timeS + Math.max(0, params.scoreOverheadS) > budgetS + EPSILON) continue;
            if (best == null || candidate.ids.length > best.ids.length
                    || (candidate.ids.length == best.ids.length && candidate.route.timeS < best.route.timeS - EPSILON)) best = candidate;
        }
        return best;
    }

    private static Candidate plan_heading(List<Pollen> all, VisionPose start, VisionPose depot,
                                           Butine.Params params, double budgetS) {
        ArrayList<VisionPose> outbound = new ArrayList<>();
        outbound.add(new VisionPose(start.x, start.y, start.heading));
        Candidate best = null;
        HashSet<Integer> selected = new HashSet<>();
        int capacity = Math.min(Butine.POSSESSION_LIMIT, Math.max(1, params.capacity));
        int cap = Math.max(1, Math.min(Butine.MAX_CANDIDATES, params.candidateCap));
        double c = Math.cos(start.heading), s = Math.sin(start.heading);
        for (int extension = 0; extension < capacity; extension++) {
            VisionPose current = outbound.get(outbound.size() - 1);
            ArrayList<Pollen> seeds = new ArrayList<>();
            for (Pollen pollen : all) {
                if (selected.contains(pollen.id) || pollen.confidence < params.minConfidence) continue;
                VisionPose anchor = new VisionPose(pollen.position.x - params.intakeForwardOffsetIn * c,
                        pollen.position.y - params.intakeForwardOffsetIn * s, start.heading);
                if (params.field.is_clear(anchor)) seeds.add(pollen);
            }
            seeds.sort(Comparator.comparingDouble((Pollen pollen) -> Math.hypot(
                    pollen.position.x - params.intakeForwardOffsetIn * c - current.x,
                    pollen.position.y - params.intakeForwardOffsetIn * s - current.y)).thenComparingInt(p -> p.id));
            Candidate chosen = null;
            ArrayList<VisionPose> chosenOutbound = null;
            int feasibleSeeds = 0;
            for (int seedIndex = 0; seedIndex < seeds.size() && feasibleSeeds < cap; seedIndex++) {
                Pollen seed = seeds.get(seedIndex);
                VisionPose anchor = new VisionPose(seed.position.x - params.intakeForwardOffsetIn * c,
                        seed.position.y - params.intakeForwardOffsetIn * s, start.heading);
                ButineField.Route extensionRoute = params.field.route(current, anchor);
                if (extensionRoute == null) continue;
                ArrayList<VisionPose> trial = new ArrayList<>(outbound);
                for (int point = 0; point < extensionRoute.point_count(); point++) {
                    VisionPose waypoint = extensionRoute.point(point);
                    trial.add(new VisionPose(waypoint.x, waypoint.y, start.heading));
                }
                List<Contact> contacts = contacts(trial, all, params, start.heading);
                // Reject the entire pass, including simultaneous fifth contacts; never silently discard extras.
                if (contacts.size() > capacity || contacts.size() <= selected.size()) continue;
                ExtendedPass extended = overshoot_pass(trial, contacts, all, params, start.heading, capacity, selected.size());
                if (extended == null) continue;
                ArrayList<VisionPose> shortened = extended.points;
                contacts = extended.contacts;
                Route route = finish(shortened, contacts, depot, params, start.heading);
                if (route == null || route.timeS + Math.max(0, params.scoreOverheadS) > budgetS + EPSILON) continue;
                double totalDistance = 0;
                for (int i = 0; i < route.segment_count(); i++) totalDistance += route.waypoint(i).distance_to(route.waypoint(i + 1));
                if (totalDistance <= EPSILON) continue;
                feasibleSeeds++;
                Candidate candidate = new Candidate(route, contacts);
                if (chosen == null || candidate.ids.length > chosen.ids.length
                        || (candidate.ids.length == chosen.ids.length && route.timeS < chosen.route.timeS - EPSILON)) {
                    chosen = candidate;
                    chosenOutbound = shortened;
                }
            }
            if (chosen == null) break;
            best = chosen;
            outbound = chosenOutbound;
            selected.clear();
            for (int id : best.ids) selected.add(id);
            if (selected.size() == capacity) break;
        }
        return best;
    }

    private static final class ExtendedPass {
        final ArrayList<VisionPose> points;
        final List<Contact> contacts;
        ExtendedPass(ArrayList<VisionPose> points, List<Contact> contacts) {
            this.points = points; this.contacts = contacts;
        }
    }

    /** Extend on one fixed incoming ray until the last incidental contact also receives the full pull-through. */
    private static ExtendedPass overshoot_pass(List<VisionPose> trial, List<Contact> initialContacts,
                                                List<Pollen> all, Butine.Params params, double heading,
                                                int capacity, int selectedCount) {
        Contact last = initialContacts.get(initialContacts.size() - 1);
        VisionPose origin = trial.get(last.segment), target = trial.get(last.segment + 1);
        double length = origin.distance_to(target);
        double dx = target.x - origin.x, dy = target.y - origin.y;
        if (length <= EPSILON) {
            for (int segment = last.segment - 1; segment >= 0 && length <= EPSILON; segment--) {
                VisionPose before = trial.get(segment), after = trial.get(segment + 1);
                length = before.distance_to(after);
                dx = after.x - before.x;
                dy = after.y - before.y;
            }
            if (length <= EPSILON) return null;
        }
        dx /= length;
        dy /= length;
        double contactDistance = origin.distance_to(target) * last.fraction;
        double endDistance = contactDistance + params.intakeOvershootIn;
        ArrayList<VisionPose> prefix = new ArrayList<>(trial.subList(0, last.segment + 1));
        for (int iteration = 0; iteration <= capacity; iteration++) {
            VisionPose endpoint = new VisionPose(origin.x + dx * endDistance, origin.y + dy * endDistance, heading);
            if (!params.field.segment_clear(origin, endpoint)) return null;
            ArrayList<VisionPose> points = new ArrayList<>(prefix);
            points.add(endpoint);
            List<Contact> found = contacts(points, all, params, heading);
            if (found.size() > capacity || found.size() <= selectedCount) return null;
            Contact newest = found.get(found.size() - 1);
            VisionPose a = points.get(newest.segment), b = points.get(newest.segment + 1);
            double contactX = a.x + (b.x - a.x) * newest.fraction;
            double contactY = a.y + (b.y - a.y) * newest.fraction;
            double latestDistance = (contactX - origin.x) * dx + (contactY - origin.y) * dy;
            double requiredDistance = latestDistance + params.intakeOvershootIn;
            if (!Double.isFinite(requiredDistance) || latestDistance < -OVERSHOOT_EPSILON_IN) return null;
            if (Math.abs(requiredDistance - endDistance) <= OVERSHOOT_EPSILON_IN)
                return new ExtendedPass(points, found);
            if (requiredDistance < endDistance) return null;
            endDistance = requiredDistance;
        }
        return null;
    }

    private static List<Contact> contacts(List<VisionPose> points, List<Pollen> all,
                                           Butine.Params params, double heading) {
        ArrayList<Contact> found = new ArrayList<>();
        for (Pollen pollen : all) {
            for (int segment = 0; segment + 1 < points.size(); segment++) {
                double fraction = contact_fraction(points.get(segment), points.get(segment + 1), pollen.position,
                        heading, params.intakeWidthIn, params.intakeForwardOffsetIn, params.pollenRadiusIn);
                if (!Double.isNaN(fraction)) {
                    found.add(new Contact(pollen, segment, fraction));
                    break;
                }
            }
        }
        found.sort(Comparator.comparingInt((Contact contact) -> contact.segment)
                .thenComparingDouble(contact -> contact.fraction).thenComparingInt(contact -> contact.pollen.id));
        return found;
    }

    private static Route finish(List<VisionPose> outbound, List<Contact> contacts, VisionPose depot,
                                 Butine.Params params, double heading) {
        VisionPose last = outbound.get(outbound.size() - 1);
        ButineField.Route home = params.field.route(last, depot);
        if (home == null) return null;
        ArrayList<VisionPose> points = new ArrayList<>(outbound);
        double angle = Butine.wrap_pi(Butine.wrap_pi(depot.heading) - Butine.wrap_pi(heading));
        double distance = 0;
        VisionPose previous = last;
        for (int i = 0; i < home.point_count(); i++) {
            VisionPose point = home.point(i);
            distance += previous.distance_to(point);
            double t = home.distanceIn <= EPSILON ? 1 : distance / home.distanceIn;
            points.add(new VisionPose(point.x, point.y, heading + angle * t));
            previous = point;
        }
        boolean[] active = new boolean[points.size() - 1];
        Arrays.fill(active, 0, outbound.size() - 1, true);
        for (int i = 0; i < active.length; i++)
            if (!params.field.segment_clear(points.get(i), points.get(i + 1))) return null;
        return new Route(points, active, contacts, params, heading, depot);
    }

    /** Safe intake-off recovery path using the same continuous timing model. */
    public static Route return_route(VisionPose start, VisionPose depot, Butine.Params params) {
        ArrayList<VisionPose> outbound = new ArrayList<>();
        outbound.add(start);
        return finish(outbound, new ArrayList<Contact>(), depot, params, start.heading);
    }

    private static final class Motion {
        final double[] times, speeds;
        Motion(double[] times, double[] speeds) { this.times = times; this.speeds = speeds; }
    }

    private static Motion motion(List<VisionPose> points, boolean[] active, Butine.Kinematics k,
                                   double collectionHeading, double depotHeading) {
        int count = active.length;
        double[] length = new double[count], cap = new double[count], speed = new double[count + 1];
        Arrays.fill(cap, k.cruiseSpeedInS);
        double returnLength = 0;
        for (int i = 0; i < count; i++) {
            length[i] = points.get(i).distance_to(points.get(i + 1));
            if (!active[i]) returnLength += length[i];
        }
        double angle = Math.abs(Butine.wrap_pi(Butine.wrap_pi(depotHeading) - Butine.wrap_pi(collectionHeading)));
        if (angle > k.turnDeadbandRad && k.omegaMaxRadS > EPSILON && returnLength > EPSILON) {
            double rotationalCap = returnLength * k.omegaMaxRadS / angle;
            for (int i = 0; i < count; i++) if (!active[i]) cap[i] = Math.min(cap[i], rotationalCap);
        }
        Arrays.fill(speed, k.cruiseSpeedInS);
        speed[0] = speed[count] = 0;
        for (int i = 1; i < count; i++) {
            speed[i] = Math.min(cap[i - 1], cap[i]);
            int before = i - 1, after = i;
            while (before >= 0 && length[before] <= EPSILON) before--;
            while (after < count && length[after] <= EPSILON) after++;
            if (before < 0 || after == count) continue;
            VisionPose a = points.get(before), b = points.get(before + 1);
            VisionPose c = points.get(after), d = points.get(after + 1);
            double cosine = ((b.x - a.x) * (d.x - c.x) + (b.y - a.y) * (d.y - c.y))
                    / (length[before] * length[after]);
            double halfCosine = Math.sqrt(Math.max(0, (1 + Math.max(-1, Math.min(1, cosine))) / 2));
            if (halfCosine < 1 - EPSILON) {
                double junction = Math.sqrt(Math.min(k.accelInS2, k.decelInS2) * k.junctionDeviationIn
                        * halfCosine / Math.max(EPSILON, 1 - halfCosine));
                speed[i] = Math.min(speed[i], junction);
            }
        }
        for (int i = 0; i < count; i++)
            speed[i + 1] = Math.min(speed[i + 1], Math.sqrt(speed[i] * speed[i] + 2 * k.accelInS2 * length[i]));
        for (int i = count - 1; i >= 0; i--)
            speed[i] = Math.min(speed[i], Math.sqrt(speed[i + 1] * speed[i + 1] + 2 * k.decelInS2 * length[i]));
        double[] times = new double[count];
        for (int i = 0; i < count; i++) times[i] = interval_time(length[i], speed[i], speed[i + 1], cap[i], k);
        if (count > 0 && angle > k.turnDeadbandRad && k.omegaMaxRadS > EPSILON) {
            double returnTime = 0;
            for (int i = 0; i < count; i++) if (!active[i]) returnTime += times[i];
            times[count - 1] += Math.max(0, angle / k.omegaMaxRadS - returnTime) + k.turnSettleS;
        }
        if (count > 0 && !active[0] && length[0] <= EPSILON && k.omegaMaxRadS > EPSILON) {
            double initialAngle = Math.abs(Butine.wrap_pi(Butine.wrap_pi(collectionHeading)
                    - Butine.wrap_pi(points.get(0).heading)));
            if (initialAngle > k.turnDeadbandRad) times[0] += initialAngle / k.omegaMaxRadS + k.turnSettleS;
        }
        return new Motion(times, speed);
    }

    private static double interval_time(double length, double fromSpeed, double toSpeed, double limit,
                                         Butine.Kinematics k) {
        if (length <= EPSILON) return 0;
        double peakSquared = (2 * k.accelInS2 * k.decelInS2 * length
                + k.decelInS2 * fromSpeed * fromSpeed + k.accelInS2 * toSpeed * toSpeed)
                / (k.accelInS2 + k.decelInS2);
        double peak = Math.min(limit, Math.sqrt(Math.max(0, peakSquared)));
        double accelerate = Math.max(0, (peak - fromSpeed) / k.accelInS2);
        double decelerate = Math.max(0, (peak - toSpeed) / k.decelInS2);
        double ramps = Math.max(0, (peak * peak - fromSpeed * fromSpeed) / (2 * k.accelInS2))
                + Math.max(0, (peak * peak - toSpeed * toSpeed) / (2 * k.decelInS2));
        return accelerate + decelerate + Math.max(0, length - ramps) / peak;
    }
}
