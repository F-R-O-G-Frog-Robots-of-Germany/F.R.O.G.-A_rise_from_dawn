package org.firstinspires.ftc.teamcode.tools;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineField;
import org.firstinspires.ftc.teamcode.limelight.ButineIntake;
import org.firstinspires.ftc.teamcode.limelight.Pollen;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/** Desktop bridge: production Butine solves and leg expansion, without motors, workers or SDK initialization. */
public final class ButineVisualizerBridge {
    private static final int CAPACITY = 4;
    private static final int MAX_RED_POLLEN = 20;
    private static final double RED_MAX_X_IN = 72.0;
    private static final double TIME_EPSILON_S = 1e-7;

    private ButineVisualizerBridge() { }

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Expected one TSV input path or --describe-field");
            if ("--describe-field".equals(args[0])) {
                System.out.println(describe_field());
                return;
            }
            Input input = read_input(Paths.get(args[0]));
            System.out.println(simulate(input));
        } catch (Exception failure) {
            System.err.println("ButineVisualizerBridge: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static final class Input {
        VisionPose start, depot;
        double remainingS, scoreOverheadS;
        double intakeWidthIn = 18, intakeForwardOffsetIn = 9;
        double intakeOvershootIn = new Butine.Params().intakeOvershootIn;
        int seed;
        final List<Pollen> pollen = new ArrayList<>();
    }

    private static final class ExecutedRun {
        int id, candidatesConsidered;
        VisionPose start;
        Butine.Run run;
        List<Leg> legs;
        double remainingBeforeS, remainingAfterS, committedTimeS, cumulativeTimeS, solveUs;
    }

    private enum LegKind { ALIGNMENT, TRANSIT, PICKUP, RETURN, DEPOT }

    /** Serialization-only record; geometry and motion costs come from production classes. */
    private static final class Leg {
        final VisionPose from, target;
        final int[] pollenIds;
        final double[] contactFractions;
        final boolean intakeOn;
        final double predictedS, swingDeg, startSpeedInS, endSpeedInS;
        final LegKind kind;

        Leg(VisionPose from, VisionPose target, int[] pollenIds, double[] contactFractions,
            boolean intakeOn, double predictedS, double startSpeedInS, double endSpeedInS, LegKind kind) {
            this.from = from;
            this.target = target;
            this.pollenIds = pollenIds;
            this.contactFractions = contactFractions;
            this.intakeOn = intakeOn;
            this.predictedS = predictedS;
            this.swingDeg = Math.toDegrees(Math.abs(VisionPose.wrap_pi(target.heading - from.heading)));
            this.startSpeedInS = startSpeedInS;
            this.endSpeedInS = endSpeedInS;
            this.kind = kind;
        }
    }

    private static Input read_input(Path path) throws Exception {
        Input input = new Input();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null) throw new IllegalArgumentException("Input is empty");
            if (first.startsWith("\ufeff")) first = first.substring(1);
            String[] config = first.split("\t", -1);
            if ((config.length != 10 && config.length != 12 && config.length != 13) || !"CONFIG".equals(config[0])) {
                throw new IllegalArgumentException("First line must be CONFIG followed by nine values, optionally intake width/forward offset and overshoot");
            }
            input.start = new VisionPose(number(config[1], "start X"), number(config[2], "start Y"),
                    number(config[3], "start heading"));
            input.depot = new VisionPose(number(config[4], "depot X"), number(config[5], "depot Y"),
                    number(config[6], "depot heading"));
            input.remainingS = number(config[7], "remaining seconds");
            input.scoreOverheadS = number(config[8], "scoring overhead seconds");
            if (input.remainingS < 0 || input.scoreOverheadS < 0)
                throw new IllegalArgumentException("Time budget and scoring overhead must be nonnegative");
            input.seed = Integer.parseInt(config[9]);
            if (config.length >= 12) {
                input.intakeWidthIn = number(config[10], "intake width");
                input.intakeForwardOffsetIn = number(config[11], "intake forward offset");
            }
            if (config.length == 13) input.intakeOvershootIn = number(config[12], "intake overshoot");
            if (input.intakeWidthIn <= 0 || input.intakeForwardOffsetIn < 0 || input.intakeOvershootIn < 0)
                throw new IllegalArgumentException("Intake width must be positive; forward offset and overshoot must be nonnegative");
            Set<Integer> ids = new HashSet<>();
            int lineNumber = 1;
            for (String line; (line = reader.readLine()) != null; ) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                String[] columns = line.split("\t", -1);
                if (columns.length != 4 || !"POLLEN".equals(columns[0]))
                    throw new IllegalArgumentException("Line " + lineNumber + " must be POLLEN, ID, X and Y separated by tabs");
                int id = Integer.parseInt(columns[1]);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate pollen ID " + id);
                double x = number(columns[2], "pollen X on line " + lineNumber);
                double y = number(columns[3], "pollen Y on line " + lineNumber);
                if (!(x < RED_MAX_X_IN)) {
                    throw new IllegalArgumentException("Pollen " + id + " is not on RED's audience-view left half (x < 72)");
                }
                input.pollen.add(new Pollen(id, new VisionPose(x, y, 0), 1, 1, 0));
                if (input.pollen.size() > MAX_RED_POLLEN)
                    throw new IllegalArgumentException("At most 20 RED pollen may be submitted");
            }
        }
        return input;
    }

    private static String simulate(Input input) {
        Butine.Params defaults = new Butine.Params();
        if (defaults.capacity != CAPACITY) {
            throw new IllegalStateException("Production Butine.Params.capacity must be 4; found " + defaults.capacity);
        }
        ButineField field = ButineField.biobuzz();
        if (defaults.field != field) throw new IllegalStateException("Production default field is not nominal BIOBUZZ");
        if (!field.is_clear(input.start) || !field.is_clear(input.depot))
            throw new IllegalArgumentException("Start or depot violates the production robot-envelope geometry");
        if (field.route(input.start, input.depot) == null)
            throw new IllegalArgumentException("Start has no safe route to the depot");
        ArrayList<Pollen> remainingPollen = new ArrayList<>(input.pollen);
        ArrayList<ExecutedRun> executed = new ArrayList<>();
        VisionPose start = input.start;
        double remainingS = input.remainingS, committedS = 0, totalSolveUs = 0;
        int solveCount = 0, collected = 0;
        String stopReason = "all eligible pollen collected";
        while (!remainingPollen.isEmpty()) {
            if (remainingS <= 0) { stopReason = "remaining time exhausted"; break; }
            if (executed.size() >= MAX_RED_POLLEN) throw new IllegalStateException("Receding-horizon iteration bound exceeded");
            Butine.Params params = new Butine.Params(defaults);
            params.remainingS = remainingS;
            params.scoreOverheadS = input.scoreOverheadS;
            params.seed = input.seed;
            params.intakeWidthIn = input.intakeWidthIn;
            params.intakeForwardOffsetIn = input.intakeForwardOffsetIn;
            params.intakeOvershootIn = input.intakeOvershootIn;
            params.sweepIntake = true;
            Butine.Plan plan = Butine.solve(remainingPollen, start, input.depot, params);
            solveCount++;
            totalSolveUs += plan.solveUs;
            if (plan.field != field) throw new IllegalStateException("Planner changed the immutable field snapshot");
            if (plan.fittingRuns == 0 || plan.runCount == 0 || !plan.runs[0].fits || plan.runs[0].count == 0) {
                stopReason = plan.rejectionReason == null || plan.rejectionReason.isEmpty()
                        ? "no fitting collection run" : plan.rejectionReason;
                break;
            }
            Butine.Run run = plan.runs[0];
            if (run.count > CAPACITY) throw new IllegalStateException("Planner exceeded the four-pollen run capacity");
            List<Leg> legs = expand_run(run, start, input.depot, field);
            double emittedTime = validate_run(remainingPollen, run, legs, start, input.depot, field);
            double committed = emittedTime + input.scoreOverheadS;
            if (committed > remainingS + TIME_EPSILON_S)
                throw new IllegalStateException("Emitted run plus scoring no longer fits the remaining budget");
            HashSet<Integer> picked = new HashSet<>();
            for (int id : run.ids) if (!picked.add(id)) throw new IllegalStateException("Repeated pickup ID " + id);
            int before = remainingPollen.size();
            remainingPollen.removeIf(point -> picked.contains(point.id));
            if (before - remainingPollen.size() != run.count)
                throw new IllegalStateException("Selected pickup IDs did not match the remaining map exactly");
            collected += run.count;
            committedS += committed;
            ExecutedRun execution = new ExecutedRun();
            execution.id = executed.size() + 1;
            execution.start = start;
            execution.run = run;
            execution.legs = legs;
            execution.remainingBeforeS = remainingS;
            remainingS = Math.max(0, input.remainingS - committedS);
            execution.remainingAfterS = remainingS;
            execution.committedTimeS = committed;
            execution.cumulativeTimeS = committedS;
            execution.solveUs = plan.solveUs;
            execution.candidatesConsidered = plan.candidatesConsidered;
            executed.add(execution);
            start = input.depot;
        }
        return output(input, defaults, field, executed, remainingPollen, solveCount, collected,
                committedS, remainingS, totalSolveUs, stopReason);
    }

    private static List<Leg> expand_run(Butine.Run run, VisionPose start, VisionPose depot,
                                       ButineField field) {
        ButineIntake.Route route = run.route;
        if (route == null || route.segment_count() < 1
                || route.waypoint(0).distance_to(start) > 1e-9
                || route.waypoint(route.waypoint_count() - 1).distance_to(depot) > 1e-9)
            throw new IllegalStateException("Production sweep route is missing or has incorrect endpoints");
        ArrayList<Leg> legs = new ArrayList<>();
        boolean collectionStarted = false;
        for (int segment = 0; segment < route.segment_count(); segment++) {
            int count = 0;
            for (int pickup = 0; pickup < run.count; pickup++)
                if (route.pickup_segment(pickup) == segment) count++;
            int[] ids = new int[count];
            double[] fractions = new double[count];
            int cursor = 0;
            for (int pickup = 0; pickup < run.count; pickup++) {
                if (route.pickup_segment(pickup) != segment) continue;
                ids[cursor] = run.ids[pickup];
                fractions[cursor++] = route.pickup_fraction(pickup);
            }
            boolean intakeOn = route.intake_active(segment);
            LegKind kind = segment == route.segment_count() - 1 ? LegKind.DEPOT
                    : count > 0 ? LegKind.PICKUP : intakeOn ? LegKind.TRANSIT
                    : collectionStarted ? LegKind.RETURN : LegKind.ALIGNMENT;
            legs.add(new Leg(route.waypoint(segment), route.waypoint(segment + 1), ids, fractions,
                    intakeOn, route.segment_time_s(segment), route.segment_start_speed_in_s(segment),
                    route.segment_end_speed_in_s(segment), kind));
            collectionStarted |= intakeOn;
        }
        return legs;
    }

    private static double validate_run(List<Pollen> available, Butine.Run run, List<Leg> legs,
                                        VisionPose start, VisionPose depot, ButineField field) {
        ButineIntake.Route route = run.route;
        HashSet<Integer> availableIds = new HashSet<>();
        for (Pollen pollen : available) availableIds.add(pollen.id);
        for (int i = 0; i < run.count; i++) {
            if (!availableIds.contains(run.ids[i]) || !(run.poses[i].x < RED_MAX_X_IN))
                throw new IllegalStateException("Plan selected an unavailable or non-RED pollen");
        }
        HashSet<Integer> collected = new HashSet<>();
        int pickupIndex = 0, depots = 0;
        double time = 0;
        VisionPose previous = start;
        boolean returning = false, collectionStarted = false;
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            if (!field.segment_clear(previous, leg.target)
                    || previous.distance_to(leg.from) > 1e-9)
                throw new IllegalStateException("Emitted route has an unsafe segment or a discontinuity");
            if (!Double.isFinite(leg.predictedS) || leg.predictedS < 0)
                throw new IllegalStateException("Emitted leg has an invalid predicted time");
            if (!leg.intakeOn && collectionStarted) returning = true;
            if (returning && leg.intakeOn)
                throw new IllegalStateException("Intake restarted during the return to depot");
            collectionStarted |= leg.intakeOn;
            HashSet<Integer> actualContacts = new HashSet<>();
            if (leg.intakeOn) {
                for (Pollen pollen : available) {
                    if (collected.contains(pollen.id)) continue;
                    double fraction = ButineIntake.contact_fraction(leg.from, leg.target, pollen.position,
                            route.collectionHeading, route.intakeWidthIn, route.intakeForwardOffsetIn,
                            route.pollenRadiusIn);
                    if (Double.isFinite(fraction)) actualContacts.add(pollen.id);
                }
            }
            if (actualContacts.size() != leg.pollenIds.length)
                throw new IllegalStateException("Route failed to account for every incidental intake contact");
            for (int j = 0; j < leg.pollenIds.length; j++) {
                int id = leg.pollenIds[j];
                if (!actualContacts.contains(id) || !collected.add(id)
                        || pickupIndex >= run.count || run.ids[pickupIndex] != id)
                    throw new IllegalStateException("Contact group differs from the selected production run");
                double fraction = ButineIntake.contact_fraction(leg.from, leg.target, run.poses[pickupIndex],
                        route.collectionHeading, route.intakeWidthIn, route.intakeForwardOffsetIn,
                        route.pollenRadiusIn);
                if (!Double.isFinite(fraction) || Math.abs(fraction - leg.contactFractions[j]) > 1e-7)
                    throw new IllegalStateException("Serialized intake contact fraction differs from production geometry");
                pickupIndex++;
            }
            if (collected.size() > CAPACITY)
                throw new IllegalStateException("Incidental intake contacts exceeded the four-pollen limit");
            if (leg.kind == LegKind.DEPOT) {
                depots++;
                if (i != legs.size() - 1 || leg.intakeOn || leg.target.distance_to(depot) > 1e-9
                        || Math.abs(VisionPose.wrap_pi(leg.target.heading - depot.heading)) > 1e-9)
                    throw new IllegalStateException("Depot arrival is not the final correctly oriented intake-off leg");
            }
            time += leg.predictedS;
            previous = leg.target;
        }
        if (pickupIndex != run.count || depots != 1 || Math.abs(time - run.timeS) > TIME_EPSILON_S)
            throw new IllegalStateException("Production planner and serialized sweep route disagree on pickups, depot or timing");
        Leg finalCollection = null;
        for (Leg leg : legs) if (leg.intakeOn) finalCollection = leg;
        if (finalCollection == null || finalCollection.contactFractions.length == 0)
            throw new IllegalStateException("Collection route has no final intake contact");
        double finalFraction = finalCollection.contactFractions[finalCollection.contactFractions.length - 1];
        double continuation = (1 - finalFraction) * finalCollection.from.distance_to(finalCollection.target);
        if (Math.abs(continuation - route.intakeOvershootIn) > 1e-6)
            throw new IllegalStateException("Collection does not continue the configured distance beyond its final contact");
        return time;
    }

    private static String output(Input input, Butine.Params defaults, ButineField field,
                                  List<ExecutedRun> runs, List<Pollen> remaining, int solveCount,
                                  int collected, double committedS, double remainingS,
                                  double totalSolveUs, String stopReason) {
        StringBuilder json = new StringBuilder(16384);
        json.append("{\"schemaVersion\":2,\"config\":{\"alliance\":\"RED\",\"eligibleHalf\":\"x < 72\",\"capacity\":")
                .append(CAPACITY).append(",\"productionDefaultCapacity\":").append(defaults.capacity)
                .append(",\"seed\":").append(input.seed);
        property(json, "remainingSeconds", input.remainingS);
        property(json, "scoreOverheadSeconds", input.scoreOverheadS);
        property(json, "fieldSizeIn", field.fieldSizeIn);
        property(json, "robotLengthIn", field.robotLengthIn);
        property(json, "robotWidthIn", field.robotWidthIn);
        property(json, "bodyClearanceIn", field.bodyClearanceIn);
        property(json, "fieldReserveIn", field.fieldReserveIn);
        property(json, "trackingReserveIn", field.trackingReserveIn);
        property(json, "radiusIn", field.radiusIn);
        property(json, "intakeWidthIn", input.intakeWidthIn);
        property(json, "intakeForwardOffsetIn", input.intakeForwardOffsetIn);
        property(json, "intakeOvershootIn", input.intakeOvershootIn);
        property(json, "pollenRadiusIn", defaults.pollenRadiusIn);
        json.append(",\"sweepIntake\":true,\"continuousCollection\":true");
        json.append(",\"kinematics\":{");
        Butine.Kinematics k = defaults.kinematics;
        json.append("\"cruiseSpeedInS\":").append(k.cruiseSpeedInS);
        property(json, "accelInS2", k.accelInS2);
        property(json, "decelInS2", k.decelInS2);
        property(json, "omegaMaxRadS", k.omegaMaxRadS);
        property(json, "turnSettleS", k.turnSettleS);
        property(json, "turnDeadbandRad", k.turnDeadbandRad);
        property(json, "turnCoupling", k.turnCoupling);
        property(json, "pickupDwellS", k.pickupDwellS);
        property(json, "junctionDeviationIn", k.junctionDeviationIn);
        json.append("}},\"start\":"); pose(json, input.start);
        json.append(",\"depot\":"); pose(json, input.depot);
        json.append(",\"obstacles\":[");
        for (int i = 0; i < field.box_count(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"index\":").append(i).append(",\"physical\":"); box(json, field.physical_box(i));
            json.append(",\"exclusion\":"); box(json, field.exclusion_box(i));
            json.append('}');
        }
        json.append("],\"eligiblePollen\":[");
        for (int i = 0; i < input.pollen.size(); i++) {
            if (i > 0) json.append(',');
            Pollen pollen = input.pollen.get(i);
            json.append("{\"id\":").append(pollen.id);
            property(json, "x", pollen.position.x);
            property(json, "y", pollen.position.y);
            property(json, "heading", pollen.position.heading);
            json.append('}');
        }
        json.append("],\"runs\":[");
        for (int i = 0; i < runs.size(); i++) {
            if (i > 0) json.append(',');
            ExecutedRun run = runs.get(i);
            json.append("{\"runId\":").append(run.id).append(",\"start\":"); pose(json, run.start);
            json.append(",\"depot\":"); pose(json, input.depot);
            json.append(",\"pickupIds\":[");
            for (int j = 0; j < run.run.ids.length; j++) {
                if (j > 0) json.append(',');
                json.append(run.run.ids[j]);
            }
            json.append("],\"pickupCount\":").append(run.run.count);
            property(json, "collectionHeading", run.run.route.collectionHeading);
            property(json, "intakeOvershootIn", run.run.route.intakeOvershootIn);
            property(json, "travelTimeS", run.run.timeS);
            property(json, "scoreOverheadS", input.scoreOverheadS);
            property(json, "committedTimeS", run.committedTimeS);
            property(json, "cumulativeTimeS", run.cumulativeTimeS);
            property(json, "remainingBeforeS", run.remainingBeforeS);
            property(json, "remainingAfterS", run.remainingAfterS);
            property(json, "solveUs", run.solveUs);
            json.append(",\"candidatesConsidered\":").append(run.candidatesConsidered).append(",\"legs\":[");
            for (int j = 0; j < run.legs.size(); j++) {
                if (j > 0) json.append(',');
                Leg leg = run.legs.get(j);
                json.append("{\"kind\":"); string(json, leg.kind.name());
                json.append(",\"pollenId\":").append(leg.pollenIds.length == 0 ? -1 : leg.pollenIds[0]);
                json.append(",\"pollenIds\":[");
                for (int contact = 0; contact < leg.pollenIds.length; contact++) {
                    if (contact > 0) json.append(',');
                    json.append(leg.pollenIds[contact]);
                }
                json.append("],\"contactFractions\":[");
                for (int contact = 0; contact < leg.contactFractions.length; contact++) {
                    if (contact > 0) json.append(',');
                    json.append(leg.contactFractions[contact]);
                }
                json.append("],\"intakeOn\":").append(leg.intakeOn).append(",\"from\":"); pose(json, leg.from);
                json.append(",\"target\":"); pose(json, leg.target);
                property(json, "predictedS", leg.predictedS);
                property(json, "swingDeg", leg.swingDeg);
                property(json, "startSpeedInS", leg.startSpeedInS);
                property(json, "endSpeedInS", leg.endSpeedInS);
                json.append('}');
            }
            json.append("]}");
        }
        json.append("],\"uncollectedIds\":[");
        for (int i = 0; i < remaining.size(); i++) {
            if (i > 0) json.append(',');
            json.append(remaining.get(i).id);
        }
        json.append("],\"diagnostics\":{\"solveCount\":").append(solveCount).append(",\"stopReason\":");
        string(json, stopReason);
        json.append(",\"totalCollected\":").append(collected).append(",\"eligibleCount\":").append(input.pollen.size());
        property(json, "totalCommittedTimeS", committedS);
        property(json, "remainingS", remainingS);
        property(json, "totalSolveUs", totalSolveUs);
        json.append(",\"allSegmentsClear\":true,\"legTimingMatches\":true,\"recedingHorizon\":true,\"solverWallTimeDeducted\":false}}");
        return json.toString();
    }

    private static String describe_field() {
        Butine.Params defaults = new Butine.Params();
        ButineField field = ButineField.biobuzz();
        StringBuilder json = new StringBuilder(2048);
        json.append("{\"config\":{\"productionDefaultCapacity\":").append(defaults.capacity)
                .append(",\"capacity\":").append(CAPACITY);
        property(json, "fieldSizeIn", field.fieldSizeIn);
        property(json, "robotLengthIn", field.robotLengthIn);
        property(json, "robotWidthIn", field.robotWidthIn);
        property(json, "bodyClearanceIn", field.bodyClearanceIn);
        property(json, "fieldReserveIn", field.fieldReserveIn);
        property(json, "trackingReserveIn", field.trackingReserveIn);
        property(json, "radiusIn", field.radiusIn);
        property(json, "intakeOvershootIn", defaults.intakeOvershootIn);
        json.append("},\"obstacles\":[");
        for (int i = 0; i < field.box_count(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"index\":").append(i).append(",\"physical\":"); box(json, field.physical_box(i));
            json.append(",\"exclusion\":"); box(json, field.exclusion_box(i));
            json.append('}');
        }
        return json.append("]}").toString();
    }

    private static double number(String value, String description) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException(description + " must be finite");
        return parsed;
    }

    private static void property(StringBuilder json, String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalStateException("Cannot serialize nonfinite " + name);
        json.append(','); string(json, name); json.append(':').append(value);
    }

    private static void pose(StringBuilder json, VisionPose pose) {
        json.append("{\"x\":").append(pose.x);
        property(json, "y", pose.y);
        property(json, "heading", pose.heading);
        json.append('}');
    }

    private static void box(StringBuilder json, ButineField.Box box) {
        json.append("{\"minX\":").append(box.minX);
        property(json, "maxX", box.maxX);
        property(json, "minY", box.minY);
        property(json, "maxY", box.maxY);
        json.append('}');
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        json.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) json.append('0');
                        json.append(hex);
                    } else json.append(c);
            }
        }
        json.append('"');
    }
}
